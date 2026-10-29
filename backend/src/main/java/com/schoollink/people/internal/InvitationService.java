package com.schoollink.people.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.InvitationIssued;
import com.schoollink.people.Invitations;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-A4 invitation codes (D7, D13). A code is single-use, expires after 30 days, and issuing a new
 * one revokes the guardian's previous code. Only its SHA-256 hash is stored: a fast hash is enough
 * because codes are random (about 40 bits), activation is rate-limited (NFR-7), and a code alone
 * never signs anyone in, an SMS one-time code to the guardian's phone is also required (UC-P1).
 * The code in clear is returned once, for the admin to print (UC-A4), and published in
 * {@link InvitationIssued}, to be sent by SMS (D13). It is never logged or audited.
 */
@Service
class InvitationService implements Invitations {

	static final Duration LIFETIME = Duration.ofDays(30);

	/** No 0/O, 1/I/L: the parent types the code from an SMS. 32 symbols, 8 of them: 40 bits. */
	private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

	private static final int LENGTH = 8;

	private final NamedParameterJdbcTemplate jdbc;

	private final SchoolClasses schoolClasses;

	private final AuditLog audit;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	InvitationService(NamedParameterJdbcTemplate jdbc, SchoolClasses schoolClasses, AuditLog audit,
			ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.schoolClasses = schoolClasses;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	/** Shown once: only the hash is stored. */
	record IssuedCode(UUID invitationId, UUID guardianId, String guardianName, String code, Instant expiresAt) {
	}

	record ClassIssuedView(List<IssuedCode> issued, int skippedWithAccount) {
	}

	/** D7: only a guardian without an app account gets a code. */
	@Transactional
	IssuedCode issueForGuardian(CurrentUser user, UUID guardianId) {
		UUID school = requireAdmin(user);
		Map<String, Object> guardian = jdbc.queryForList(
				"SELECT name, user_id IS NOT NULL AS has_account FROM guardian WHERE id = :id AND school_id = :school FOR UPDATE",
				new MapSqlParameterSource("id", guardianId).addValue("school", school))
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
		if ((Boolean) guardian.get("has_account")) {
			throw ApiException.conflict("invitation.guardian_has_account");
		}
		return issue(school, user.userId(), guardianId, (String) guardian.get("name"));
	}

	/** UC-A4 for a whole class: every guardian of its students who has no account yet. */
	@Transactional
	ClassIssuedView issueForClass(CurrentUser user, UUID classId) {
		UUID school = requireAdmin(user);
		if (!schoolClasses.classExists(school, classId)) {
			throw ApiException.notFound("not_found");
		}
		MapSqlParameterSource p = new MapSqlParameterSource("class", classId).addValue("school", school);
		// FOR UPDATE: an activation (UC-P1) cannot link an account while codes are issued
		List<Map<String, Object>> guardians = jdbc.queryForList("""
				SELECT g.id, g.name, g.user_id IS NOT NULL AS has_account FROM guardian g
				WHERE g.school_id = :school AND g.id IN (SELECT sg.guardian_id FROM student_guardian sg
				               JOIN student s ON s.id = sg.student_id AND s.school_id = sg.school_id
				               WHERE s.class_id = :class AND s.school_id = :school)
				ORDER BY g.name, g.id FOR UPDATE""", p);
		List<IssuedCode> issued = new ArrayList<>();
		int skipped = 0;
		for (Map<String, Object> g : guardians) {
			if ((Boolean) g.get("has_account")) {
				skipped++;
			}
			else {
				issued.add(issue(school, user.userId(), (UUID) g.get("id"), (String) g.get("name")));
			}
		}
		return new ClassIssuedView(issued, skipped);
	}

	private IssuedCode issue(UUID school, UUID actor, UUID guardianId, String guardianName) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(LIFETIME);
		MapSqlParameterSource p = new MapSqlParameterSource("guardian", guardianId).addValue("school", school)
			.addValue("now", Timestamp.from(now));
		int revoked = jdbc.update("""
				UPDATE invitation_code SET revoked_at = :now
				WHERE guardian_id = :guardian AND school_id = :school AND used_at IS NULL AND revoked_at IS NULL""", p);
		String code = generate();
		UUID id = jdbc.queryForObject("""
				INSERT INTO invitation_code (school_id, guardian_id, code_hash, created_at, expires_at)
				VALUES (:school, :guardian, :hash, :now, :expires) RETURNING id""",
				p.addValue("hash", hash(code)).addValue("expires", Timestamp.from(expiresAt)), UUID.class);
		events.publishEvent(new InvitationIssued(school, guardianId, code, expiresAt));
		// NFR-11, NFR-9: never the code itself
		audit.record(new AuditEntry(school, actor, AuditAction.CREATE, "invitation_code", id.toString(), null,
				Map.of("guardianId", guardianId, "expiresAt", expiresAt.toString(), "revokedPrevious", revoked)));
		return new IssuedCode(id, guardianId, guardianName, code, expiresAt);
	}

	@Override
	public Optional<PendingInvitation> find(String code) {
		return jdbc.query("""
				SELECT id, school_id, guardian_id FROM invitation_code
				WHERE code_hash = :hash AND used_at IS NULL AND revoked_at IS NULL AND expires_at > :now""",
				new MapSqlParameterSource("hash", hash(normalize(code))).addValue("now", Timestamp.from(clock.instant())),
				(rs, n) -> new PendingInvitation(rs.getObject("id", UUID.class), rs.getObject("school_id", UUID.class),
						rs.getObject("guardian_id", UUID.class)))
			.stream()
			.findFirst();
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<PendingInvitation> use(String code) {
		return jdbc.query("""
				UPDATE invitation_code SET used_at = :now
				WHERE code_hash = :hash AND used_at IS NULL AND revoked_at IS NULL AND expires_at > :now
				RETURNING id, school_id, guardian_id""",
				new MapSqlParameterSource("hash", hash(normalize(code))).addValue("now", Timestamp.from(clock.instant())),
				(rs, n) -> new PendingInvitation(rs.getObject("id", UUID.class), rs.getObject("school_id", UUID.class),
						rs.getObject("guardian_id", UUID.class)))
			.stream()
			.findFirst();
	}

	/** "K7QM-3XPA": two groups of four, easier to read out and type. */
	private String generate() {
		StringBuilder code = new StringBuilder(LENGTH + 1);
		for (int i = 0; i < LENGTH; i++) {
			if (i == LENGTH / 2) {
				code.append('-');
			}
			code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

	/** Upper case, without spaces or dashes. */
	static String normalize(String code) {
		return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
	}

	static String hash(String code) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalize(code).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static UUID requireAdmin(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.ADMIN)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

}

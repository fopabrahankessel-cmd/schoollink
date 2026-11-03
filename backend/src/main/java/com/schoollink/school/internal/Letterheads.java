package com.schoollink.school.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.school.Schools;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * UC-A7, D36: the letterhead of generated documents. The admin writes it; the admin and the DM read
 * it (the DM prints it on the full-day absence report, UC-D9). Every change is audited (NFR-11).
 */
@Service
class Letterheads {

	static final int MAX_LINES = 12;

	static final int MAX_TEXT = 100;

	static final int MAX_LOGO_BYTES = 1_048_576;

	/** A 30 mm logo needs a few hundred pixels; more only costs the DM's phone memory. */
	static final int MAX_LOGO_PIXELS = 2000;

	enum Layout {
		BILINGUAL, FRENCH, ENGLISH
	}

	enum Size {
		SMALL, NORMAL, LARGE
	}

	record Line(String text, boolean bold, Size size) {
	}

	/**
	 * {@code configured}: at least one line or a logo; otherwise the document header is the school
	 * name. {@code logoVersion} changes with each new logo (a cache key for the apps).
	 */
	record Letterhead(String schoolName, boolean configured, Layout layout, List<Line> french, List<Line> english,
			boolean hasLogo, String logoVersion) {
	}

	record Logo(byte[] bytes, String type) {
	}

	private static final TypeReference<List<Line>> LINES = new TypeReference<>() {
	};

	private final JdbcTemplate jdbc;

	private final Schools schools;

	private final AuditLog audit;

	private final JsonMapper json;

	private final Clock clock;

	Letterheads(JdbcTemplate jdbc, Schools schools, AuditLog audit, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.schools = schools;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	Letterhead view(CurrentUser user) {
		return view(reader(user));
	}

	@Transactional
	Letterhead save(CurrentUser user, Layout layout, List<Line> french, List<Line> english) {
		UUID school = admin(user);
		List<Line> fr = clean("french", french);
		List<Line> en = clean("english", english);
		jdbc.update("""
				INSERT INTO school_letterhead (school_id, layout, french, english, updated_at, updated_by)
				VALUES (?, ?, ?::jsonb, ?::jsonb, ?, ?)
				ON CONFLICT (school_id) DO UPDATE SET layout = EXCLUDED.layout, french = EXCLUDED.french,
				    english = EXCLUDED.english, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by""",
				school, layout.name(), json.writeValueAsString(fr), json.writeValueAsString(en), now(), user.userId());
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("layout", layout.name());
		after.put("french", fr);
		after.put("english", en);
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "school_letterhead", school.toString(),
				null, after));
		return view(school);
	}

	@Transactional(readOnly = true)
	Logo logo(CurrentUser user) {
		UUID school = reader(user);
		return jdbc.query("SELECT logo, logo_type FROM school_letterhead WHERE school_id = ? AND logo IS NOT NULL",
				(rs, n) -> new Logo(rs.getBytes("logo"), rs.getString("logo_type")), school)
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
	}

	/** PNG or JPEG by their first bytes, whatever the file name says; at most 1 MB. */
	@Transactional
	Letterhead setLogo(CurrentUser user, byte[] bytes) {
		UUID school = admin(user);
		String type = typeOf(bytes);
		if (type == null) {
			throw ApiException.invalid("letterhead.logo_type", "file");
		}
		if (bytes.length > MAX_LOGO_BYTES) {
			throw ApiException.invalid("letterhead.logo_too_large", "file");
		}
		checkDecodes(bytes);
		Timestamp now = now();
		jdbc.update("""
				INSERT INTO school_letterhead (school_id, layout, french, english, logo, logo_type, logo_at, updated_at, updated_by)
				VALUES (?, 'BILINGUAL', '[]', '[]', ?, ?, ?, ?, ?)
				ON CONFLICT (school_id) DO UPDATE SET logo = EXCLUDED.logo, logo_type = EXCLUDED.logo_type,
				    logo_at = EXCLUDED.logo_at, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by""",
				school, bytes, type, now, now, user.userId());
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "school_letterhead", school.toString(),
				null, Map.of("logo", Map.of("type", type, "bytes", bytes.length))));
		return view(school);
	}

	@Transactional
	Letterhead removeLogo(CurrentUser user) {
		UUID school = admin(user);
		int removed = jdbc.update("""
				UPDATE school_letterhead SET logo = NULL, logo_type = NULL, logo_at = NULL, updated_at = ?, updated_by = ?
				WHERE school_id = ? AND logo IS NOT NULL""", now(), user.userId(), school);
		if (removed > 0) {
			Map<String, Object> after = new LinkedHashMap<>();
			after.put("logo", null);
			audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "school_letterhead",
					school.toString(), null, after));
		}
		return view(school);
	}

	private Letterhead view(UUID school) {
		String name = schools.name(school);
		return jdbc.query("SELECT layout, french::text, english::text, logo_at FROM school_letterhead WHERE school_id = ?",
				(rs, n) -> {
					List<Line> fr = json.readValue(rs.getString("french"), LINES);
					List<Line> en = json.readValue(rs.getString("english"), LINES);
					Timestamp logoAt = rs.getTimestamp("logo_at");
					boolean hasLogo = logoAt != null;
					return new Letterhead(name, hasLogo || !fr.isEmpty() || !en.isEmpty(),
							Layout.valueOf(rs.getString("layout")), fr, en, hasLogo,
							hasLogo ? Long.toString(logoAt.getTime()) : null);
				}, school)
			.stream()
			.findFirst()
			.orElseGet(() -> new Letterhead(name, false, Layout.BILINGUAL, List.of(), List.of(), false, null));
	}

	/** At most 12 lines, each 1 to 100 characters once trimmed; a missing size is NORMAL. */
	private static List<Line> clean(String field, List<Line> lines) {
		if (lines == null) {
			return List.of();
		}
		if (lines.size() > MAX_LINES) {
			throw ApiException.invalid("letterhead.too_many_lines", field, MAX_LINES);
		}
		return lines.stream().map(line -> {
			String text = line == null || line.text() == null ? "" : line.text().strip();
			if (text.isEmpty() || text.length() > MAX_TEXT) {
				// same arguments as @Size: {1} max, {2} min
				throw ApiException.invalid("validation.size", field, field, MAX_TEXT, 1);
			}
			return new Line(text, line.bold(), line.size() == null ? Size.NORMAL : line.size());
		}).toList();
	}

	/**
	 * D36: every DM's phone draws the logo into the PDF, so a logo that does not decode, or decodes into a
	 * huge picture, would break the report for the whole school. The size is read from the header before
	 * anything is decoded.
	 */
	private static void checkDecodes(byte[] bytes) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw ApiException.invalid("letterhead.logo_type", "file");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				if (reader.getWidth(0) > MAX_LOGO_PIXELS || reader.getHeight(0) > MAX_LOGO_PIXELS) {
					throw ApiException.invalid("letterhead.logo_pixels", "file",
							String.valueOf(MAX_LOGO_PIXELS));
				}
				reader.read(0);
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException e) {
			if (e instanceof ApiException refusal) {
				throw refusal;
			}
			throw ApiException.invalid("letterhead.logo_type", "file");
		}
	}

	private static String typeOf(byte[] bytes) {
		if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
			return "image/png";
		}
		if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
			return "image/jpeg";
		}
		return null;
	}

	private static boolean startsWith(byte[] bytes, int... prefix) {
		if (bytes.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if ((bytes[i] & 0xFF) != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	private Timestamp now() {
		return Timestamp.from(Instant.now(clock));
	}

	private static UUID reader(CurrentUser user) {
		if (user.schoolId() == null || !user.hasAny(Role.ADMIN, Role.DM)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

	private static UUID admin(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.ADMIN)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

}

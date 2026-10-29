package com.schoollink.audit;

import com.schoollink.TestcontainersConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** NFR-11: append-only audit log, written in the same transaction as the change. Fake data only. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuditLogTests {

	@Autowired
	AuditLog auditLog;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate tx;

	@Test
	void recordsActionWithRedactedSnapshots() { // NFR-9, NFR-11
		UUID school = school();
		String guardianId = UUID.randomUUID().toString();
		Map<String, Object> before = Map.of("name", "Parent Test", "phone", "+237699000001");
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("name", "Parent Test");
		after.put("phone", "+237699000077");
		after.put("password_hash", "$2a$10$secret");
		after.put("codeHash", "abc");

		auditLog.record(new AuditEntry(school, null, AuditAction.UPDATE, "guardian", guardianId, before, after));

		var row = jdbc.queryForMap("""
				SELECT action, entity, before->>'phone' AS before_phone, after->>'phone' AS after_phone,
				       after->>'name' AS name, after->>'password_hash' AS hash, after->>'codeHash' AS code, at
				FROM audit_log WHERE entity_id = ?""", guardianId);
		assertThat(row).containsEntry("action", "UPDATE")
			.containsEntry("entity", "guardian")
			.containsEntry("before_phone", "+237*******01")
			.containsEntry("after_phone", "+237*******77")
			.containsEntry("name", "Parent Test")
			.containsEntry("hash", "[redacted]")
			.containsEntry("code", "[redacted]");
		assertThat(row.get("at")).isNotNull();
	}

	@Test
	void createHasNoBeforeSnapshot() {
		String id = UUID.randomUUID().toString();

		auditLog.record(new AuditEntry(school(), null, AuditAction.CREATE, "block", id, null, Map.of("name", "Bloc 1")));

		assertThat(jdbc.queryForObject("SELECT before IS NULL FROM audit_log WHERE entity_id = ?", Boolean.class, id))
			.isTrue();
	}

	@Test
	void updateIsRejectedByTheDatabase() {
		String id = recordOne();

		assertRejected(() -> jdbc.update("UPDATE audit_log SET entity = 'tampered' WHERE entity_id = ?", id));
	}

	@Test
	void deleteIsRejectedByTheDatabase() {
		String id = recordOne();

		assertRejected(() -> jdbc.update("DELETE FROM audit_log WHERE entity_id = ?", id));
	}

	@Test
	void truncateIsRejectedByTheDatabase() {
		recordOne();

		assertRejected(() -> jdbc.execute("TRUNCATE audit_log"));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void recordingOutsideATransactionFails() {
		assertThatThrownBy(() -> auditLog.record(
				new AuditEntry(null, null, AuditAction.EXPORT, "dataset", "attendance", null, null)))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void auditRecordRollsBackWithTheChange() {
		String id = UUID.randomUUID().toString();

		tx.executeWithoutResult(status -> {
			auditLog.record(new AuditEntry(school(), null, AuditAction.CREATE, "block", id, null, Map.of("name", "X")));
			status.setRollbackOnly();
		});

		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ?", Integer.class, id))
			.isZero();
	}

	private String recordOne() {
		String id = UUID.randomUUID().toString();
		auditLog.record(new AuditEntry(school(), null, AuditAction.CREATE, "block", id, null, Map.of("name", "Bloc")));
		return id;
	}

	private UUID school() {
		return jdbc.queryForObject("INSERT INTO school (name) VALUES ('Collège Test') RETURNING id", UUID.class);
	}

	/** The trigger error aborts the transaction, so the statement runs inside a savepoint. */
	private void assertRejected(ThrowingCallable statement) {
		jdbc.execute("SAVEPOINT expected_failure");
		assertThatThrownBy(statement).isInstanceOf(DataAccessException.class)
			.rootCause()
			.hasMessageContaining("audit_log is append-only");
		jdbc.execute("ROLLBACK TO SAVEPOINT expected_failure");
	}

}

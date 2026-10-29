package com.schoollink.audit.internal;

import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Writes audit rows with plain JDBC; the table itself rejects updates and deletes (V4). */
@Service
class JdbcAuditLog implements AuditLog {

	private final JdbcTemplate jdbc;

	private final JsonMapper json;

	JdbcAuditLog(JdbcTemplate jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY) // NFR-11: same transaction as the change
	public void record(AuditEntry entry) {
		jdbc.update("""
				INSERT INTO audit_log (school_id, actor_id, action, entity, entity_id, before, after)
				VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
				""", entry.schoolId(), entry.actorId(), entry.action().name(), entry.entity(), entry.entityId(),
				snapshot(entry.before()), snapshot(entry.after()));
	}

	private String snapshot(Object value) {
		return value == null ? null : json.writeValueAsString(AuditRedactor.redact(json.valueToTree(value)));
	}

}

package com.schoollink.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * One audit record (NFR-11). {@code before} and {@code after} are snapshots of the entity (any
 * object Jackson can serialize, or {@code null}); secrets are redacted and phone numbers masked
 * before storage (NFR-9).
 *
 * @param schoolId the school the entity belongs to; {@code null} only for cross-school researcher actions
 * @param actorId the user who acted; {@code null} for system jobs
 */
public record AuditEntry(UUID schoolId, UUID actorId, AuditAction action, String entity, String entityId,
		Object before, Object after) {

	public AuditEntry {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(entity, "entity");
		Objects.requireNonNull(entityId, "entityId");
	}

}

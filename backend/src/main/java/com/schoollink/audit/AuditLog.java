package com.schoollink.audit;

/**
 * Append-only audit log (NFR-11), the audit module's public API.
 *
 * <p>Must be called inside the transaction that makes the change, so that the change and its
 * audit record commit or roll back together; calling it outside a transaction fails.
 */
public interface AuditLog {

	void record(AuditEntry entry);

}

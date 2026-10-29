package com.schoollink.audit;

/** What happened to the audited entity (NFR-11). Stored as text; keep in sync with V4's CHECK. */
public enum AuditAction {

	CREATE, UPDATE, DELETE, CANCEL, JUSTIFY, DECIDE, IMPORT, EXPORT

}

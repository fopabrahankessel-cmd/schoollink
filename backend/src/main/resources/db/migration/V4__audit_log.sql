-- Append-only audit log (SPEC §7, NFR-11): every create, edit, cancel, justification, decision
-- and export is recorded with actor and time. Rows are written by the audit module only, in the
-- same transaction as the change they describe.

CREATE TABLE audit_log (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- NULL only for actions outside a school (the researcher's cross-school exports).
    school_id  uuid        REFERENCES school (id),
    -- NULL for system actions (nightly rules, digest job).
    actor_id   uuid        REFERENCES app_user (id),
    action     varchar(10) NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'CANCEL', 'JUSTIFY', 'DECIDE', 'IMPORT', 'EXPORT')),
    entity     text        NOT NULL,
    entity_id  text        NOT NULL,
    -- Snapshots with secrets redacted and phone numbers masked by the audit module (NFR-9).
    before     jsonb,
    after      jsonb,
    at         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX audit_log_entity_idx ON audit_log (entity, entity_id);
CREATE INDEX audit_log_school_at_idx ON audit_log (school_id, at);

-- Append-only, enforced by the database: any UPDATE, DELETE or TRUNCATE fails.
CREATE FUNCTION audit_log_reject_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_change();

CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_reject_change();

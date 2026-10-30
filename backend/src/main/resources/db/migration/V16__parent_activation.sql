-- UC-P1 parent activation (task 2.9, NFR-7). Step 1 checks the invitation code and the phone and
-- sends a one-time code by SMS; step 2 checks it, uses the invitation and creates the account.
-- Only the one-time code's BCrypt hash is stored. It expires after 10 minutes and allows 5 wrong
-- tries; a new request for the same invitation replaces the previous one.

CREATE TABLE parent_activation (
    id             uuid        PRIMARY KEY,
    school_id      uuid        NOT NULL REFERENCES school (id),
    invitation_id  uuid        NOT NULL REFERENCES invitation_code (id),
    guardian_id    uuid        NOT NULL,
    otp_hash       text        NOT NULL,
    created_at     timestamptz NOT NULL,
    expires_at     timestamptz NOT NULL,
    failed_attempts smallint   NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    verified_at    timestamptz,
    CHECK (expires_at > created_at),
    FOREIGN KEY (school_id, guardian_id) REFERENCES guardian (school_id, id)
);

CREATE INDEX parent_activation_invitation_idx ON parent_activation (invitation_id);

-- UC-P1 follow-up (D24): a parent whose account exists signs in again on a phone with an SMS
-- one-time code, since parents have no password. Same rules as the activation (V16, V17): only the
-- code's BCrypt hash is stored, it lasts 10 minutes and allows 5 wrong tries, requests are kept so
-- the limits survive a restart (3 codes per account per hour), and only the newest request of an
-- account can be verified.

CREATE TABLE parent_sign_in (
    id              uuid        PRIMARY KEY,
    school_id       uuid        NOT NULL REFERENCES school (id),
    user_id         uuid        NOT NULL REFERENCES app_user (id),
    otp_hash        text        NOT NULL,
    created_at      timestamptz NOT NULL,
    expires_at      timestamptz NOT NULL,
    failed_attempts smallint    NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    verified_at     timestamptz,
    seq             bigint      GENERATED ALWAYS AS IDENTITY,
    CHECK (expires_at > created_at)
);

CREATE INDEX parent_sign_in_user_idx ON parent_sign_in (user_id, created_at);

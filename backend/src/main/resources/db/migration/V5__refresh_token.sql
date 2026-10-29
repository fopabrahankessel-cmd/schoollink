-- Refresh tokens (NFR-7): 30-day lifetime, rotated on every use. Only a SHA-256 hash of the
-- opaque token is stored. All tokens issued from one login share a family_id; presenting a token
-- that was already used revokes the whole family (likely theft).
CREATE TABLE refresh_token (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES app_user (id),
    family_id   uuid        NOT NULL,
    token_hash  text        NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    revoked_at  timestamptz
);

CREATE INDEX refresh_token_family_idx ON refresh_token (family_id);
CREATE INDEX refresh_token_user_idx ON refresh_token (user_id);

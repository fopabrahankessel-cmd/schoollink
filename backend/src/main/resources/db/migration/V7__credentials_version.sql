-- D18, NFR-7: bumped on every password change, password reset and disabling. Access tokens carry
-- the version they were issued with ("cv" claim); a token with an older version is rejected, so a
-- stolen access token stops working at once instead of when it expires.
ALTER TABLE app_user ADD COLUMN credentials_version integer NOT NULL DEFAULT 0;

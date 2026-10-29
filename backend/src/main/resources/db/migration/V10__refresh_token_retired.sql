-- NFR-7: a token replaced moments ago may be presented once more when its replacement never
-- reached the client (a page reloaded during a refresh). That lost replacement is then retired:
-- marked used and retired_at set. A retired token never gets that grace; presenting it always
-- revokes its family, so a stolen replacement cannot ride the grace window.
ALTER TABLE refresh_token ADD COLUMN retired_at timestamptz;

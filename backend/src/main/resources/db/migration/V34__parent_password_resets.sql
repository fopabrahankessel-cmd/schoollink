-- D46: a parent resets a forgotten password with an SMS code at most 3 times per school year;
-- after that the school administration lets them do it again.

-- a sign-in code that was used to replace a password the account already had
ALTER TABLE parent_sign_in ADD COLUMN password_reset boolean NOT NULL DEFAULT false;

-- the admin allowed resets again: only those completed after the latest allowance count
CREATE TABLE password_reset_allowance (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id  uuid        NOT NULL REFERENCES school (id),
    user_id    uuid        NOT NULL REFERENCES app_user (id),
    allowed_by uuid        NOT NULL REFERENCES app_user (id),
    created_at timestamptz NOT NULL
);

CREATE INDEX password_reset_allowance_user_idx ON password_reset_allowance (user_id, created_at);

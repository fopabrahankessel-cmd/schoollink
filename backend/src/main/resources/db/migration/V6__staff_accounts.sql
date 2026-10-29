-- Staff accounts (UC-A2; decision D18 in docs/PLAN.md).
-- D18: the admin never chooses a staff password. The server generates a temporary one, shown once
-- to the admin; until the user replaces it, only GET /me and POST /me/password are allowed.
ALTER TABLE app_user ADD COLUMN password_change_required boolean NOT NULL DEFAULT false;

-- Corrections to V2 (decisions D7, D15 in docs/PLAN.md).

-- D15: a phone number or an email identifies one user within one school only; the same parent can
-- have an account in each pilot school. The researcher (school_id NULL) is unique among researchers.
DROP INDEX app_user_phone_key;
DROP INDEX app_user_email_key;
CREATE UNIQUE INDEX app_user_school_phone_key ON app_user (school_id, phone) NULLS NOT DISTINCT
    WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX app_user_school_email_key ON app_user (school_id, email) NULLS NOT DISTINCT
    WHERE email IS NOT NULL;

-- D7: every guardian may receive an invitation code by SMS. A guardian who activates it has an app
-- account (user_id set) and gets push notifications; a guardian without an account gets SMS. The
-- channel follows from user_id, so the sms_only flag of the spec is not stored.
ALTER TABLE guardian DROP COLUMN sms_only;

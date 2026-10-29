-- People (SPEC §7; UC-A2, UC-A3, UC-A4; decisions D5, D7 in docs/PLAN.md).
-- Same tenant-safety pattern as V1: UNIQUE (school_id, id) on parents, composite FKs on children (NFR-8).
-- Privacy (NFR-9, NFR-10): no photos, birth year only; phone numbers stored in E.164 (+237 + 9 digits).

-- Staff and parents with an app account. school_id is NULL only for the researcher, who works
-- across the pilot schools on anonymised data (SPEC §3).
CREATE TABLE app_user (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id      uuid        REFERENCES school (id),
    first_name     text        NOT NULL,
    last_name      text        NOT NULL,
    phone          varchar(13) CHECK (phone ~ '^\+237[0-9]{9}$'),
    email          text        CHECK (email = lower(email) AND email LIKE '%_@_%'),
    -- BCrypt (NFR-7). NULL for parents, who sign in with an invitation code + SMS one-time code (UC-P1).
    password_hash  text,
    language       varchar(2)  NOT NULL DEFAULT 'FR' CHECK (language IN ('FR', 'EN')),
    status         varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED')),
    created_at     timestamptz NOT NULL DEFAULT now(),
    CHECK (phone IS NOT NULL OR email IS NOT NULL),
    UNIQUE (school_id, id)
);

-- POST /auth/login takes a phone or an email without a school, so both identify one user globally.
CREATE UNIQUE INDEX app_user_phone_key ON app_user (phone) WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX app_user_email_key ON app_user (email) WHERE email IS NOT NULL;

-- Role and scope (SPEC §3). Scope comes from assignments, not from the role alone:
--   DM, ASSISTANT_DM: one block each row (D5); TEACHER: classes via teaching_assignment;
--   ADMIN, COUNSELLOR: the whole school (the counsellor then sees referred students only);
--   PARENT: their children via guardian; RESEARCHER: no school (anonymised, cross-school).
CREATE TABLE role_assignment (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid,
    user_id     uuid        NOT NULL,
    role        varchar(12) NOT NULL CHECK (role IN ('ADMIN', 'TEACHER', 'ASSISTANT_DM', 'DM', 'COUNSELLOR', 'PARENT', 'RESEARCHER')),
    block_id    uuid,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK ((role = 'RESEARCHER') = (school_id IS NULL)),
    CHECK ((role IN ('DM', 'ASSISTANT_DM')) = (block_id IS NOT NULL)),
    UNIQUE NULLS NOT DISTINCT (user_id, role, block_id),
    FOREIGN KEY (user_id) REFERENCES app_user (id),
    FOREIGN KEY (school_id, user_id) REFERENCES app_user (school_id, id),
    FOREIGN KEY (school_id, block_id) REFERENCES block (school_id, id)
);

-- D5: one DM per block, so "the DM in charge" of a student is unique (BR-11, BR-18).
CREATE UNIQUE INDEX role_assignment_one_dm_per_block ON role_assignment (block_id) WHERE role = 'DM';
CREATE INDEX role_assignment_user_idx ON role_assignment (user_id);

-- Which teacher teaches which subject in which class (BR-1: only an assigned teacher opens a session).
CREATE TABLE teaching_assignment (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL,
    teacher_id  uuid        NOT NULL,
    class_id    uuid        NOT NULL,
    subject     text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, teacher_id, class_id, subject),
    FOREIGN KEY (school_id, teacher_id) REFERENCES app_user (school_id, id),
    FOREIGN KEY (school_id, class_id) REFERENCES school_class (school_id, id)
);

CREATE INDEX teaching_assignment_class_idx ON teaching_assignment (class_id);

-- D1 + BR-1: a timetable slot can only name a teacher who is assigned to that class and subject.
ALTER TABLE timetable_slot
    ADD FOREIGN KEY (school_id, teacher_id, class_id, subject)
        REFERENCES teaching_assignment (school_id, teacher_id, class_id, subject);

-- Current class enrolment. NFR-10: birth year only, no photo.
CREATE TABLE student (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL,
    class_id    uuid        NOT NULL,
    matricule   text        NOT NULL,
    first_name  text        NOT NULL,
    last_name   text        NOT NULL,
    sex         char(1)     NOT NULL CHECK (sex IN ('M', 'F')),
    birth_year  smallint    NOT NULL CHECK (birth_year BETWEEN 1980 AND 2100),
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, id),
    UNIQUE (school_id, matricule),
    FOREIGN KEY (school_id, class_id) REFERENCES school_class (school_id, id)
);

CREATE INDEX student_class_idx ON student (class_id);

-- A guardian may have no app account (user_id NULL). D7: sms_only guardians never get an account
-- or an invitation code; they receive notifications by SMS only.
CREATE TABLE guardian (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL REFERENCES school (id),
    user_id     uuid,
    name        text        NOT NULL,
    phone       varchar(13) NOT NULL CHECK (phone ~ '^\+237[0-9]{9}$'),
    language    varchar(2)  NOT NULL DEFAULT 'FR' CHECK (language IN ('FR', 'EN')),
    sms_only    boolean     NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (NOT (sms_only AND user_id IS NOT NULL)),
    UNIQUE (school_id, id),
    UNIQUE (school_id, phone),
    FOREIGN KEY (school_id, user_id) REFERENCES app_user (school_id, id)
);

CREATE UNIQUE INDEX guardian_user_key ON guardian (user_id) WHERE user_id IS NOT NULL;

-- Many-to-many: a student has several guardians, a guardian several children (SPEC §2).
CREATE TABLE student_guardian (
    school_id    uuid        NOT NULL,
    student_id   uuid        NOT NULL,
    guardian_id  uuid        NOT NULL,
    relation     varchar(8)  NOT NULL CHECK (relation IN ('MOTHER', 'FATHER', 'GUARDIAN', 'OTHER')),
    is_primary   boolean     NOT NULL DEFAULT false,
    created_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, guardian_id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, guardian_id) REFERENCES guardian (school_id, id)
);

CREATE UNIQUE INDEX student_guardian_one_primary ON student_guardian (student_id) WHERE is_primary;
CREATE INDEX student_guardian_guardian_idx ON student_guardian (guardian_id);

-- UC-A4: single-use code, 30-day expiry, only its hash is stored. At most one live code per
-- guardian: issuing a new one revokes the previous one.
CREATE TABLE invitation_code (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id    uuid        NOT NULL,
    guardian_id  uuid        NOT NULL,
    code_hash    text        NOT NULL UNIQUE,
    created_at   timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz NOT NULL,
    used_at      timestamptz,
    revoked_at   timestamptz,
    CHECK (expires_at > created_at),
    CHECK (used_at IS NULL OR revoked_at IS NULL),
    FOREIGN KEY (school_id, guardian_id) REFERENCES guardian (school_id, id)
);

CREATE UNIQUE INDEX invitation_code_one_live_per_guardian ON invitation_code (guardian_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

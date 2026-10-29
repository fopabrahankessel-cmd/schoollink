-- School structure (SPEC §7; UC-A1; decisions D1, D3, D5, D10 in docs/PLAN.md).
--
-- Tenant safety (NFR-8): every school-owned row carries school_id, and every parent table has
-- UNIQUE (school_id, id) so children reference it with a composite foreign key
-- (school_id, parent_id). The database then rejects a row that points at another school's data,
-- in addition to the server-side filtering.

-- Non-overlapping term dates per school need an exclusion constraint on (school_id, daterange).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE school (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        text        NOT NULL,
    language    varchar(2)  NOT NULL DEFAULT 'FR' CHECK (language IN ('FR', 'EN')),
    -- BR-7: lateness counts from the school start time (default 07:30).
    start_time  time        NOT NULL DEFAULT '07:30',
    timezone    text        NOT NULL DEFAULT 'Africa/Douala',
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE academic_year (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL REFERENCES school (id),
    label       text        NOT NULL,
    start_date  date        NOT NULL,
    end_date    date        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (end_date > start_date),
    UNIQUE (school_id, id),
    UNIQUE (school_id, label)
);

-- BR-16: the "unjustified absences" rule counts over the current term, so terms of one school
-- must not overlap.
CREATE TABLE term (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         uuid        NOT NULL,
    academic_year_id  uuid        NOT NULL,
    label             text        NOT NULL,
    start_date        date        NOT NULL,
    end_date          date        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CHECK (end_date > start_date),
    UNIQUE (school_id, id),
    UNIQUE (academic_year_id, label),
    FOREIGN KEY (school_id, academic_year_id) REFERENCES academic_year (school_id, id),
    EXCLUDE USING gist (school_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
);

-- D10: holidays are configured per term by the admin; D3: school days are Mon–Fri minus holidays.
CREATE TABLE holiday (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL,
    term_id     uuid        NOT NULL,
    label       text        NOT NULL,
    start_date  date        NOT NULL,
    end_date    date        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date),
    FOREIGN KEY (school_id, term_id) REFERENCES term (school_id, id)
);

CREATE INDEX holiday_term_idx ON holiday (term_id);

-- Timetable slots P1..Pn of the school day.
CREATE TABLE period (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL REFERENCES school (id),
    number      smallint    NOT NULL CHECK (number BETWEEN 1 AND 12),
    start_time  time        NOT NULL,
    end_time    time        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (end_time > start_time),
    UNIQUE (school_id, id),
    UNIQUE (school_id, number)
);

-- D5: a block is a named group of classes; one DM (and its assistant DMs) per block.
CREATE TABLE block (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL REFERENCES school (id),
    name        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, id),
    UNIQUE (school_id, name)
);

-- Every class belongs to a block, so every student has a DM in charge (BR-11, BR-18).
CREATE TABLE school_class (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         uuid        NOT NULL,
    academic_year_id  uuid        NOT NULL,
    block_id          uuid        NOT NULL,
    name              text        NOT NULL,
    level             text        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, id),
    UNIQUE (academic_year_id, name),
    FOREIGN KEY (school_id, academic_year_id) REFERENCES academic_year (school_id, id),
    FOREIGN KEY (school_id, block_id) REFERENCES block (school_id, id)
);

CREATE INDEX school_class_block_idx ON school_class (block_id);

-- D1: which teacher teaches which class in which period on which weekday. Drives
-- GET /me/sessions and UC-T3. weekday is ISO (1 = Monday); school days are Mon–Fri (D3).
-- teacher_id gets its foreign key to app_user in V2, when the people tables exist.
CREATE TABLE timetable_slot (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL,
    class_id    uuid        NOT NULL,
    weekday     smallint    NOT NULL CHECK (weekday BETWEEN 1 AND 5),
    period_id   uuid        NOT NULL,
    teacher_id  uuid        NOT NULL,
    subject     text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (class_id, weekday, period_id),
    FOREIGN KEY (school_id, class_id) REFERENCES school_class (school_id, id),
    FOREIGN KEY (school_id, period_id) REFERENCES period (school_id, id)
);

CREATE INDEX timetable_slot_teacher_idx ON timetable_slot (teacher_id, weekday);

-- Attendance, lateness and justification (SPEC §7; BR-1 to BR-15; decisions D1, D3 in docs/PLAN.md).
-- Same tenant-safety pattern as V1: UNIQUE (school_id, id) on parents, composite FKs on children (NFR-8).
--
-- BR-8, BR-9: attendance and lateness are separate tables with no link between them; neither ever
-- changes the other.
-- BR-4: every stored session is confirmed and locked. The database rejects any change to a session,
-- and to a record only the justification and a one-time DM cancellation may change (BR-13). Nothing
-- is ever deleted: cancelled rows stay and are excluded from counts (BR-15).
-- Rules that need other tables or the school's settings are checked by the API: BR-1 (the teacher is
-- assigned to the class), BR-7 (arrival after the school start time), holidays (D3, D10).

-- BR-11, BR-12: written by the DM in charge; one justification may cover several absences and
-- lateness records (e.g. a three-day illness). NFR-10: the reason says "illness", no health detail;
-- the note is the DM's free text.
CREATE TABLE justification (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id    uuid        NOT NULL REFERENCES school (id),
    reason       varchar(15) NOT NULL CHECK (reason IN ('ILLNESS', 'FAMILY', 'SCHOOL_ACTIVITY', 'OTHER')),
    note         text,
    recorded_by  uuid        NOT NULL,
    recorded_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, recorded_by) REFERENCES app_user (school_id, id)
);

-- BR-1: one class, one date, one period, one teacher. BR-6: the id is the UUID the device generated,
-- so a replayed upload is recognised (PUT /sessions/{uuid}), and confirmed_at is the device's
-- confirmation time, which is the lock time; synced_at is when the server received it.
CREATE TABLE attendance_session (
    id            uuid        PRIMARY KEY,
    school_id     uuid        NOT NULL,
    class_id      uuid        NOT NULL,
    date          date        NOT NULL,
    period_id     uuid        NOT NULL,
    teacher_id    uuid        NOT NULL,
    confirmed_at  timestamptz NOT NULL,
    synced_at     timestamptz NOT NULL DEFAULT now(),
    -- D3: school days are Monday to Friday.
    CHECK (extract(isodow FROM date) BETWEEN 1 AND 5),
    UNIQUE (school_id, id),
    UNIQUE (class_id, date, period_id),
    FOREIGN KEY (school_id, class_id) REFERENCES school_class (school_id, id),
    FOREIGN KEY (school_id, period_id) REFERENCES period (school_id, id),
    FOREIGN KEY (school_id, teacher_id) REFERENCES app_user (school_id, id)
);

CREATE INDEX attendance_session_teacher_idx ON attendance_session (teacher_id, date);
CREATE INDEX attendance_session_school_date_idx ON attendance_session (school_id, date);

-- One row per student of the session, present or absent (BR-2, BR-5), for clean statistics.
-- BR-10: an absence starts unjustified (justification_id NULL). BR-13: only an absence is cancelled,
-- always with a reason, by whom and when.
CREATE TABLE attendance_record (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         uuid        NOT NULL,
    session_id        uuid        NOT NULL,
    student_id        uuid        NOT NULL,
    status            varchar(7)  NOT NULL CHECK (status IN ('PRESENT', 'ABSENT')),
    justification_id  uuid,
    cancelled_at      timestamptz,
    cancelled_by      uuid,
    cancel_reason     text,
    CHECK (justification_id IS NULL OR status = 'ABSENT'),
    CHECK (cancelled_at IS NULL OR status = 'ABSENT'),
    CHECK ((cancelled_at IS NULL) = (cancelled_by IS NULL) AND (cancelled_at IS NULL) = (cancel_reason IS NULL)),
    CHECK (cancel_reason IS NULL OR btrim(cancel_reason) <> ''),
    UNIQUE (school_id, id),
    UNIQUE (session_id, student_id),
    FOREIGN KEY (school_id, session_id) REFERENCES attendance_session (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, justification_id) REFERENCES justification (school_id, id),
    FOREIGN KEY (school_id, cancelled_by) REFERENCES app_user (school_id, id)
);

CREATE INDEX attendance_record_student_idx ON attendance_record (student_id);
CREATE INDEX attendance_record_justification_idx ON attendance_record (justification_id)
    WHERE justification_id IS NOT NULL;

-- BR-7: recorded at the gate by the assistant DM. The id comes from the device (PUT /lateness/{uuid});
-- recorded_at is the device time. One live record per student per day: a cancelled one no longer
-- counts (BR-15), so the student can be recorded again that day.
CREATE TABLE lateness_record (
    id                uuid        PRIMARY KEY,
    school_id         uuid        NOT NULL,
    student_id        uuid        NOT NULL,
    date              date        NOT NULL,
    arrival_time      time        NOT NULL,
    recorded_by       uuid        NOT NULL,
    recorded_at       timestamptz NOT NULL,
    synced_at         timestamptz NOT NULL DEFAULT now(),
    justification_id  uuid,
    cancelled_at      timestamptz,
    cancelled_by      uuid,
    cancel_reason     text,
    CHECK (extract(isodow FROM date) BETWEEN 1 AND 5),
    CHECK ((cancelled_at IS NULL) = (cancelled_by IS NULL) AND (cancelled_at IS NULL) = (cancel_reason IS NULL)),
    CHECK (cancel_reason IS NULL OR btrim(cancel_reason) <> ''),
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, recorded_by) REFERENCES app_user (school_id, id),
    FOREIGN KEY (school_id, justification_id) REFERENCES justification (school_id, id),
    FOREIGN KEY (school_id, cancelled_by) REFERENCES app_user (school_id, id)
);

CREATE UNIQUE INDEX lateness_record_one_per_student_day ON lateness_record (student_id, date)
    WHERE cancelled_at IS NULL;
CREATE INDEX lateness_record_school_date_idx ON lateness_record (school_id, date);
CREATE INDEX lateness_record_justification_idx ON lateness_record (justification_id)
    WHERE justification_id IS NOT NULL;

-- BR-4, BR-15: enforced by the database. The errors use SQLSTATE 23000 (integrity constraint
-- violation), like the constraints above.
CREATE FUNCTION attendance_reject_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '%: % is not allowed (BR-4, BR-15)', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

-- A locked session never changes and is never deleted.
CREATE TRIGGER attendance_session_locked
    BEFORE UPDATE OR DELETE ON attendance_session
    FOR EACH ROW EXECUTE FUNCTION attendance_reject_change();

CREATE TRIGGER attendance_session_no_truncate
    BEFORE TRUNCATE ON attendance_session
    FOR EACH STATEMENT EXECUTE FUNCTION attendance_reject_change();

-- Records and lateness: what was recorded never changes. Only the justification (BR-11) may be set
-- or changed, and the cancellation set once (BR-13); a cancellation is never undone.
CREATE FUNCTION attendance_record_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '%: DELETE is not allowed (BR-15)', TG_TABLE_NAME
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF (to_jsonb(NEW) - 'justification_id' - 'cancelled_at' - 'cancelled_by' - 'cancel_reason')
           IS DISTINCT FROM (to_jsonb(OLD) - 'justification_id' - 'cancelled_at' - 'cancelled_by' - 'cancel_reason') THEN
        RAISE EXCEPTION '%: a recorded value cannot change (BR-4)', TG_TABLE_NAME
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF OLD.cancelled_at IS NOT NULL
           AND (NEW.cancelled_at, NEW.cancelled_by, NEW.cancel_reason)
               IS DISTINCT FROM (OLD.cancelled_at, OLD.cancelled_by, OLD.cancel_reason) THEN
        RAISE EXCEPTION '%: a cancellation cannot change (BR-13)', TG_TABLE_NAME
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER attendance_record_guard
    BEFORE UPDATE OR DELETE ON attendance_record
    FOR EACH ROW EXECUTE FUNCTION attendance_record_guard();

CREATE TRIGGER attendance_record_no_truncate
    BEFORE TRUNCATE ON attendance_record
    FOR EACH STATEMENT EXECUTE FUNCTION attendance_reject_change();

CREATE TRIGGER lateness_record_guard
    BEFORE UPDATE OR DELETE ON lateness_record
    FOR EACH ROW EXECUTE FUNCTION attendance_record_guard();

CREATE TRIGGER lateness_record_no_truncate
    BEFORE TRUNCATE ON lateness_record
    FOR EACH STATEMENT EXECUTE FUNCTION attendance_reject_change();

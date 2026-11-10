-- Disciplinary records outside alerts (UC-D5, D37 in docs/PLAN.md; design 2026-11-09 §4.1).

-- What happened, when, and what the DM decided. Rows from alert decisions (D4) become OTHER / NONE,
-- dated on the school day they were recorded.
ALTER TABLE disciplinary_record ADD COLUMN category varchar(11) NOT NULL DEFAULT 'OTHER'
    CHECK (category IN ('BEHAVIOUR', 'INSOLENCE', 'FIGHT', 'ABSENTEEISM', 'CHEATING', 'DRESS', 'PHONE', 'DAMAGE', 'OTHER'));
ALTER TABLE disciplinary_record ALTER COLUMN category DROP DEFAULT;
ALTER TABLE disciplinary_record ADD COLUMN measure varchar(10) NOT NULL DEFAULT 'NONE'
    CHECK (measure IN ('NONE', 'WARNING', 'DETENTION', 'SUSPENSION', 'OTHER'));
ALTER TABLE disciplinary_record ADD COLUMN occurred_on date;
UPDATE disciplinary_record d SET occurred_on = (d.recorded_at AT TIME ZONE s.timezone)::date
FROM school s WHERE s.id = d.school_id;
ALTER TABLE disciplinary_record ALTER COLUMN occurred_on SET NOT NULL;

-- Closing, once, with an optional note.
ALTER TABLE disciplinary_record ADD COLUMN close_note text CHECK (char_length(close_note) <= 500);
ALTER TABLE disciplinary_record ADD COLUMN closed_by  uuid;
ALTER TABLE disciplinary_record ADD COLUMN closed_at  timestamptz;
ALTER TABLE disciplinary_record ADD FOREIGN KEY (school_id, closed_by) REFERENCES app_user (school_id, id);
ALTER TABLE disciplinary_record ADD CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL AND closed_by IS NOT NULL));

-- A record made by mistake is cancelled with a reason, never deleted, and hidden from lists (the
-- spirit of BR-13 to BR-15).
ALTER TABLE disciplinary_record ADD COLUMN cancelled_by  uuid;
ALTER TABLE disciplinary_record ADD COLUMN cancelled_at  timestamptz;
ALTER TABLE disciplinary_record ADD COLUMN cancel_reason text CHECK (char_length(cancel_reason) BETWEEN 1 AND 500);
ALTER TABLE disciplinary_record ADD FOREIGN KEY (school_id, cancelled_by) REFERENCES app_user (school_id, id);
ALTER TABLE disciplinary_record ADD CHECK ((cancelled_at IS NULL) = (cancelled_by IS NULL)
    AND (cancelled_at IS NULL) = (cancel_reason IS NULL));

CREATE INDEX disciplinary_record_list_idx ON disciplinary_record (school_id, student_id, occurred_on DESC)
    WHERE cancelled_at IS NULL;

-- D37: no edit after saving, no delete. A record is closed once and cancelled once; a cancelled
-- record never changes again (NFR-11: the audit trail stays simple).
CREATE FUNCTION disciplinary_record_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'disciplinary_record % cannot be deleted', OLD.id;
    END IF;
    IF OLD.cancelled_at IS NOT NULL THEN
        RAISE EXCEPTION 'disciplinary_record % is cancelled and cannot change', OLD.id;
    END IF;
    IF (NEW.id, NEW.school_id, NEW.student_id, NEW.alert_id, NEW.category, NEW.measure, NEW.occurred_on,
        NEW.description, NEW.recorded_by, NEW.recorded_at) IS DISTINCT FROM
       (OLD.id, OLD.school_id, OLD.student_id, OLD.alert_id, OLD.category, OLD.measure, OLD.occurred_on,
        OLD.description, OLD.recorded_by, OLD.recorded_at) THEN
        RAISE EXCEPTION 'what disciplinary_record % says cannot change', OLD.id;
    END IF;
    IF OLD.status = 'CLOSED' AND (NEW.status, NEW.close_note, NEW.closed_by, NEW.closed_at) IS DISTINCT FROM
       (OLD.status, OLD.close_note, OLD.closed_by, OLD.closed_at) THEN
        RAISE EXCEPTION 'disciplinary_record % is closed and cannot change', OLD.id;
    END IF;
    IF OLD.status = 'OPEN' AND NEW.status = 'OPEN' AND (NEW.close_note, NEW.closed_by, NEW.closed_at) IS DISTINCT FROM
       (OLD.close_note, OLD.closed_by, OLD.closed_at) THEN
        RAISE EXCEPTION 'an open disciplinary_record % only changes by closing or cancelling', OLD.id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER disciplinary_record_guard BEFORE UPDATE OR DELETE ON disciplinary_record
    FOR EACH ROW EXECUTE FUNCTION disciplinary_record_guard();

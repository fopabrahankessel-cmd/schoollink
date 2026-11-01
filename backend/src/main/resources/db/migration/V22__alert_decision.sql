-- DM decisions on risk alerts (task 3.5; BR-18, BR-19, UC-D2, D4, D17, D30 in docs/PLAN.md).

-- BR-18: one decision per alert, with who, when and why. Detection never sets it (it only inserts
-- OPEN alerts); the DM closes the alert by deciding.
ALTER TABLE risk_alert ADD COLUMN decision      varchar(12) CHECK (decision IN ('DISMISS', 'SUMMON', 'DISCIPLINARY', 'REFER'));
ALTER TABLE risk_alert ADD COLUMN decision_note text        CHECK (char_length(decision_note) <= 500);
-- What the decision led to: the summons, disciplinary record or counselling case (SPEC §7).
ALTER TABLE risk_alert ADD COLUMN outcome_id    uuid;
ALTER TABLE risk_alert ADD CHECK ((status = 'CLOSED') = (decision IS NOT NULL AND closed_by IS NOT NULL));
ALTER TABLE risk_alert ADD CHECK (decision <> 'DISMISS' OR decision_note IS NOT NULL);

-- An alert is raised OPEN and closed once by a decision; after that it never changes, and it is
-- never deleted (like the records, D20).
CREATE FUNCTION risk_alert_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'risk_alert % cannot be deleted', OLD.id;
    END IF;
    IF OLD.status = 'CLOSED' THEN
        RAISE EXCEPTION 'risk_alert % is closed and cannot change', OLD.id;
    END IF;
    IF NEW.status = 'OPEN' AND ROW(NEW.*) IS DISTINCT FROM ROW(OLD.*) THEN
        RAISE EXCEPTION 'an open risk_alert % only changes by a decision', OLD.id;
    END IF;
    IF (NEW.id, NEW.school_id, NEW.student_id, NEW.class_id, NEW.rule_type, NEW.threshold, NEW.as_of, NEW.evidence,
        NEW.raised_at) IS DISTINCT FROM
       (OLD.id, OLD.school_id, OLD.student_id, OLD.class_id, OLD.rule_type, OLD.threshold, OLD.as_of, OLD.evidence,
        OLD.raised_at) THEN
        RAISE EXCEPTION 'what risk_alert % found cannot change', OLD.id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER risk_alert_guard BEFORE UPDATE OR DELETE ON risk_alert
    FOR EACH ROW EXECUTE FUNCTION risk_alert_guard();

-- D4: the disciplinary issue recorded from an alert, minimal until its screens (UC-D5, wave 2).
CREATE TABLE disciplinary_record (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id    uuid        NOT NULL REFERENCES school (id),
    student_id   uuid        NOT NULL,
    alert_id     uuid,
    description  text        NOT NULL CHECK (char_length(description) BETWEEN 1 AND 500),
    status       varchar(6)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    recorded_by  uuid        NOT NULL,
    recorded_at  timestamptz NOT NULL,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, alert_id) REFERENCES risk_alert (school_id, id),
    FOREIGN KEY (school_id, recorded_by) REFERENCES app_user (school_id, id)
);

CREATE INDEX disciplinary_record_student_idx ON disciplinary_record (school_id, student_id);

-- D4: the referral to the counsellor, a case OPEN until a counsellor takes it (UC-C1, wave 2). The
-- counsellor sees a student only once referred (SPEC §2).
CREATE TABLE counselling_case (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id      uuid        NOT NULL REFERENCES school (id),
    student_id     uuid        NOT NULL,
    referred_by    uuid        NOT NULL,
    -- The alert that led to the referral; further referrals while the case is open add nothing (D30).
    alert_id       uuid,
    referral_note  text        CHECK (char_length(referral_note) <= 500),
    counsellor_id  uuid,
    status         varchar(9)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'FOLLOW_UP', 'CLOSED')),
    opened_at      timestamptz NOT NULL,
    closed_at      timestamptz,
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, referred_by) REFERENCES app_user (school_id, id),
    FOREIGN KEY (school_id, alert_id) REFERENCES risk_alert (school_id, id),
    FOREIGN KEY (school_id, counsellor_id) REFERENCES app_user (school_id, id)
);

-- D30: one case in progress per student.
CREATE UNIQUE INDEX counselling_case_one_active ON counselling_case (student_id) WHERE status <> 'CLOSED';

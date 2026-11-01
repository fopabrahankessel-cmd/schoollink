-- Risk alerts (task 3.4; BR-16 to BR-19, D2, D3, D9, D29 in docs/PLAN.md).
--
-- Detection only ever inserts an OPEN alert: no decision, no summons, nothing else (BR-16 to
-- BR-19). The DM closes it with a decision (task 3.5); justifying the absences later does not
-- close it (BR-19).

CREATE TABLE risk_alert (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL REFERENCES school (id),
    student_id  uuid        NOT NULL,
    -- The student's class when the alert was raised: BR-18, it goes to the DM in charge of it.
    class_id    uuid        NOT NULL,
    rule_type   varchar(22) NOT NULL CHECK (rule_type IN ('FULL_DAY_ABSENCE', 'CONSECUTIVE_LATENESS', 'UNJUSTIFIED_ABSENCES')),
    -- The rule's count when it fired: the DM may change it later (UC-D7).
    threshold   smallint    NOT NULL CHECK (threshold > 0),
    -- The school day the facts end on. D29: after this alert, the rule only counts later days.
    as_of       date        NOT NULL,
    -- What the rule saw: the dates of the streak, or the number of unjustified periods and since when.
    evidence    jsonb       NOT NULL,
    status      varchar(6)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    raised_at   timestamptz NOT NULL DEFAULT now(),
    closed_at   timestamptz,
    closed_by   uuid,
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, class_id) REFERENCES school_class (school_id, id),
    FOREIGN KEY (school_id, closed_by) REFERENCES app_user (school_id, id)
);

-- BR-17: at most one open alert per student per rule, whatever runs at the same time.
CREATE UNIQUE INDEX risk_alert_one_open ON risk_alert (student_id, rule_type) WHERE status = 'OPEN';

-- D29: the previous alert of a student and rule, and the DM's list of open alerts (task 3.5).
CREATE INDEX risk_alert_student_rule_idx ON risk_alert (school_id, student_id, rule_type, as_of DESC);
CREATE INDEX risk_alert_open_idx ON risk_alert (school_id, class_id) WHERE status = 'OPEN';

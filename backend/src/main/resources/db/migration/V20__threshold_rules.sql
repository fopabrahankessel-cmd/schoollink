-- Threshold rules and the DM's settings (task 3.3; BR-16, BR-22, UC-D7, D9, D28 in docs/PLAN.md).

-- BR-22: the SMS digest goes at a time set per school, by default 13:00 and again at 17:00 for
-- what is new since the first run (D6). The second run can be turned off.
ALTER TABLE school ADD COLUMN digest_first  time NOT NULL DEFAULT '13:00';
ALTER TABLE school ADD COLUMN digest_second time          DEFAULT '17:00';
ALTER TABLE school ADD CHECK (digest_second IS NULL OR digest_second > digest_first);

-- BR-16: one rule of each type per school. Unit and window are stored with the rule (SPEC §2, Q1);
-- in the pilot each type has one unit and one window (D28), the DM sets the count and turns the
-- rule on or off.
CREATE TABLE threshold_rule (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL REFERENCES school (id),
    type        varchar(22) NOT NULL CHECK (type IN ('FULL_DAY_ABSENCE', 'CONSECUTIVE_LATENESS', 'UNJUSTIFIED_ABSENCES')),
    count       smallint    NOT NULL CHECK (count > 0),
    unit        varchar(20) NOT NULL CHECK (unit IN ('FULL_DAYS', 'SCHOOL_DAYS', 'PERIODS')),
    "window"    varchar(14) NOT NULL CHECK ("window" IN ('CONSECUTIVE', 'CURRENT_TERM')),
    active      boolean     NOT NULL DEFAULT true,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    UNIQUE (school_id, id),
    UNIQUE (school_id, type),
    FOREIGN KEY (school_id, updated_by) REFERENCES app_user (school_id, id)
);

-- D9: the pilot's starting values, for every school however it is created.
CREATE FUNCTION threshold_rule_defaults(school uuid) RETURNS void LANGUAGE sql AS $$
    INSERT INTO threshold_rule (school_id, type, count, unit, "window") VALUES
        (school, 'FULL_DAY_ABSENCE', 1, 'FULL_DAYS', 'CONSECUTIVE'),
        (school, 'CONSECUTIVE_LATENESS', 5, 'SCHOOL_DAYS', 'CONSECUTIVE'),
        (school, 'UNJUSTIFIED_ABSENCES', 15, 'PERIODS', 'CURRENT_TERM')
    ON CONFLICT (school_id, type) DO NOTHING;
$$;

CREATE FUNCTION threshold_rule_seed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM threshold_rule_defaults(NEW.id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER school_threshold_rules AFTER INSERT ON school
    FOR EACH ROW EXECUTE FUNCTION threshold_rule_seed();

SELECT threshold_rule_defaults(id) FROM school;

-- Summons of a parent (task 3.6; BR-23, UC-D3, D12, D31 in docs/PLAN.md). Sent at once by push
-- to app users and by SMS to the other guardians, through the outbox, never in the digest.

CREATE TABLE summons (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     uuid        NOT NULL REFERENCES school (id),
    student_id    uuid        NOT NULL,
    -- The risk alert it answers (decision SUMMON, BR-18); NULL for a summons on its own.
    alert_id      uuid,
    -- For the DM's records only: the parents' message gives the meeting, not the reason (D31).
    reason        text        CHECK (char_length(reason) <= 300),
    meeting_date  date        NOT NULL,
    meeting_time  time        NOT NULL,
    sent_by       uuid        NOT NULL,
    sent_at       timestamptz NOT NULL,
    CHECK (alert_id IS NOT NULL OR reason IS NOT NULL),
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, alert_id) REFERENCES risk_alert (school_id, id),
    FOREIGN KEY (school_id, sent_by) REFERENCES app_user (school_id, id)
);

CREATE INDEX summons_student_idx ON summons (school_id, student_id, sent_at DESC);

-- A summons is a record of what was sent: it never changes and is never deleted.
CREATE FUNCTION summons_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'summons % cannot be changed or deleted', OLD.id;
END;
$$;

CREATE TRIGGER summons_guard BEFORE UPDATE OR DELETE ON summons
    FOR EACH ROW EXECUTE FUNCTION summons_guard();

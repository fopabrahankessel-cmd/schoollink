-- SMS digest and monthly cap (task 3.8; BR-22, BR-25, D6, D12, D33 in docs/PLAN.md).
--
-- Every guardian gets an absence's or a lateness's SMS HELD for the digest, app users too: BR-22
-- sends it to those who have no app or did not open the push. At each digest run, a HELD row whose
-- push was opened is SKIPPED; the others of one guardian, student and day are folded into one
-- DIGEST row, sent by the dispatcher, and become DIGESTED with a link to it.

ALTER TABLE notification_outbox DROP CONSTRAINT notification_outbox_event_type_check;
ALTER TABLE notification_outbox ADD CONSTRAINT notification_outbox_event_type_check
    CHECK (event_type IN ('ABSENCE', 'LATENESS', 'CORRECTION', 'SUMMONS', 'DM_MESSAGE', 'ANNOUNCEMENT', 'INVITATION', 'DIGEST'));

ALTER TABLE notification_outbox DROP CONSTRAINT notification_outbox_status_check;
ALTER TABLE notification_outbox ADD CONSTRAINT notification_outbox_status_check
    CHECK (status IN ('HELD', 'QUEUED', 'SENDING', 'SENT', 'DELIVERED', 'OPENED', 'FAILED', 'WITHDRAWN', 'DIGESTED', 'SKIPPED'));

-- The digest SMS that carried this row (DIGESTED rows only).
ALTER TABLE notification_outbox ADD COLUMN digest_id uuid REFERENCES notification_outbox (id);
ALTER TABLE notification_outbox ADD CHECK ((status = 'DIGESTED') = (digest_id IS NOT NULL));
ALTER TABLE notification_outbox ADD CHECK (status NOT IN ('DIGESTED', 'SKIPPED') OR channel = 'SMS');
ALTER TABLE notification_outbox ADD CHECK (event_type <> 'DIGEST' OR channel = 'SMS');

-- BR-25: the SMS sent by a school this month (the counter), and what a digest carried.
CREATE INDEX notification_outbox_sms_sent_idx ON notification_outbox (school_id, sent_at)
    WHERE channel = 'SMS' AND sent_at IS NOT NULL;
CREATE INDEX notification_outbox_digest_idx ON notification_outbox (digest_id) WHERE digest_id IS NOT NULL;

-- BR-25: the most SMS a school sends in a calendar month (in its time zone); at the cap only
-- summons go by SMS (D12). Set by the operator for the pilot.
ALTER TABLE school ADD COLUMN sms_monthly_cap integer NOT NULL DEFAULT 2000 CHECK (sms_monthly_cap >= 0);

-- One run per school, day and digest time, even after a restart or on two instances.
CREATE TABLE sms_digest_run (
    school_id  uuid        NOT NULL REFERENCES school (id),
    run_date   date        NOT NULL,
    slot       time        NOT NULL,
    ran_at     timestamptz NOT NULL,
    digests    integer     NOT NULL DEFAULT 0,
    PRIMARY KEY (school_id, run_date, slot)
);

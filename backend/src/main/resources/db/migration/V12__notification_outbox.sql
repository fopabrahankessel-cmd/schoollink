-- Notification outbox (SPEC §7, §6.1; BR-20 to BR-24, NFR-5, NFR-6; decisions D2, D6, D7, D12, D13).
-- Rows are written by the notification module in the same transaction as the event that causes them
-- (an absence, a lateness, an invitation, ...), and sent later by the dispatcher (task 2.6), so no
-- notification is lost after a crash. One row per guardian per channel per event (BR-20).
--
-- Channel (D7): a guardian with an app account gets PUSH, sent at once (BR-21); a guardian without
-- one gets SMS. Absence and lateness SMS rows are HELD for the daily digest (BR-22, D6), which sends
-- one SMS per student; summons and invitations go out at once (BR-23, D12, D13).
-- Privacy (NFR-9): no phone number here; the dispatcher reads it from the guardian when sending.

CREATE TABLE notification_outbox (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     uuid        NOT NULL,
    guardian_id   uuid        NOT NULL,
    -- The child concerned; NULL for a notification about the guardian (an invitation).
    student_id    uuid,
    event_type    varchar(12) NOT NULL CHECK (event_type IN ('ABSENCE', 'LATENESS', 'CORRECTION', 'SUMMONS', 'DM_MESSAGE', 'ANNOUNCEMENT', 'INVITATION')),
    -- The record behind the event: an attendance_record for ABSENCE, an invitation_code for INVITATION...
    event_id      uuid        NOT NULL,
    channel       varchar(4)  NOT NULL CHECK (channel IN ('PUSH', 'SMS')),
    -- The guardian's language when the event happened (BR-20); templates are rendered in it (NFR-12).
    language      varchar(2)  NOT NULL CHECK (language IN ('FR', 'EN')),
    -- What the templates need, captured when the event happened (task 2.5).
    payload       jsonb       NOT NULL,
    -- BR-24, plus HELD: waiting for the SMS digest, never sent on its own.
    status        varchar(9)  NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('HELD', 'QUEUED', 'SENT', 'DELIVERED', 'OPENED', 'FAILED')),
    attempts      smallint    NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at    timestamptz NOT NULL DEFAULT now(),
    sent_at       timestamptz,
    delivered_at  timestamptz,
    opened_at     timestamptz,
    CHECK (status <> 'HELD' OR channel = 'SMS'),
    UNIQUE (event_type, event_id, guardian_id, channel),
    FOREIGN KEY (school_id, guardian_id) REFERENCES guardian (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id)
);

-- The dispatcher's queue (task 2.6) and the digest's (task 3.8).
CREATE INDEX notification_outbox_queued_idx ON notification_outbox (created_at) WHERE status = 'QUEUED';
CREATE INDEX notification_outbox_held_idx ON notification_outbox (school_id, created_at) WHERE status = 'HELD';
-- A parent's inbox (UC-P2) and a student's notifications (corrections, research metrics).
CREATE INDEX notification_outbox_guardian_idx ON notification_outbox (guardian_id, created_at);
CREATE INDEX notification_outbox_student_idx ON notification_outbox (student_id, created_at) WHERE student_id IS NOT NULL;

-- Outbox dispatch (task 2.6; BR-21, BR-24, NFR-5, NFR-6). The dispatcher claims due rows with
-- FOR UPDATE SKIP LOCKED and marks them SENDING with the claim time, sends outside the transaction,
-- then records the outcome. A row still SENDING after the lease (the server stopped mid-send) is
-- claimed again, so nothing is lost; a send may then happen twice, never zero times.

ALTER TABLE notification_outbox DROP CONSTRAINT notification_outbox_status_check;
ALTER TABLE notification_outbox ADD CONSTRAINT notification_outbox_status_check
    CHECK (status IN ('HELD', 'QUEUED', 'SENDING', 'SENT', 'DELIVERED', 'OPENED', 'FAILED'));

-- When a QUEUED row may be sent: at once, or after a backoff once an attempt failed.
ALTER TABLE notification_outbox ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE notification_outbox ADD COLUMN claimed_at timestamptz;
-- The provider's reason for the last failure. NFR-9: never a phone number or a device token.
ALTER TABLE notification_outbox ADD COLUMN last_error text;
ALTER TABLE notification_outbox ADD CHECK ((status = 'SENDING') = (claimed_at IS NOT NULL));

DROP INDEX notification_outbox_queued_idx;
CREATE INDEX notification_outbox_due_idx ON notification_outbox (next_attempt_at) WHERE status = 'QUEUED';
CREATE INDEX notification_outbox_sending_idx ON notification_outbox (claimed_at) WHERE status = 'SENDING';

-- NFR-6, BR-24: when a send failed for good, so the school dashboard can count the failures of the
-- last hour. Rows that failed before this column existed get their last known time.
ALTER TABLE notification_outbox ADD COLUMN failed_at timestamptz;

UPDATE notification_outbox SET failed_at = COALESCE(sent_at, next_attempt_at, created_at) WHERE status = 'FAILED';

CREATE INDEX notification_outbox_failed_idx ON notification_outbox (school_id, failed_at) WHERE status = 'FAILED';

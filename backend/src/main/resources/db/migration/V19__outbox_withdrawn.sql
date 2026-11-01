-- Cancellation with correction (task 3.2; BR-13 to BR-15, D11). When the DM cancels an absence or
-- a lateness, its notifications not sent yet (a push still QUEUED, an SMS HELD for the digest) are
-- WITHDRAWN: the parent never hears of the error, so no correction follows. Those already on their
-- way or delivered get a correction. Withdrawn rows stay, so the research metrics still see them.

ALTER TABLE notification_outbox DROP CONSTRAINT notification_outbox_status_check;
ALTER TABLE notification_outbox ADD CONSTRAINT notification_outbox_status_check
    CHECK (status IN ('HELD', 'QUEUED', 'SENDING', 'SENT', 'DELIVERED', 'OPENED', 'FAILED', 'WITHDRAWN'));

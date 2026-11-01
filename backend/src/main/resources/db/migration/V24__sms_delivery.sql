-- SMS gateway and delivery reports (task 3.7; BR-24, D32 in docs/PLAN.md).
--
-- The gateway's id for each SMS it accepted (a Twilio message SID), so that its signed delivery
-- report (POST /api/v1/webhooks/sms/delivery) finds the row and marks it DELIVERED or FAILED.
ALTER TABLE notification_outbox ADD COLUMN provider_ref text;

CREATE UNIQUE INDEX notification_outbox_provider_ref ON notification_outbox (channel, provider_ref)
    WHERE provider_ref IS NOT NULL;

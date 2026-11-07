-- UC-P1, D24: a parent signs in with their school, phone and one SMS code, the first time too: the
-- invitation code, which went to the same phone as the one-time code, proved nothing more. A first
-- sign-in has no account yet, so its request belongs to the guardian, and the account is made when
-- the code is right. Exactly one of user_id and guardian_id is set.

ALTER TABLE parent_sign_in ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE parent_sign_in ADD COLUMN guardian_id uuid;
ALTER TABLE parent_sign_in ADD FOREIGN KEY (school_id, guardian_id) REFERENCES guardian (school_id, id);
ALTER TABLE parent_sign_in ADD CHECK ((user_id IS NULL) <> (guardian_id IS NULL));

CREATE INDEX parent_sign_in_guardian_idx ON parent_sign_in (guardian_id, created_at) WHERE guardian_id IS NOT NULL;

DROP TABLE parent_activation;
DROP TABLE invitation_code;

DELETE FROM notification_outbox WHERE event_type = 'INVITATION';
ALTER TABLE notification_outbox DROP CONSTRAINT notification_outbox_event_type_check;
ALTER TABLE notification_outbox ADD CONSTRAINT notification_outbox_event_type_check
    CHECK (event_type IN ('ABSENCE', 'LATENESS', 'CORRECTION', 'SUMMONS', 'DM_MESSAGE', 'ANNOUNCEMENT', 'DIGEST'));

-- UC-D4, UC-A5, D38: messages to parents. A DM writes to the guardians of one student or one class
-- of their blocks; an administrator to the whole school or one class. Sending writes this row, one
-- notification_outbox row per guardian and channel (event_type DM_MESSAGE or ANNOUNCEMENT, event_id
-- = this id) and an audit row, in one transaction (BR-20, NFR-6, NFR-11). Messages are never edited
-- or deleted. The id comes from the author's device, so sending twice sends once.

CREATE TABLE parent_message (
    id          uuid         PRIMARY KEY,
    school_id   uuid         NOT NULL REFERENCES school (id),
    author_id   uuid         NOT NULL,
    kind        varchar(12)  NOT NULL CHECK (kind IN ('DM_MESSAGE', 'ANNOUNCEMENT')),
    audience    varchar(7)   NOT NULL CHECK (audience IN ('STUDENT', 'CLASS', 'SCHOOL')),
    student_id  uuid,
    class_id    uuid,
    body        varchar(480) NOT NULL CHECK (length(btrim(body)) > 0),
    -- also by SMS for the guardians without the app (D38); plain: accents removed in the SMS only
    sms         boolean      NOT NULL,
    plain_sms   boolean      NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CHECK ((audience = 'STUDENT') = (student_id IS NOT NULL)),
    CHECK ((audience = 'CLASS') = (class_id IS NOT NULL)),
    CHECK (kind <> 'DM_MESSAGE' OR audience <> 'SCHOOL'),
    CHECK (kind <> 'ANNOUNCEMENT' OR audience <> 'STUDENT'),
    FOREIGN KEY (school_id, author_id) REFERENCES app_user (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, class_id) REFERENCES school_class (school_id, id)
);

-- The sent lists: a DM's own messages, the school's announcements; newest first.
CREATE INDEX parent_message_list_idx ON parent_message (school_id, kind, created_at DESC);

CREATE FUNCTION parent_message_guard() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'parent_message % cannot change', OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER parent_message_guard BEFORE UPDATE OR DELETE ON parent_message
    FOR EACH ROW EXECUTE FUNCTION parent_message_guard();

-- D38: the price of one SMS, for the cost shown before sending (FCFA).
ALTER TABLE school ADD COLUMN sms_unit_price integer NOT NULL DEFAULT 181 CHECK (sms_unit_price >= 0);

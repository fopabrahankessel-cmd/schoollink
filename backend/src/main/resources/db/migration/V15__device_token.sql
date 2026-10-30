-- FCM registration tokens (task 2.7; BR-21, SPEC §8 DeviceToken). One row per installed app: a
-- token belongs to one user at a time, and when someone else signs in on the same phone the token
-- moves to them. A token FCM reports as unregistered is deleted by the dispatcher.
-- NFR-9: a token lets anyone push to that phone; it never appears in logs or in the audit log.

CREATE TABLE device_token (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL,
    user_id     uuid        NOT NULL,
    token       text        NOT NULL UNIQUE CHECK (length(token) BETWEEN 1 AND 4096),
    platform    varchar(7)  NOT NULL CHECK (platform IN ('ANDROID')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    -- The last time the app registered it (each sign-in and app start).
    last_seen   timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (school_id, user_id) REFERENCES app_user (school_id, id)
);

CREATE INDEX device_token_user_idx ON device_token (user_id);

-- BR-6, NFR-3: a roll call or a lateness from a phone whose clock is wrong is kept, not refused.
-- The server then stores its own time in confirmed_at / recorded_at / arrival_time, and what the
-- phone sent goes here, for information and so that a retry of the same upload is recognised.
-- NULL when the phone's time was plausible and was stored as it came.
ALTER TABLE attendance_session ADD COLUMN device_confirmed_at timestamptz;

ALTER TABLE lateness_record ADD COLUMN device_recorded_at timestamptz;
ALTER TABLE lateness_record ADD COLUMN device_arrival_time time;

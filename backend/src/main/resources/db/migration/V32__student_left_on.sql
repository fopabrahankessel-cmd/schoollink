-- D43: the day a student left the school; NULL while enrolled. From that day they are in no roll
-- call and on no gate list, and nothing new is recorded or sent about them. What was recorded
-- before stays (BR-15, NFR-11). Cleared when the student comes back.
ALTER TABLE student ADD COLUMN left_on date;

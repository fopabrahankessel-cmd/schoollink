-- UC-P1, NFR-7 (follow-up to V16): one-time code requests are kept instead of replaced, so the
-- limits survive a restart: 3 codes per guardian per hour, and 15 wrong tries in all per
-- invitation (then the school issues a new code). Only the newest request of an invitation can
-- be verified; seq orders requests even when the clock gives two of them the same time.

ALTER TABLE parent_activation ADD COLUMN seq bigint GENERATED ALWAYS AS IDENTITY;

CREATE INDEX parent_activation_guardian_idx ON parent_activation (guardian_id, created_at);

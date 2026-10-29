-- NFR-7: the order in which a family's refresh tokens were created, independent of the clock. The
-- reload grace (V10) must know which tokens came after a replaced one; comparing timestamps fails
-- when two of them share a value (a coarse system clock, or two requests in the same microsecond).
ALTER TABLE refresh_token ADD COLUMN seq bigint GENERATED ALWAYS AS IDENTITY;

-- UC-A7, D36: the school letterhead printed on generated documents (the full-day absence report,
-- UC-D9). Lines are JSON arrays of {text, bold, size}; the logo is the school's emblem (PNG or JPEG,
-- at most 1 MB, checked by the API), never a person (NFR-10). One row per school; no row: the
-- document header is the school name.
CREATE TABLE school_letterhead (
    school_id   uuid        PRIMARY KEY REFERENCES school (id),
    layout      varchar(9)  NOT NULL CHECK (layout IN ('BILINGUAL', 'FRENCH', 'ENGLISH')),
    french      jsonb       NOT NULL CHECK (jsonb_typeof(french) = 'array'),
    english     jsonb       NOT NULL CHECK (jsonb_typeof(english) = 'array'),
    logo        bytea,
    logo_type   varchar(10) CHECK (logo_type IN ('image/png', 'image/jpeg')),
    logo_at     timestamptz,
    updated_at  timestamptz NOT NULL,
    updated_by  uuid        NOT NULL,
    CHECK ((logo IS NULL) = (logo_type IS NULL)),
    CHECK ((logo IS NULL) = (logo_at IS NULL)),
    FOREIGN KEY (school_id, updated_by) REFERENCES app_user (school_id, id)
);

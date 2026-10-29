-- UC-A3 student and guardian import, in two steps (task 1.11 preview, 1.12 commit).
-- The preview parses and checks the file and stores every row here; nothing reaches student or
-- guardian until the commit. The uploaded file itself is never stored.

CREATE TABLE student_import (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         uuid        NOT NULL REFERENCES school (id),
    academic_year_id  uuid        NOT NULL,
    file_name         text        NOT NULL,
    status            varchar(10) NOT NULL DEFAULT 'PREVIEW' CHECK (status IN ('PREVIEW', 'COMMITTED')),
    row_count         integer     NOT NULL,
    error_count       integer     NOT NULL,
    created_by        uuid        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    committed_at      timestamptz,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, academic_year_id) REFERENCES academic_year (school_id, id),
    FOREIGN KEY (school_id, created_by) REFERENCES app_user (school_id, id)
);

-- One row per data row of the file. data: the normalized student and guardians, NULL when the row
-- has errors; errors: [{"column": ..., "code": ..., "args": [...]}], empty when valid.
CREATE TABLE student_import_row (
    school_id   uuid     NOT NULL,
    import_id   uuid     NOT NULL,
    row_number  integer  NOT NULL,
    data        jsonb,
    errors      jsonb    NOT NULL DEFAULT '[]',
    PRIMARY KEY (import_id, row_number),
    FOREIGN KEY (school_id, import_id) REFERENCES student_import (school_id, id),
    CHECK ((data IS NULL) = (jsonb_array_length(errors) > 0))
);

-- D44: a term is made of two sequences, each with its own dates. They lie within the term and
-- sequence 1 ends before sequence 2 starts; the service checks both and names the error.
CREATE TABLE term_sequence (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid        NOT NULL,
    term_id     uuid        NOT NULL,
    number      smallint    NOT NULL CHECK (number IN (1, 2)),
    label       text        NOT NULL,
    start_date  date        NOT NULL,
    end_date    date        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (end_date > start_date),
    UNIQUE (term_id, number),
    FOREIGN KEY (school_id, term_id) REFERENCES term (school_id, id),
    EXCLUDE USING gist (term_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
);

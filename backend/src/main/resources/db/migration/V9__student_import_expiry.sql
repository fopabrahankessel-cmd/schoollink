-- UC-A3, NFR-9 (decision D19): a preview that is never committed expires after 7 days and its rows
-- (students' personal data) are deleted; a committed import keeps only its header and counts.
ALTER TABLE student_import DROP CONSTRAINT student_import_status_check;
ALTER TABLE student_import ADD CONSTRAINT student_import_status_check
    CHECK (status IN ('PREVIEW', 'COMMITTED', 'EXPIRED'));

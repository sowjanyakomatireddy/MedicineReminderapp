-- Photos live in the database, so hosts without a lasting disk (e.g. free plans) keep them.
-- Keys are random ("<uuid>.<ext>") and referenced from medicine.photo_key.
CREATE TABLE photo (
    photo_key    VARCHAR(64) PRIMARY KEY,
    content_type VARCHAR(32) NOT NULL,
    data         BYTEA       NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE mod
(
    id                TEXT PRIMARY KEY,
    title             TEXT,
    active            BOOLEAN NOT NULL DEFAULT FALSE,
    published_file_id TEXT
);

CREATE TABLE property
(
    key           TEXT PRIMARY KEY,
    value         TEXT,
    default_value TEXT,
    description   TEXT,
    is_required   Boolean,
    created_at    TIMESTAMP, -- store Instant as ISO-8601 string
    updated_at    TIMESTAMP  -- same
);

CREATE TABLE mod
(
    id                TEXT PRIMARY KEY,
    title             TEXT,
    active            BOOLEAN NOT NULL DEFAULT FALSE,
    steam_mod_id      TEXT
);
CREATE INDEX idx_mod_steam__id ON mod(steam_mod_id);

CREATE TABLE steam_mod
(
    id                     TEXT PRIMARY KEY,
    title                  TEXT NULL,
    description            TEXT NULL,
    updated_at             TIMESTAMP NULL,
    required_steam_mod_ids TEXT NULL
);


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


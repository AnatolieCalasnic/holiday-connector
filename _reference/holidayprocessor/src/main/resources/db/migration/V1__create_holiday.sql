CREATE TABLE holiday (
    id            TEXT        PRIMARY KEY,           -- <country>:<date>:<slug>, built by the adapter
    country_code  TEXT        NOT NULL,
    year          INTEGER     NOT NULL,
    date          DATE,
    local_name    TEXT,
    name          TEXT,
    global        BOOLEAN,
    counties      JSONB,
    types         JSONB,
    payload       JSONB       NOT NULL,              -- the full last message, next to the typed columns
    content_hash  TEXT,
    observed_at   TIMESTAMPTZ NOT NULL,              -- when the adapter saw this version
    removed_at    TIMESTAMPTZ,                       -- soft delete: gone from a complete pull
    version       INTEGER     NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX holiday_country_date ON holiday (country_code, date);

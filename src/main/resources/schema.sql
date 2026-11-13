-- noinspection GrazieInspectionForFile

--     CREATE TABLE IF NOT EXISTS events (
--         -- Global position in the log, gaps (skipped numbers) are OK
--         event_sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
--         -- Primary subject of the event; doesn't define an aggregate-like consistency boundary.
--         subject_id     UUID                     NOT NULL,
--         -- Stable across retries of the same event.
--         event_id       UUID                     NOT NULL UNIQUE,
--         -- Logical name used for deserialization into records or classes.
--         event_type     TEXT                     NOT NULL,
--         -- Tags used to select events for live decision contexts and read models.
--         tags           TEXT[]                   NOT NULL DEFAULT '{}',
--         -- Could be stored as TEXT as event payloads shouldn't be queried in the database
--         payload        JSONB                    NOT NULL,
--         -- Supports schema migration: the version of this event
--         schema_version INTEGER                  NOT NULL,
--         -- Time INSERT statement started: if event-creation (command execution) time is needed,
--         -- that should be inside the event payload itself (i.e., business information).
--         created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT statement_timestamp()
--     );
--
-- CREATE INDEX events_subject_sequence_idx
--     ON events (subject_id, event_sequence);
--
-- CREATE INDEX events_tags_idx
--     ON events USING GIN (tags);
--
-- Events table for storing event-sourced aggregates
CREATE TABLE IF NOT EXISTS events (
    aggregate_root_id UUID NOT NULL,
    -- This is a global sequence for ordering events (within and across aggregates)
    -- per Postgres docs, this starts at 1
    event_sequence BIGSERIAL,
    event_type TEXT NOT NULL,
    json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),

    -- Primary key is composite of aggregate_root_id and event_sequence
    PRIMARY KEY (aggregate_root_id, event_sequence)

    -- Optional: Add a version field for schema versioning
    , version INTEGER DEFAULT 1
);

-- Index for global ordering, e.g., for queries like findAllAfter(event_sequence)
CREATE INDEX IF NOT EXISTS idx_events_event_sequence ON events (event_sequence);

CREATE INDEX IF NOT EXISTS idx_events_event_type ON events (event_type);

CREATE INDEX IF NOT EXISTS idx_events_event_type_and_sequence ON events (event_type, event_sequence);

-- Index for timestamp-based queries
CREATE INDEX IF NOT EXISTS idx_events_created_at ON events (created_at);


-- == PROJECTIONS == --

CREATE TABLE IF NOT EXISTS concert_sales_projection
(
    projection_name          TEXT NOT NULL PRIMARY KEY,
    last_event_sequence_seen BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS concert_sales
(
    concert_id               UUID PRIMARY KEY,
    artist_name              TEXT NOT NULL ,
    concert_date             DATE NOT NULL ,
    tickets_sold             INTEGER NOT NULL,
    total_sales              INTEGER NOT NULL,

    -- Foreign key to the parent
    concert_sales_projection TEXT NOT NULL,
    CONSTRAINT fk_projection FOREIGN KEY (concert_sales_projection)
        REFERENCES concert_sales_projection (projection_name)
            ON DELETE CASCADE
);

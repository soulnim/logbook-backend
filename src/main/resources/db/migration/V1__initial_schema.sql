-- ============================================================
-- Logbook — V1: Initial Schema
-- ============================================================

-- Users table (auth added in V2, defined here for clean ordering)
-- Note: users table is created in V2 since auth was added after initial schema.
-- This migration only creates the core entry/tag structure (no user_id yet).

-- Tags table
CREATE TABLE tags (
                      id         BIGSERIAL    PRIMARY KEY,
                      name       VARCHAR(50)  NOT NULL,
                      color      VARCHAR(7)   NOT NULL DEFAULT '#6B7280',
                      created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Core entries table
CREATE TABLE entries (
                         id           BIGSERIAL    PRIMARY KEY,
                         title        VARCHAR(255) NOT NULL,
                         content      TEXT,
                         entry_type   VARCHAR(20)  NOT NULL,
                         entry_date   DATE         NOT NULL,
                         start_time   TIME,
                         end_time     TIME,
                         is_completed BOOLEAN      NOT NULL DEFAULT FALSE,
                         mood         SMALLINT     CHECK (mood BETWEEN 1 AND 5),
                         created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                         updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Entry <-> Tag join table
CREATE TABLE entry_tags (
                            entry_id BIGINT NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
                            tag_id   BIGINT NOT NULL REFERENCES tags(id)    ON DELETE CASCADE,
                            PRIMARY KEY (entry_id, tag_id)
);

-- Indexes for fast calendar queries
CREATE INDEX idx_entries_entry_date ON entries(entry_date);
CREATE INDEX idx_entries_entry_type ON entries(entry_type);
CREATE INDEX idx_entries_date_type  ON entries(entry_date, entry_type);
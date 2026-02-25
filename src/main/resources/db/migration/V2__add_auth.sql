-- ============================================================
-- Logbook — V2: Add users + link entries/tags to a user
-- ============================================================

-- Users table
CREATE TABLE users (
                       id         BIGSERIAL    PRIMARY KEY,
                       google_id  VARCHAR(255) NOT NULL UNIQUE,
                       email      VARCHAR(255) NOT NULL UNIQUE,
                       name       VARCHAR(255) NOT NULL,
                       avatar_url TEXT,
                       created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       last_login TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_google_id ON users(google_id);
CREATE INDEX idx_users_email     ON users(email);

-- Add user_id to entries
-- No backfill needed — on a fresh DB entries table is empty at this point
ALTER TABLE entries
    ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX idx_entries_user_id ON entries(user_id);

-- Add user_id to tags
ALTER TABLE tags
    ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX idx_tags_user_id ON tags(user_id);

-- Tag names are unique per user (not globally)
ALTER TABLE tags
    ADD CONSTRAINT tags_name_user_unique UNIQUE (name, user_id);
-- ============================================================
-- Logbook V3 — GitHub Integration
-- ============================================================
-- Note: entry_type is VARCHAR(20), not a PostgreSQL enum.
-- No schema change needed to support the new 'COMMIT' type.

-- 1. Add GitHub OAuth fields to users
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS github_id           VARCHAR(100),
    ADD COLUMN IF NOT EXISTS github_username     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS github_access_token TEXT,
    ADD COLUMN IF NOT EXISTS github_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS github_sync_from    TIMESTAMP WITH TIME ZONE;

-- 2. Add source_meta to entries (stores GitHub commit JSON for COMMIT-type entries)
ALTER TABLE entries
    ADD COLUMN IF NOT EXISTS source_meta TEXT;

-- 3. Table to track which repos are being watched per user
CREATE TABLE IF NOT EXISTS github_watched_repos (
                                                    id              BIGSERIAL PRIMARY KEY,
                                                    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repo_full_name  VARCHAR(255) NOT NULL,
    repo_name       VARCHAR(255) NOT NULL,
    webhook_id      BIGINT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, repo_full_name)
    );
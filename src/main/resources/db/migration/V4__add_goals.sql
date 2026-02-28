-- ============================================================
-- Logbook V4 — Goals & Milestones
-- ============================================================

CREATE TABLE goals (
                       id           BIGSERIAL    PRIMARY KEY,
                       user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                       title        VARCHAR(255) NOT NULL,
                       description  TEXT,
                       type         VARCHAR(20)  NOT NULL DEFAULT 'PERSONAL',
                       status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                       color        VARCHAR(7)   NOT NULL DEFAULT '#818cf8',
                       target_date  DATE,
                       created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE milestones (
                            id             BIGSERIAL    PRIMARY KEY,
                            goal_id        BIGINT       NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
                            title          VARCHAR(255) NOT NULL,
                            is_completed   BOOLEAN      NOT NULL DEFAULT FALSE,
                            completed_at   TIMESTAMPTZ,
                            display_order  INT          NOT NULL DEFAULT 0,
                            created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_goals_user_id    ON goals(user_id);
CREATE INDEX idx_goals_status     ON goals(user_id, status);
CREATE INDEX idx_milestones_goal  ON milestones(goal_id);
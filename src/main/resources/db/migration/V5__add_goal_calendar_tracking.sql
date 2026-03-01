-- ============================================================
-- Logbook V5 — Goal & Milestone Completion Tracking in Calendar
-- ============================================================

-- Extend entries table with goal/milestone reference columns
ALTER TABLE entries ADD COLUMN goal_reference_id BIGINT;
ALTER TABLE entries ADD COLUMN milestone_reference_id BIGINT;

-- Add indexes for efficient GOAL entry lookups
CREATE INDEX idx_entries_goal_ref       ON entries(goal_reference_id);
CREATE INDEX idx_entries_milestone_ref  ON entries(milestone_reference_id);
CREATE INDEX idx_entries_type           ON entries(entry_type);

-- Compound index for the most common GOAL entry lookup pattern
CREATE INDEX idx_entries_goal_milestone ON entries(goal_reference_id, milestone_reference_id, entry_type);
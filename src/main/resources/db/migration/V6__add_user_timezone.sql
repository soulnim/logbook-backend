-- ============================================================
-- Logbook V6 — User Timezone Preference
-- ============================================================
-- Stores an IANA timezone string (e.g. "Asia/Kuala_Lumpur").
-- NULL means the timezone has never been set by the client —
-- the frontend sends it on first login and it stays fixed
-- unless the user changes it in Settings.
ALTER TABLE users ADD COLUMN timezone VARCHAR(64);
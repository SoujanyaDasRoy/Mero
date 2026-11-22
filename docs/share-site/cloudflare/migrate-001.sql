-- For a database made from the first schema.sql (before rate limits). Run once:
--   wrangler d1 execute mero --remote --file migrate-001.sql
ALTER TABLE groups ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS limits (
  key  TEXT PRIMARY KEY,
  hour INTEGER NOT NULL,
  n    INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS limits_hour ON limits (hour);

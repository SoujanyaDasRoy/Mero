-- Group playlists (docs/superpowers/specs/2026-11-16-group-playlists-design.md).
-- Apply with: wrangler d1 execute mero --remote --file schema.sql
CREATE TABLE IF NOT EXISTS groups (
  code      TEXT PRIMARY KEY,
  name      TEXT NOT NULL,
  has_cover INTEGER NOT NULL DEFAULT 0,
  head      INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS steps (
  code  TEXT NOT NULL,
  seq   INTEGER NOT NULL,
  op_id TEXT NOT NULL,
  kind  TEXT NOT NULL,
  value TEXT,
  at    INTEGER NOT NULL,
  PRIMARY KEY (code, seq),
  UNIQUE (code, op_id)
);
-- Per-sender request counters for rate limits: a salted hash, never an address; older than an hour, deleted.
CREATE TABLE IF NOT EXISTS limits (
  key  TEXT PRIMARY KEY,
  hour INTEGER NOT NULL,
  n    INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS limits_hour ON limits (hour);

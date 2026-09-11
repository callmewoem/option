'use strict';

const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');
const config = require('./config');

const resolvedPath = path.resolve(config.databasePath);
fs.mkdirSync(path.dirname(resolvedPath), { recursive: true });

const db = new Database(resolvedPath);
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

db.exec(`
  CREATE TABLE IF NOT EXISTS devices (
    id TEXT PRIMARY KEY,
    created_at INTEGER NOT NULL
  );

  -- A device's own pairing code, plus the buddy display name it advertises to
  -- whoever redeems it. One live (unredeemed) code per device -- regenerating
  -- replaces it (see routes/buddies.js).
  CREATE TABLE IF NOT EXISTS pairing_codes (
    code TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id),
    display_name TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
  );

  -- Buddy pairing is mutual: redeeming a code inserts both directions in one
  -- transaction (see routes/buddies.js), so each side's GET /buddies lists the
  -- other without either having to separately "accept".
  CREATE TABLE IF NOT EXISTS buddy_links (
    device_id TEXT NOT NULL REFERENCES devices(id),
    buddy_device_id TEXT NOT NULL REFERENCES devices(id),
    pairing_code TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (device_id, buddy_device_id)
  );

  CREATE TABLE IF NOT EXISTS daily_summaries (
    device_id TEXT PRIMARY KEY REFERENCES devices(id),
    date TEXT NOT NULL,
    habits_completed INTEGER NOT NULL,
    total_habits INTEGER NOT NULL,
    current_streak INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  );

  -- One row per device: its current entitlement, last set either by a client-
  -- reported purchase verified against the Play Developer API, or by an RTDN
  -- webhook call keyed by purchase_token. free_verification_month/_count track the
  -- free tier's monthly photo-verification quota (config.freeTier.verificationsPerMonth,
  -- see routes/verify.js) -- folded into this table rather than a separate one since
  -- it's the same "one row per device" shape and always read/written alongside tier.
  CREATE TABLE IF NOT EXISTS entitlements (
    device_id TEXT PRIMARY KEY REFERENCES devices(id),
    tier TEXT NOT NULL DEFAULT 'NONE',
    expires_at INTEGER,
    purchase_token TEXT,
    product_id TEXT,
    updated_at INTEGER NOT NULL,
    free_verification_month TEXT,
    free_verification_count INTEGER NOT NULL DEFAULT 0
  );

  -- Lets an RTDN webhook call (keyed only by purchaseToken/subscriptionId, no
  -- device id) find which device to update.
  CREATE INDEX IF NOT EXISTS idx_entitlements_purchase_token ON entitlements(purchase_token);

  -- A/B test bucketing (see services/experiments.js): one row per (device, experiment),
  -- written once the first time a device asks for its assignments and never touched
  -- again -- deliberately persisted rather than recomputed on every request, so a
  -- device's variant stays stable even if EXPERIMENTS' weights (or variant set) change
  -- later. assigned_at is diagnostic only (when this device first saw the experiment).
  CREATE TABLE IF NOT EXISTS experiment_assignments (
    device_id TEXT NOT NULL REFERENCES devices(id),
    experiment_key TEXT NOT NULL,
    variant TEXT NOT NULL,
    assigned_at INTEGER NOT NULL,
    PRIMARY KEY (device_id, experiment_key)
  );

  -- Analytics events batched up and POSTed by the Android client
  -- (data/repository/AnalyticsRepository.kt) -- see routes/analytics.js. No PII by
  -- construction: device_id is the same anonymous per-install id used everywhere else
  -- in this database, name is a fixed event constant, and properties is a small JSON
  -- blob the client controls (never a photo, a habit's name/text, or anything else
  -- free-form the user typed). Purely an ingest table -- nothing here reads it back out
  -- yet; a future analytics dashboard/export would query it directly.
  CREATE TABLE IF NOT EXISTS analytics_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL REFERENCES devices(id),
    name TEXT NOT NULL,
    properties TEXT NOT NULL DEFAULT '{}',
    client_timestamp INTEGER NOT NULL,
    received_at INTEGER NOT NULL
  );

  CREATE INDEX IF NOT EXISTS idx_analytics_events_name ON analytics_events(name);
  CREATE INDEX IF NOT EXISTS idx_analytics_events_device_id ON analytics_events(device_id);
`);

// Additive migration for a database file created before the free_verification_*
// columns existed above -- CREATE TABLE IF NOT EXISTS doesn't retrofit columns onto
// an already-existing table. Safe to run every boot: each ALTER is skipped once its
// column is already present.
const entitlementColumns = new Set(db.prepare('PRAGMA table_info(entitlements)').all().map((row) => row.name));
if (!entitlementColumns.has('free_verification_month')) {
  db.exec('ALTER TABLE entitlements ADD COLUMN free_verification_month TEXT');
}
if (!entitlementColumns.has('free_verification_count')) {
  db.exec('ALTER TABLE entitlements ADD COLUMN free_verification_count INTEGER NOT NULL DEFAULT 0');
}

module.exports = db;

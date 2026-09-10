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

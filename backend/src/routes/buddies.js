'use strict';

const express = require('express');
const crypto = require('crypto');
const db = require('../db');
const { requireDevice } = require('../auth');
const { getEntitlement } = require('../services/entitlement');
const config = require('../config');

const router = express.Router();

const PAIRING_CODE_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000; // a week -- regenerate if it goes stale

/** 6 chars from an alphabet that excludes visually-confusable characters (0/O, 1/I/L). */
function generateCode() {
  const alphabet = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) code += alphabet[crypto.randomInt(alphabet.length)];
  return code;
}

function countBuddies(deviceId) {
  return db.prepare('SELECT COUNT(*) AS count FROM buddy_links WHERE device_id = ?').get(deviceId).count;
}

/** Free tier gets config.freeTier.maxBuddies (1) real buddy connections -- enough to try the whole feature -- before Premium is required for more. */
function hasRoomForAnotherBuddy(deviceId) {
  return getEntitlement(deviceId).isPremium || countBuddies(deviceId) < config.freeTier.maxBuddies;
}

function loadSummary(deviceId) {
  return db.prepare('SELECT date, habits_completed, total_habits, current_streak FROM daily_summaries WHERE device_id = ?').get(deviceId);
}

function toBuddyJson(buddyDeviceId, pairingCode) {
  const summary = loadSummary(buddyDeviceId);
  return {
    id: buddyDeviceId,
    displayName: 'Buddy',
    pairingCode,
    status: summary ? 'connected' : 'pending',
    lastSummary: summary
      ? {
          date: summary.date,
          habitsCompleted: summary.habits_completed,
          totalHabits: summary.total_habits,
          currentStreak: summary.current_streak,
        }
      : null,
  };
}

/**
 * Mints (or replaces) this device's own shareable pairing code. Not gated on room for
 * another buddy -- minting is free either way, and the room check that actually
 * matters happens at redemption time below (on both sides of the pairing), since
 * that's the moment a new buddy connection is actually created.
 */
router.post('/pairing-codes', requireDevice, (req, res) => {
  db.prepare('DELETE FROM pairing_codes WHERE device_id = ?').run(req.deviceId);

  let code;
  // Extremely unlikely to collide (32^6 space) but loop just in case rather than
  // trust luck against the PRIMARY KEY constraint.
  for (let attempt = 0; attempt < 5; attempt++) {
    code = generateCode();
    const exists = db.prepare('SELECT 1 FROM pairing_codes WHERE code = ?').get(code);
    if (!exists) break;
  }

  const now = Date.now();
  db.prepare(
    'INSERT INTO pairing_codes (code, device_id, display_name, created_at, expires_at) VALUES (?, ?, ?, ?, ?)',
  ).run(code, req.deviceId, 'A Locke user', now, now + PAIRING_CODE_TTL_MILLIS);

  res.status(201).json({ code });
});

/**
 * Redeems a buddy's pairing code -- pairing is mutual, both directions are linked in
 * one transaction. Free tier: checked on *both* sides, since the new connection would
 * push either one over their own cap -- whichever is unpaid and already at the limit
 * is the one named in the error, so the redeemer knows whether it's their own limit
 * or the other person's.
 */
router.post('/buddies', requireDevice, (req, res) => {
  const code = typeof req.body?.code === 'string' ? req.body.code.trim().toUpperCase() : '';
  if (!code) return res.status(400).json({ error: 'code is required.' });

  const pairing = db.prepare('SELECT device_id, expires_at FROM pairing_codes WHERE code = ?').get(code);
  if (!pairing) return res.status(404).json({ error: "That pairing code doesn't exist." });
  if (pairing.expires_at < Date.now()) return res.status(410).json({ error: 'That pairing code has expired.' });
  if (pairing.device_id === req.deviceId) return res.status(400).json({ error: "You can't add yourself as a buddy." });

  if (!hasRoomForAnotherBuddy(req.deviceId)) {
    return res.status(402).json({
      error: `You've reached the free plan's ${config.freeTier.maxBuddies}-buddy limit. Upgrade to add more.`,
    });
  }
  if (!hasRoomForAnotherBuddy(pairing.device_id)) {
    return res.status(402).json({ error: 'That person has already reached their free plan buddy limit.' });
  }

  const link = db.transaction(() => {
    const now = Date.now();
    db.prepare(
      'INSERT OR REPLACE INTO buddy_links (device_id, buddy_device_id, pairing_code, created_at) VALUES (?, ?, ?, ?)',
    ).run(req.deviceId, pairing.device_id, code, now);
    db.prepare(
      'INSERT OR REPLACE INTO buddy_links (device_id, buddy_device_id, pairing_code, created_at) VALUES (?, ?, ?, ?)',
    ).run(pairing.device_id, req.deviceId, code, now);
  });
  link();

  res.status(201).json(toBuddyJson(pairing.device_id, code));
});

/** Every paired buddy's latest known summary. Not gated at all (read-only) -- a lapsed subscriber, or someone below the free cap, still sees who they'd paired with. */
router.get('/buddies', requireDevice, (req, res) => {
  const rows = db.prepare('SELECT buddy_device_id, pairing_code FROM buddy_links WHERE device_id = ?').all(req.deviceId);
  res.json(rows.map((row) => toBuddyJson(row.buddy_device_id, row.pairing_code)));
});

/**
 * Uploads this device's own current daily summary for its buddies to see. Not gated
 * on premium/room -- once a buddy connection exists (free or paid), sharing your own
 * progress with it costs nothing extra and is the entire point of having one.
 */
router.post('/daily-summary', requireDevice, (req, res) => {
  const { date, habitsCompleted, totalHabits, currentStreak } = req.body || {};
  if (typeof date !== 'string' || !date) return res.status(400).json({ error: 'date is required.' });

  db.prepare(
    `INSERT INTO daily_summaries (device_id, date, habits_completed, total_habits, current_streak, updated_at)
     VALUES (@deviceId, @date, @habitsCompleted, @totalHabits, @currentStreak, @updatedAt)
     ON CONFLICT(device_id) DO UPDATE SET
       date = excluded.date,
       habits_completed = excluded.habits_completed,
       total_habits = excluded.total_habits,
       current_streak = excluded.current_streak,
       updated_at = excluded.updated_at`,
  ).run({
    deviceId: req.deviceId,
    date,
    habitsCompleted: Number.isInteger(habitsCompleted) ? habitsCompleted : 0,
    totalHabits: Number.isInteger(totalHabits) ? totalHabits : 0,
    currentStreak: Number.isInteger(currentStreak) ? currentStreak : 0,
    updatedAt: Date.now(),
  });

  res.status(204).end();
});

module.exports = router;

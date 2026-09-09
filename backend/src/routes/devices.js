'use strict';

const express = require('express');
const crypto = require('crypto');
const db = require('../db');
const { issueToken } = require('../auth');

const router = express.Router();

/**
 * Registers a new anonymous device identity. No auth required -- this is how a
 * fresh install gets its first token, mirroring what `DeviceIdentityRepository`
 * on the Android side calls lazily before its first authenticated request.
 * Idempotent-ish in practice: the app calls this at most once per install (it
 * caches the returned token), but nothing stops it being called again -- each
 * call simply mints a brand new, unrelated device id.
 */
router.post('/devices', (req, res) => {
  const deviceId = crypto.randomUUID();
  db.prepare('INSERT INTO devices (id, created_at) VALUES (?, ?)').run(deviceId, Date.now());
  db.prepare(
    'INSERT INTO entitlements (device_id, tier, expires_at, purchase_token, product_id, updated_at) VALUES (?, ?, ?, ?, ?, ?)',
  ).run(deviceId, 'NONE', null, null, null, Date.now());

  res.status(201).json({ deviceId, token: issueToken(deviceId) });
});

module.exports = router;

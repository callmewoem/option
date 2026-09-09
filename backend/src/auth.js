'use strict';

const crypto = require('crypto');
const config = require('./config');
const db = require('./db');

/**
 * Device identity, not user accounts: Locke has no login, so each install
 * registers itself once (POST /v1/devices) and gets back an opaque bearer token
 * scoped to that one device id. The token is `${deviceId}.${hmac}` where `hmac`
 * is HMAC-SHA256(deviceId) under DEVICE_TOKEN_SECRET -- stateless to verify (just
 * recompute and compare), so there's no session table to expire or clean up.
 * Anyone holding a device's token can act as it (push its daily summary, spend
 * its entitlement, redeem/mint its pairing codes) -- acceptable for this app's
 * threat model (no PII, no payment data -- Play Billing itself, not this token,
 * guards purchases), same trust boundary as e.g. a long-lived API key on-device.
 */

function issueToken(deviceId) {
  const hmac = crypto.createHmac('sha256', config.deviceTokenSecret).update(deviceId).digest('hex');
  return `${deviceId}.${hmac}`;
}

function verifyToken(token) {
  if (typeof token !== 'string' || !token.includes('.')) return null;
  const separatorIndex = token.lastIndexOf('.');
  const deviceId = token.slice(0, separatorIndex);
  const providedHmac = token.slice(separatorIndex + 1);
  if (!deviceId || !providedHmac) return null;

  const expectedHmac = crypto.createHmac('sha256', config.deviceTokenSecret).update(deviceId).digest('hex');
  const expected = Buffer.from(expectedHmac, 'hex');
  const provided = Buffer.from(providedHmac, 'hex');
  if (expected.length !== provided.length || !crypto.timingSafeEqual(expected, provided)) return null;
  return deviceId;
}

/** Express middleware: requires `Authorization: Bearer <token>`, sets `req.deviceId`. */
function requireDevice(req, res, next) {
  const header = req.get('authorization') || '';
  const [scheme, token] = header.split(' ');
  if (scheme !== 'Bearer' || !token) {
    return res.status(401).json({ error: 'Missing or malformed Authorization header.' });
  }

  const deviceId = verifyToken(token);
  if (!deviceId) {
    return res.status(401).json({ error: 'Invalid or expired device token.' });
  }

  const device = db.prepare('SELECT id FROM devices WHERE id = ?').get(deviceId);
  if (!device) {
    return res.status(401).json({ error: 'Unknown device -- register again with POST /v1/devices.' });
  }

  req.deviceId = deviceId;
  next();
}

module.exports = { issueToken, verifyToken, requireDevice };

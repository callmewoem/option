'use strict';

require('dotenv').config();
const crypto = require('crypto');

/**
 * Central config, read once at boot. Every value here has a safe fallback so the
 * server starts and most endpoints work for local development with no `.env` at
 * all -- the exceptions (logged clearly at boot, see server.js) are real Claude
 * calls, which need ANTHROPIC_API_KEY, and real Play purchase verification, which
 * needs the GOOGLE_PLAY_* vars.
 */
const config = {
  port: parseInt(process.env.PORT || '8787', 10),
  databasePath: process.env.DATABASE_PATH || './data/locke.sqlite3',

  anthropicApiKey: process.env.ANTHROPIC_API_KEY || null,

  googlePlay: {
    packageName: process.env.GOOGLE_PLAY_PACKAGE_NAME || 'com.habitsfirst.androidclone',
    serviceAccountJsonPath: process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH || null,
  },

  // Devices register once (POST /v1/devices) and get back an opaque bearer token
  // that's just an HMAC of their device id under this secret -- cheap to verify
  // (recompute and compare) without a session table, and rotating this secret
  // invalidates every issued token at once. Randomly generated per-boot if unset,
  // which is fine for local dev but means every device must re-register after a
  // restart -- set DEVICE_TOKEN_SECRET in production so tokens survive restarts.
  deviceTokenSecret: process.env.DEVICE_TOKEN_SECRET || crypto.randomBytes(32).toString('hex'),
};

module.exports = config;

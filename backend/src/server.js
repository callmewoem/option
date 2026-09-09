'use strict';

const express = require('express');
const config = require('./config');
require('./db'); // opens (and migrates) the database as a side effect of require

const devicesRouter = require('./routes/devices');
const verifyRouter = require('./routes/verify');
const buddiesRouter = require('./routes/buddies');
const billingRouter = require('./routes/billing');
const { isConfigured: isGooglePlayConfigured } = require('./services/googlePlay');

const app = express();
app.use(express.json({ limit: '10mb' })); // photos are base64-encoded JSON strings -- default 100kb limit is far too small

app.get('/healthz', (req, res) => res.json({ ok: true }));

const v1 = express.Router();
v1.use(devicesRouter);
v1.use(verifyRouter);
v1.use(buddiesRouter);
v1.use(billingRouter);
app.use('/v1', v1);

// eslint-disable-next-line no-unused-vars
app.use((error, req, res, next) => {
  if (error?.type === 'entity.parse.failed' || error?.type === 'entity.too.large') {
    return res.status(400).json({ error: 'Malformed or oversized request body.' });
  }
  console.error('[server] unhandled error:', error);
  res.status(500).json({ error: 'Internal server error.' });
});

app.listen(config.port, () => {
  console.log(`Locke backend listening on :${config.port}`);
  if (!config.anthropicApiKey) {
    console.warn('[startup] ANTHROPIC_API_KEY is not set -- POST /v1/verify-photo will return 503 until it is.');
  }
  if (!isGooglePlayConfigured()) {
    console.warn(
      '[startup] GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH is not set -- POST /v1/purchases/verify will trust ' +
        'client-reported purchases instead of checking the Play Developer API. Fine for development, not for production.',
    );
  }
});

module.exports = app;

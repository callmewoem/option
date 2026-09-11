'use strict';

const express = require('express');
const { requireDevice } = require('../auth');
const db = require('../db');

const router = express.Router();

const MAX_EVENTS_PER_REQUEST = 200;
const MAX_PROPERTIES_JSON_LENGTH = 4000;
// Event names are fixed constants the client defines (see
// util/AnalyticsEvents.kt), never free-typed user input -- lowercase snake_case only.
const NAME_PATTERN = /^[a-z][a-z0-9_]{0,63}$/;

const insertEvent = db.prepare(
  'INSERT INTO analytics_events (device_id, name, properties, client_timestamp, received_at) VALUES (?, ?, ?, ?, ?)',
);
const insertEvents = db.transaction((rows) => {
  for (const row of rows) {
    insertEvent.run(row.deviceId, row.name, row.properties, row.clientTimestamp, row.receivedAt);
  }
});

/**
 * Ingests a batch of analytics events queued on-device (`data/local/entity/
 * AnalyticsEventEntity.kt`, uploaded by `AnalyticsUploadWorker`). No PII by
 * construction -- see `analytics_events`'s own doc in src/db.js -- and entirely
 * opt-in/opt-out client-side (`PreferencesRepository.analyticsEnabled`): a device with
 * it turned off never calls this at all. Validates just enough to keep the table
 * sane (bounded batch size, a fixed event-name shape, bounded properties size) --
 * this is an ingest endpoint, not a place to enforce product logic.
 */
router.post('/analytics/events', requireDevice, (req, res) => {
  const events = Array.isArray(req.body?.events) ? req.body.events : null;
  if (!events || events.length === 0) {
    return res.status(400).json({ error: 'events must be a non-empty array.' });
  }
  if (events.length > MAX_EVENTS_PER_REQUEST) {
    return res.status(400).json({ error: `Too many events in one request (max ${MAX_EVENTS_PER_REQUEST}).` });
  }

  const now = Date.now();
  const rows = [];
  for (const event of events) {
    if (typeof event?.name !== 'string' || !NAME_PATTERN.test(event.name)) {
      return res.status(400).json({ error: `Invalid event name: ${JSON.stringify(event?.name ?? null)}.` });
    }

    let propertiesJson = '{}';
    if (event.properties !== undefined && event.properties !== null) {
      try {
        propertiesJson = JSON.stringify(event.properties);
      } catch (e) {
        return res.status(400).json({ error: `Event "${event.name}" has unserializable properties.` });
      }
      if (propertiesJson.length > MAX_PROPERTIES_JSON_LENGTH) {
        return res.status(400).json({ error: `Event "${event.name}" properties are too large.` });
      }
    }

    const clientTimestamp = Number.isInteger(event.clientTimestampEpochMillis) ? event.clientTimestampEpochMillis : now;
    rows.push({ deviceId: req.deviceId, name: event.name, properties: propertiesJson, clientTimestamp, receivedAt: now });
  }

  insertEvents(rows);
  res.status(204).end();
});

module.exports = router;

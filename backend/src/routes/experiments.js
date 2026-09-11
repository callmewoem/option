'use strict';

const express = require('express');
const { requireDevice } = require('../auth');
const { getAllAssignments } = require('../services/experiments');

const router = express.Router();

/**
 * This device's A/B assignment for every experiment the backend currently runs (see
 * services/experiments.js) -- pricing/plan presentation, free-tier gating caps, and
 * whether a given feature/step is shown at all. Assigned deterministically and
 * persisted on first call, so repeat calls (every app launch, see
 * `data/repository/ExperimentRepository.kt`) always return the same variant.
 */
router.get('/experiments', requireDevice, (req, res) => {
  res.json({ experiments: getAllAssignments(req.deviceId) });
});

module.exports = router;

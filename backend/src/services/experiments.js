'use strict';

const crypto = require('crypto');
const db = require('../db');

/**
 * Every experiment this backend currently runs. Each is a simple weighted A/B(/n)
 * split -- add a new one by pushing another `{ key, description, variants }` entry;
 * nothing else needs to change (the route, the Android client, and the assignment
 * table are all generic over this list). `key` must be stable forever once shipped --
 * changing it orphans any already-persisted `experiment_assignments` rows and starts
 * everyone fresh. `variants[].value` is the actual config payload the client acts on
 * (a number, a string, whatever) -- keeping it here instead of hardcoding a
 * key->behavior mapping on the client means changing what a variant *does* (e.g.
 * tweaking the "tighter" gating cap from 3 to 4) never needs an app release.
 *
 * Weights don't need to sum to 100 -- [assignVariant] normalizes against whatever they
 * add up to.
 */
const EXPERIMENTS = [
  {
    key: 'gating_habit_cap',
    description: 'Free-tier cap on new GATING habits before Premium is required.',
    variants: [
      { id: 'control', weight: 70, value: '5' },
      { id: 'tighter', weight: 30, value: '3' },
    ],
  },
  {
    key: 'paywall_plan_emphasis',
    description: 'Which subscription plan the paywall sorts first and visually highlights as "Best value".',
    variants: [
      { id: 'control', weight: 50, value: 'annual' },
      { id: 'monthly_first', weight: 50, value: 'monthly' },
    ],
  },
  {
    key: 'onboarding_paywall_step',
    description:
      'Whether onboarding ends with its own skippable Premium pitch step, or skips straight to Home (the pitch ' +
      'is still reachable from Settings either way) -- a feature-level (not just copy/pricing) test.',
    variants: [
      { id: 'shown', weight: 80, value: 'shown' },
      { id: 'hidden', weight: 20, value: 'hidden' },
    ],
  },
];

/**
 * Deterministically buckets `deviceId` into one of `variants` for `experimentKey`.
 * Stable and stateless by construction -- the same inputs always produce the same
 * output, so this alone would be enough for a device that's never been assigned before
 * to get a consistent variant across requests even without [getOrCreateAssignment]'s
 * persistence. Not cryptographically meaningful, just a well-distributed, deterministic
 * bucket: sha256(deviceId:experimentKey) mod (sum of weights), walked against each
 * variant's cumulative weight range.
 */
function assignVariant(deviceId, experimentKey, variants) {
  const totalWeight = variants.reduce((sum, variant) => sum + variant.weight, 0);
  const hash = crypto.createHash('sha256').update(`${deviceId}:${experimentKey}`).digest();
  const bucket = hash.readUInt32BE(0) % totalWeight;

  let cumulative = 0;
  for (const variant of variants) {
    cumulative += variant.weight;
    if (bucket < cumulative) return variant;
  }
  return variants[variants.length - 1]; // unreachable unless weights are misconfigured (e.g. all zero)
}

/**
 * The device's persisted variant for one experiment, assigning (and storing) one on
 * first ask. Persisting rather than recomputing on every call means a device's variant
 * survives [EXPERIMENTS] later changing that experiment's weights or dropping a variant
 * it was already in (the stale variant id then just falls back to the first current
 * variant, treated as "control").
 */
function getOrCreateAssignment(deviceId, experiment) {
  const existing = db
    .prepare('SELECT variant FROM experiment_assignments WHERE device_id = ? AND experiment_key = ?')
    .get(deviceId, experiment.key);
  if (existing) {
    return experiment.variants.find((variant) => variant.id === existing.variant) ?? experiment.variants[0];
  }

  const assigned = assignVariant(deviceId, experiment.key, experiment.variants);
  db.prepare(
    'INSERT INTO experiment_assignments (device_id, experiment_key, variant, assigned_at) VALUES (?, ?, ?, ?)',
  ).run(deviceId, experiment.key, assigned.id, Date.now());
  return assigned;
}

/** [deviceId]'s assignment for every experiment in [EXPERIMENTS], as `{key, variant, value}`. */
function getAllAssignments(deviceId) {
  return EXPERIMENTS.map((experiment) => {
    const variant = getOrCreateAssignment(deviceId, experiment);
    return { key: experiment.key, variant: variant.id, value: variant.value };
  });
}

module.exports = { EXPERIMENTS, assignVariant, getAllAssignments };

'use strict';

const express = require('express');
const { requireDevice } = require('../auth');
const { verifyPhoto, AnthropicNotConfiguredError } = require('../services/anthropic');
const { getEntitlement, remainingFreeVerifications, consumeFreeVerification } = require('../services/entitlement');
const config = require('../config');

const router = express.Router();

/** "yyyy-MM" in UTC -- doesn't need to agree with the client's own local-timezone month string to the minute, just closely enough that neither side's count drifts by more than an edge-of-month check or two. */
function currentMonth() {
  return new Date().toISOString().slice(0, 7);
}

/**
 * Photo verification for a [HabitType.PHOTO] habit or the morning check-in --
 * replaces the Android app's old direct-to-Anthropic call
 * (`AnthropicImageVerificationClient`). The client sends the photo(s), this
 * forwards to Claude using the server's own key (see services/anthropic.js) and
 * returns just the verdict -- the Anthropic API key itself never reaches the app.
 *
 * Free tier gets config.freeTier.verificationsPerMonth checks a month before Premium
 * is required -- enforced here server-side (in addition to whatever the client
 * checks) since every call costs real money against ANTHROPIC_API_KEY, so a modified
 * or older client can't grant itself more free checks than this by skipping its own
 * local check. The quota is only spent once a check actually gets a verdict back
 * (approved or rejected both cost a real Anthropic call) -- never on a request that
 * fails before that.
 */
router.post('/verify-photo', requireDevice, async (req, res) => {
  const entitlement = getEntitlement(req.deviceId);
  const nowMonth = currentMonth();
  if (!entitlement.isPremium && remainingFreeVerifications(req.deviceId, nowMonth, config.freeTier.verificationsPerMonth) <= 0) {
    return res.status(402).json({
      error: `You've used this month's ${config.freeTier.verificationsPerMonth} free AI photo checks. Upgrade for unlimited.`,
    });
  }

  const { habitName, description, exampleImageBase64, submittedImageBase64 } = req.body || {};
  if (typeof habitName !== 'string' || !habitName.trim()) {
    return res.status(400).json({ error: 'habitName is required.' });
  }
  if (typeof submittedImageBase64 !== 'string' || !submittedImageBase64) {
    return res.status(400).json({ error: 'submittedImageBase64 is required.' });
  }

  try {
    const result = await verifyPhoto({
      habitName,
      description: typeof description === 'string' ? description : null,
      exampleImageBase64: typeof exampleImageBase64 === 'string' ? exampleImageBase64 : null,
      submittedImageBase64,
    });
    if (!entitlement.isPremium) {
      consumeFreeVerification(req.deviceId, nowMonth, config.freeTier.verificationsPerMonth);
    }
    res.json(result);
  } catch (error) {
    if (error instanceof AnthropicNotConfiguredError) {
      return res.status(503).json({ error: 'Photo verification is temporarily unavailable -- try again later.' });
    }
    const status = Number.isInteger(error.status) ? error.status : 502;
    res.status(status >= 400 && status < 600 ? status : 502).json({ error: error.message || 'Verification failed.' });
  }
});

module.exports = router;

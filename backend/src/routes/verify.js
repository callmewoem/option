'use strict';

const express = require('express');
const { requireDevice } = require('../auth');
const { verifyPhoto, AnthropicNotConfiguredError } = require('../services/anthropic');
const { getEntitlement } = require('../services/entitlement');

const router = express.Router();

/**
 * Photo verification for a [HabitType.PHOTO] habit or the morning check-in --
 * replaces the Android app's old direct-to-Anthropic call
 * (`AnthropicImageVerificationClient`). The client sends the photo(s), this
 * forwards to Claude using the server's own key (see services/anthropic.js) and
 * returns just the verdict -- the Anthropic API key itself never reaches the app.
 *
 * Gated on premium server-side (in addition to whatever the client checks) since
 * every call costs real money against ANTHROPIC_API_KEY -- a modified or older
 * client can't bypass this by skipping its own local check.
 */
router.post('/verify-photo', requireDevice, async (req, res) => {
  const entitlement = getEntitlement(req.deviceId);
  if (!entitlement.isPremium) {
    return res.status(402).json({ error: 'Photo verification is a premium feature. Upgrade to use it.' });
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

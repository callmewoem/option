'use strict';

const express = require('express');
const { requireDevice } = require('../auth');
const { getEntitlement, setEntitlement, findDeviceByPurchaseToken } = require('../services/entitlement');
const googlePlay = require('../services/googlePlay');

const router = express.Router();

/**
 * Product/subscription IDs configured in Play Console -- must match
 * `SubscriptionProducts` on the Android side exactly (see
 * `app/src/main/java/.../data/billing/SubscriptionProducts.kt`).
 */
const PRODUCT_TIERS = {
  locke_premium_monthly: { tier: 'MONTHLY', productType: 'subs' },
  locke_premium_annual: { tier: 'ANNUAL', productType: 'subs' },
  locke_premium_lifetime: { tier: 'LIFETIME', productType: 'inapp' },
};

function entitlementJson(deviceId) {
  const entitlement = getEntitlement(deviceId);
  return {
    tier: entitlement.tier,
    isPremium: entitlement.isPremium,
    expiresAtEpochMillis: entitlement.expiresAtEpochMillis,
  };
}

/**
 * Verifies a just-completed Play Billing purchase and, once confirmed, records
 * the resulting entitlement server-side -- this row, not anything the app
 * asserts on its own, is what POST /v1/verify-photo and the buddy endpoints
 * check before doing anything that costs money or is a paid feature.
 *
 * Falls back to trusting the client-reported product/tier when
 * GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH isn't set (logged loudly) -- convenient
 * against Play's license-tester sandbox during development, but a real
 * deployment must set it so a modified client can't just claim premium.
 */
router.post('/purchases/verify', requireDevice, async (req, res) => {
  const { productId, purchaseToken } = req.body || {};
  if (typeof productId !== 'string' || !PRODUCT_TIERS[productId]) {
    return res.status(400).json({ error: 'Unknown productId.' });
  }
  if (typeof purchaseToken !== 'string' || !purchaseToken) {
    return res.status(400).json({ error: 'purchaseToken is required.' });
  }

  const { tier, productType } = PRODUCT_TIERS[productId];

  try {
    let expiresAtEpochMillis = null;

    if (googlePlay.isConfigured()) {
      if (productType === 'subs') {
        const result = await googlePlay.verifySubscriptionPurchase(productId, purchaseToken);
        if (!result.isActive) {
          return res.status(402).json({ error: 'That subscription is not currently active.' });
        }
        expiresAtEpochMillis = result.expiresAtEpochMillis;
      } else {
        const result = await googlePlay.verifyProductPurchase(productId, purchaseToken);
        if (!result.isPurchased) {
          return res.status(402).json({ error: 'That purchase could not be verified.' });
        }
        expiresAtEpochMillis = null; // lifetime never expires
      }
    } else {
      console.warn(
        '[billing] GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH is not set -- trusting the client-reported ' +
          `purchase for device ${req.deviceId} without verifying it against the Play Developer API. ` +
          'Fine for local development, not safe for production.',
      );
      if (productType === 'subs') {
        expiresAtEpochMillis = Date.now() + (productId === 'locke_premium_annual' ? 365 : 30) * 24 * 60 * 60 * 1000;
      }
    }

    setEntitlement(req.deviceId, { tier, expiresAtEpochMillis, purchaseToken, productId });
    res.json(entitlementJson(req.deviceId));
  } catch (error) {
    console.error('[billing] purchase verification failed:', error);
    res.status(502).json({ error: "Couldn't verify that purchase with Google Play -- try again shortly." });
  }
});

/** The device's current entitlement, as last recorded by a verified purchase or an RTDN update. */
router.get('/entitlement', requireDevice, (req, res) => {
  res.json(entitlementJson(req.deviceId));
});

/**
 * Google Play Real-time Developer Notifications webhook (Pub/Sub push
 * endpoint) -- keeps entitlements current between app opens as subscriptions
 * renew, cancel, or enter a grace period, without waiting for the client to
 * ask. Configure this URL as a Pub/Sub push subscription on the topic set in
 * Play Console -> Monetization setup -> Real-time developer notifications.
 *
 * Always responds 200 (even on an internal error, logged instead) once the
 * payload is at least parseable -- Pub/Sub retries non-2xx responses, and a
 * transient failure here shouldn't cause a redelivery storm; the next RTDN or
 * the client's own periodic refresh() will reconcile it.
 */
router.post('/rtdn', express.json({ type: '*/*' }), async (req, res) => {
  res.status(200).end(); // ack immediately; Pub/Sub doesn't wait for the work below

  try {
    const dataBase64 = req.body?.message?.data;
    if (!dataBase64) return;
    const payload = JSON.parse(Buffer.from(dataBase64, 'base64').toString('utf8'));

    const subscriptionNotification = payload.subscriptionNotification;
    const oneTimeProductNotification = payload.oneTimeProductNotification;

    if (subscriptionNotification) {
      const { purchaseToken, subscriptionId } = subscriptionNotification;
      const deviceId = findDeviceByPurchaseToken(purchaseToken);
      if (!deviceId || !googlePlay.isConfigured()) return;

      const result = await googlePlay.verifySubscriptionPurchase(subscriptionId, purchaseToken);
      const productEntry = PRODUCT_TIERS[subscriptionId];
      setEntitlement(deviceId, {
        tier: result.isActive ? productEntry?.tier ?? 'NONE' : 'NONE',
        expiresAtEpochMillis: result.expiresAtEpochMillis,
        purchaseToken,
        productId: subscriptionId,
      });
    } else if (oneTimeProductNotification) {
      const { purchaseToken, sku } = oneTimeProductNotification;
      const deviceId = findDeviceByPurchaseToken(purchaseToken);
      if (!deviceId || !googlePlay.isConfigured()) return;

      const result = await googlePlay.verifyProductPurchase(sku, purchaseToken);
      setEntitlement(deviceId, {
        tier: result.isPurchased ? 'LIFETIME' : 'NONE',
        expiresAtEpochMillis: null,
        purchaseToken,
        productId: sku,
      });
    }
  } catch (error) {
    console.error('[billing] RTDN handling failed:', error);
  }
});

module.exports = router;

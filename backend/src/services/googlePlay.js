'use strict';

const config = require('../config');

let cachedClient = null;

function isConfigured() {
  return Boolean(config.googlePlay.serviceAccountJsonPath || process.env.GOOGLE_APPLICATION_CREDENTIALS);
}

/** Lazily builds (and caches) an authenticated Android Publisher API client. */
async function androidPublisher() {
  if (cachedClient) return cachedClient;

  // Required lazily -- `googleapis` is a large dependency and importing it eagerly
  // would slow down boot for the common local-dev case where Play verification
  // isn't configured at all yet.
  const { google } = require('googleapis');
  const auth = new google.auth.GoogleAuth({
    keyFile: config.googlePlay.serviceAccountJsonPath || undefined,
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  });
  cachedClient = google.androidpublisher({ version: 'v3', auth });
  return cachedClient;
}

/**
 * Verifies a subscription purchase token against the real Play Developer API.
 * Returns null if the token is invalid/expired/doesn't belong to this app;
 * otherwise `{ isActive, expiresAtEpochMillis, acknowledged }`.
 */
async function verifySubscriptionPurchase(subscriptionId, purchaseToken) {
  const publisher = await androidPublisher();
  const { data } = await publisher.purchases.subscriptionsv2.get({
    packageName: config.googlePlay.packageName,
    token: purchaseToken,
  });

  const isActive = data.subscriptionState === 'SUBSCRIPTION_STATE_ACTIVE' || data.subscriptionState === 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD';
  const expiryTimeMillis = data.lineItems?.[0]?.expiryTime ? Date.parse(data.lineItems[0].expiryTime) : null;

  return {
    isActive,
    expiresAtEpochMillis: expiryTimeMillis,
    acknowledged: data.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
  };
}

/** Verifies a one-time (lifetime) purchase token. Returns `{ isPurchased, acknowledged }`. */
async function verifyProductPurchase(productId, purchaseToken) {
  const publisher = await androidPublisher();
  const { data } = await publisher.purchases.products.get({
    packageName: config.googlePlay.packageName,
    productId,
    token: purchaseToken,
  });

  // purchaseState: 0 = purchased, 1 = canceled, 2 = pending.
  return {
    isPurchased: data.purchaseState === 0,
    acknowledged: data.acknowledgementState === 1,
  };
}

module.exports = { isConfigured, verifySubscriptionPurchase, verifyProductPurchase };

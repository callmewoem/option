'use strict';

const db = require('../db');

/** Tiers that never expire once granted (a one-time purchase, not a renewing subscription). */
const NON_EXPIRING_TIERS = new Set(['LIFETIME']);

function getEntitlement(deviceId) {
  const row = db.prepare('SELECT tier, expires_at, purchase_token, product_id FROM entitlements WHERE device_id = ?').get(deviceId);
  if (!row) return { tier: 'NONE', expiresAtEpochMillis: null, isPremium: false };

  const isPremium =
    row.tier !== 'NONE' &&
    (NON_EXPIRING_TIERS.has(row.tier) || row.expires_at === null || row.expires_at > Date.now());

  return { tier: row.tier, expiresAtEpochMillis: row.expires_at, isPremium };
}

function setEntitlement(deviceId, { tier, expiresAtEpochMillis, purchaseToken, productId }) {
  db.prepare(
    `INSERT INTO entitlements (device_id, tier, expires_at, purchase_token, product_id, updated_at)
     VALUES (@deviceId, @tier, @expiresAtEpochMillis, @purchaseToken, @productId, @updatedAt)
     ON CONFLICT(device_id) DO UPDATE SET
       tier = excluded.tier,
       expires_at = excluded.expires_at,
       purchase_token = excluded.purchase_token,
       product_id = excluded.product_id,
       updated_at = excluded.updated_at`,
  ).run({
    deviceId,
    tier,
    expiresAtEpochMillis: expiresAtEpochMillis ?? null,
    purchaseToken: purchaseToken ?? null,
    productId: productId ?? null,
    updatedAt: Date.now(),
  });
}

/** Looks up which device holds a given Play purchase token -- how the RTDN webhook (no device id in the payload) finds who to update. */
function findDeviceByPurchaseToken(purchaseToken) {
  const row = db.prepare('SELECT device_id FROM entitlements WHERE purchase_token = ?').get(purchaseToken);
  return row?.device_id ?? null;
}

module.exports = { getEntitlement, setEntitlement, findDeviceByPurchaseToken };

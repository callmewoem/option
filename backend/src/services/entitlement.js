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

function usedFreeVerificationsThisMonth(deviceId, nowMonth) {
  const row = db.prepare('SELECT free_verification_month, free_verification_count FROM entitlements WHERE device_id = ?').get(deviceId);
  if (!row || row.free_verification_month !== nowMonth) return 0;
  return row.free_verification_count;
}

/** How many of this free-tier device's monthly photo-verification checks are left. Meaningless for a premium device (unlimited) -- callers should check `getEntitlement(deviceId).isPremium` first. */
function remainingFreeVerifications(deviceId, nowMonth, limit) {
  return Math.max(0, limit - usedFreeVerificationsThisMonth(deviceId, nowMonth));
}

/**
 * Spends one of this month's free verification checks if any are left. Node/
 * better-sqlite3 calls are synchronous, so nothing else can run between the read and
 * the write below within one process -- no explicit locking needed for this to be
 * atomic. Returns whether it was spent.
 */
function consumeFreeVerification(deviceId, nowMonth, limit) {
  const used = usedFreeVerificationsThisMonth(deviceId, nowMonth);
  if (used >= limit) return false;
  db.prepare(
    'UPDATE entitlements SET free_verification_month = ?, free_verification_count = ?, updated_at = ? WHERE device_id = ?',
  ).run(nowMonth, used + 1, Date.now(), deviceId);
  return true;
}

module.exports = {
  getEntitlement,
  setEntitlement,
  findDeviceByPurchaseToken,
  remainingFreeVerifications,
  consumeFreeVerification,
};

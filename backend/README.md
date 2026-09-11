# Locke backend

A small Node/Express service the Android app talks to for three things that
can't live on-device:

1. **Photo verification** (`POST /v1/verify-photo`) -- forwards a submitted
   proof photo to Claude using a server-held `ANTHROPIC_API_KEY`, so the key
   never ships inside the APK. Used by both photo-verification habits and the
   morning check-in (they share the same client on the Android side).
2. **Accountability buddies** (`POST /v1/pairing-codes`, `POST /v1/buddies`,
   `GET /v1/buddies`, `POST /v1/daily-summary`) -- pairing codes and daily
   summary sync between paired devices.
3. **Google Play subscriptions** (`POST /v1/purchases/verify`,
   `GET /v1/entitlement`, `POST /v1/rtdn`) -- verifies a completed Play
   Billing purchase against the real Play Developer API and stores the
   resulting entitlement, which the two endpoints above check before doing
   anything that costs money (verification) or is a paid feature (buddies).
4. **A/B experiment assignment** (`GET /v1/experiments`) -- deterministically
   buckets a device into a variant of each experiment defined in
   `src/services/experiments.js` (pricing presentation, free-tier gating cap,
   whether a given onboarding step is shown at all) and persists the
   assignment so it stays stable even if an experiment's weights change later.
5. **Analytics ingestion** (`POST /v1/analytics/events`) -- accepts batched,
   non-identifying product-analytics events queued by the Android client
   (`data/repository/AnalyticsRepository.kt`) and stores them in
   `analytics_events`. No dashboard/export exists yet -- query the table
   directly.

No user accounts: each install registers itself once
(`POST /v1/devices`, no auth) and gets back an opaque bearer token scoped to
that one device id (`data/remote/DeviceIdentityRepository.kt` on the Android
side calls this lazily and caches the token). Every other endpoint requires
`Authorization: Bearer <token>`.

The free tier isn't a trial -- `config.freeTier` (`src/config.js`) sizes it to
be generously usable on its own: `verificationsPerMonth` (3, resets every
calendar month) gates `/v1/verify-photo`, `maxBuddies` (1) gates `/v1/buddies`
(checked on *both* sides of a pairing, since a new connection could push
either device over its own cap). These numbers must match the Android app's
own copies (`PreferencesRepository.FREE_VERIFICATIONS_PER_MONTH` /
`MAX_FREE_BUDDIES`) -- the client checks them too, for instant feedback with
no round trip, but this server-side check is the actual enforcement.

## Running locally

```bash
cd backend
cp .env.example .env   # fill in ANTHROPIC_API_KEY at minimum
npm install
npm start               # or `npm run dev` to restart on file changes
```

Data is a single SQLite file (`better-sqlite3`, see `src/db.js`) written to
`DATABASE_PATH` (defaults to `./data/locke.sqlite3`, git-ignored). No separate
database server to run.

Point the Android app at it by setting the `BACKEND_BASE_URL` Gradle property
(see the root `README.md`'s Backend section) to
`http://10.0.2.2:8787/v1` for an emulator (`10.0.2.2` is the emulator's alias
for the host machine's `localhost`) or your machine's LAN IP for a physical
device on the same network.

Every var in `.env.example` has a safe fallback except `ANTHROPIC_API_KEY`
and the `GOOGLE_PLAY_*` ones -- the server starts and most endpoints work
without them, but `/v1/verify-photo` returns 503 until a key is set, and
`/v1/purchases/verify` trusts whatever product/tier the client reports
instead of checking the Play Developer API until a service account is
configured (loud warnings on boot either way).

## Deploying

This is a plain stateless-except-for-SQLite Node process -- run it anywhere
that can run `npm start` behind HTTPS and give it a persistent volume for
`DATABASE_PATH` (a single-file SQLite database, so no separate DB service to
provision): a small VM, a container platform (Fly.io, Render, Railway,
Cloud Run with a mounted volume), etc. Whatever the app's own hosting
conventions are, this is not tied to them.

Production checklist beyond `.env`:
- Put it behind HTTPS (the Android client only ever calls the base URL over
  `https://`) and a real domain.
- Set `DEVICE_TOKEN_SECRET` explicitly -- left unset, a fresh random one is
  generated every boot, which invalidates every previously issued device
  token (every install would have to silently re-register) on every restart.
- Set `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_PATH` (a service account with API
  access granted in Play Console -> Setup -> API access) so purchases are
  actually verified against Play, not just trusted from the client.
- Configure a Pub/Sub push subscription pointing at
  `POST https://<your-host>/v1/rtdn` (Play Console -> Monetization setup ->
  Real-time developer notifications) so subscription renewals/cancellations
  update entitlements without waiting on the client.

## Endpoint reference

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/v1/devices` | none | Registers a new device, returns `{deviceId, token}`. |
| POST | `/v1/verify-photo` | device | `{habitName, description?, exampleImageBase64?, submittedImageBase64}` -> `{approved, reasoning}`. 402 once premium and this month's free quota are both exhausted. |
| POST | `/v1/pairing-codes` | device | Mints this device's own pairing code. Always available (minting is free; the cap is checked at redemption). |
| POST | `/v1/buddies` | device | `{code}` -> redeems a buddy's code, pairs mutually. 402 if either side (redeemer or code owner) is non-premium and already at `maxBuddies`. |
| GET | `/v1/buddies` | device | Lists paired buddies with their last-synced summary. Always available. |
| POST | `/v1/daily-summary` | device | Uploads this device's today summary. Always available once a buddy connection exists. |
| POST | `/v1/purchases/verify` | device | `{productId, purchaseToken}` -> verifies with Play, records entitlement. |
| GET | `/v1/entitlement` | device | This device's current `{tier, isPremium, expiresAtEpochMillis}`. |
| POST | `/v1/rtdn` | Pub/Sub push | Play's Real-time Developer Notifications webhook. |
| GET | `/v1/experiments` | device | This device's persisted `{key, variant, value}` for every experiment in `src/services/experiments.js`. |
| POST | `/v1/analytics/events` | device | `{events: [{name, properties?, clientTimestampEpochMillis?}, ...]}` (max 200/request) -> 204. |
| GET | `/healthz` | none | Liveness check. |
| GET | `/privacy` | none | Plain-text privacy policy (`privacy-policy.md`, kept in sync with the root `PRIVACY_POLICY.md`) -- the public URL an app-store listing points at. |

## Known follow-ups

- SQLite is fine at this app's likely scale but doesn't horizontally scale
  past one writer process -- swap `src/db.js` for a hosted Postgres/MySQL if
  that ever matters, the query surface here is small.
- Pairing codes are a week-long TTL with no rate limiting on redemption
  attempts -- fine for a habit app's threat model, but add a per-device
  attempt counter before this ever handles anything more sensitive.
- No structured logging/metrics/request tracing -- `console.log`/`console.warn`
  only, adequate for a service this size but worth swapping for something
  real (pino, OpenTelemetry) before it's under real load.
- `analytics_events` has no dashboard, export, or retention/pruning job yet --
  it's an ingest-only table today; add a scheduled cleanup before it grows
  unbounded, and something to actually query it (even just scheduled SQL
  reports) before it's useful day to day.
- `POST /v1/analytics/events` has no per-device rate limiting -- a modified
  client could flood the table. Low-risk today (no PII, cheap inserts), but
  worth a simple per-device/per-minute cap before this is under real load,
  same caveat as the pairing-code endpoint above.

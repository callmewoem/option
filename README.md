# Locke (Android)

An independent Android implementation of the [habitsfirst.com](https://habitsfirst.com)
concept, taken further: **do your habits, then use your apps** -- plus a punish/reward
loop around it. You pick the apps that eat your time and the habits that matter more;
Locke locks those apps behind a full-screen checklist until every gating habit for the
day is done, then unlocks them automatically. On top of that: antihabits you can slip
on and pay for, a hard bedtime curfew, a daily lootbox, and a plain non-repeating todo
list.

This is not affiliated with, endorsed by, or built from the source of the original
iOS app -- it's a from-scratch Android app that reproduces the same idea using
Android's own APIs (Accessibility Service + `UsageStatsManager`, since Android has
no equivalent of iOS's Screen Time / Shortcuts APIs the original relies on).

## How it works

1. **Onboarding** -- a 6-step flow: grant Usage Access up front so the app-picking
   step can recommend apps by this phone's *actual* screen time instead of a
   generic guess (skip it and that step falls back to a curated list of
   commonly-distracting apps); pick which installed apps to lock; pick a few
   starter habits (steps, exercise, meditation, or a plain custom check-in);
   grant the remaining two special permissions; set up (optional) bedtime/morning
   check-in; then a skippable Premium pitch. Picking 2+ habits also asks you to rank them easiest-first
   ("ease into it"): only the easiest gates your apps right away, and each one
   after it is promoted from tracked-only to gating automatically once the habit
   before it has been a consistent streak for a configurable number of days
   (`EaseInRepository`, checked on the same periodic worker as usage tracking).
   Home surfaces the ramp's progress with a small banner while it's in flight.
2. **Home** -- shows today's gating habits with live progress, your current streak,
   and how many apps are locked right now.
3. **The lock** -- an `AccessibilityService` watches for foreground-app changes.
   The instant you switch into a locked app while it isn't allowed to be open yet,
   it covers the screen with `BlockOverlayActivity`. Android has no public API to
   prevent an app from launching outright, so this "detect, then cover" technique
   is what every Play Store app-blocker uses. An app is allowed to be open once
   today's gating habits are done, no active penalty lock is running, and (if
   limited unblocking is on) that post-completion window hasn't run out -- unless
   it's bedtime, which no token bypasses, you can also redeem a grace token to
   bypass the rest of that.
4. **Habit kinds** -- every habit is one of three kinds:
   - *Gating* -- must be done today or your locked apps stay locked (the default).
   - *Tracked* -- logged and shown on the heatmap, never blocks anything.
   - *Antihabit* -- silence is success. A day you don't log it is a clean day
     (green); logging it records a slip (red) and immediately extends the block
     lock via `PenaltyRepository`.

   `Home` is where you do all of the above -- every kind, plus today's todos,
   completable inline so there's no reason to leave it. `Stats` is pure
   read-only stats (heatmap, completion rate by habit, completion rate by day
   of week) -- nothing to tap. Managing the habit list itself (add/edit/delete)
   lives in Settings.
5. **Habit types** (independent of kind, above):
   - *Timed* -- N minutes of anything (a workout, meditating, whatever), tracked
     with a built-in stopwatch.
   - *Use an app for N minutes* (the "use Duolingo" style habit) -- tracked
     automatically via `UsageStatsManager`, refreshed every 15 minutes and again
     immediately after you leave the tracked app.
   - *Photo* -- gated on a proof photo instead of an honor-system toggle (see
     *Photo verification* below).
   - *Tally* -- a plain manual check-in, no automatic tracking.
   - *Walk N steps* -- synced from Health Connect once permission is granted in
     Settings (manual fallback otherwise).
6. **Stats tab** -- a GitHub-style heatmap (Canvas-drawn, shaded by the fraction of
   gating habits completed each day in 5 buckets like GitHub's, with a cosmetic
   gold-star overlay on days a lootbox awarded one; a day marked broken by a
   penalty reads as failed even if habits were later completed), plus two
   distribution views computed from the same window: completion rate per habit
   (a habit created partway through the window is rated only over the days it
   actually existed) and average completion rate by day of week.
7. **Penalties** -- a small, reusable engine (`PenaltyRepository`) with three
   primitives everything else composes from: extend the block lock by N minutes,
   add a one-day makeup habit, or mark a day's streak broken. Right now the only
   trigger is an antihabit slip (+10 minutes locked); the primitives are there for
   more triggers later.
8. **Bedtime lock** (Settings) -- an optional hard curfew window (wraps midnight,
   e.g. 22:30-06:30). Blocked apps stay locked for its entire duration regardless
   of habit completion, penalties, or a grace token -- there is no bypass.
9. **Limited unblocking** (Settings, optional) -- by default, finishing today's
   gating habits unlocks blocked apps/sites for the rest of the day. Turn this on
   and that unlock only lasts an hour (`LimitedUnblockRepository`) before they
   re-lock, even though the habits stay complete; a grace token still bypasses it,
   same as it bypasses habit gating.
10. **Daily lootbox** -- awarded once per day, the moment every gating habit is
    done. Weighted reward pool: a grace-period token (common, 1-minute unblock,
    redeemed from a lock screen, never during bedtime), a cosmetic gold star on
    today's heatmap cell (uncommon), a new theme accent unlock (uncommon), or a
    task-skip token (rare -- force-completes one gating habit for the day, redeemed
    from Settings).
11. **Todos** -- plain non-repeating one-off tasks for today, added and checked off
    inline on Home (there's no separate Todos tab). A periodic worker posts a
    reminder to fill them in once it's near your configured morning time (a
    ~15-minute-cadence check, not an exact alarm).
12. **Morning check-in** (Settings, optional) -- a daily proof-of-life photo, due by a
    configured time plus a grace window. Miss it and `PenaltyRepository` extends the
    block lock, same as an antihabit slip. It's a thin wrapper around the same
    capture/verify flow as photo verification below, not tied to any habit.
13. Progress resets automatically at midnight because completion is stored keyed by
    calendar date, not as a flag that has to be cleared.

### Photo verification (3 free checks/month, unlimited on Premium)

A *Photo* habit is gated on a proof photo instead of a manual honor-system toggle --
when setting it up you write what a proof photo should show (e.g. "a made bed"),
attach an example photo, or both. To
complete it for the day, tap the habit, take a photo with the camera, and submit
it — capture is camera-only by design, so a stored gallery photo can't stand in for
today's proof. `BackendImageVerificationClient` sends it (downscaled, alongside the
example photo if any) to Locke's own backend (`backend/`, see its own README),
which forwards it to the Claude Messages API using a server-held key and marks the
habit done only if the model approves, showing its one-sentence reasoning either
way. The Anthropic API key never ships inside the app -- it lives only in the
backend process's environment. Every real device call costs money against the
backend's Anthropic usage, so it's rate-limited on the free tier -- 3 checks free
every month (resets monthly, not a one-time trial), unlimited on Premium -- checked
both client-side and, for defense in depth, server-side. Photos are stored locally
under the app's own storage (`data/verification/`, `util/ImageStore.kt`) and only
ever leave the device as part of one verification request to Locke's own backend,
never straight to a third party.

The morning check-in above shares this exact capture/verify UI
(`ui/components/PhotoVerificationCapture.kt`) and the same `ImageVerificationClient`
-- its own screen (`ui/proofoflife/`) just supplies a fixed prompt instead of a
habit's own rules, and confirms `ProofOfLifeRepository` instead of a habit on
approval, and draws from the same monthly free-check allowance. Its photos aren't
kept once verified (`ImageStore.saveToCache`), since there's nothing to show again
later -- just today's yes/no.

## Project structure

```
app/src/main/java/com/locke/app/
├── data/            Room entities/DAOs, repositories (habits, blocked apps,
│                    penalties, bedtime, lootbox, todos), DataStore preferences,
│                    billing/ (Play Billing + entitlement), remote/ (backend HTTP
│                    clients + device identity), verification/ (photo-verification
│                    client)
├── domain/model/    Plain Kotlin models (Habit, HabitKind, HabitType, BlockedApp,
│                    ThemeVariant, LootboxReward, Todo, PremiumProduct, ...)
├── di/              Hilt modules
├── service/         AccessibilityService, WorkManager workers (usage tracking,
│                    morning todo reminder), boot receiver
├── ui/
│   ├── onboarding/  6-screen first-run flow (welcome, 4 numbered steps, then a
│   │                skippable Premium pitch)
│   ├── home/        Dashboard + manual progress logging + lootbox reveal
│   ├── habits/      Gating/tracked/antihabit sections + heatmap
│   ├── todos/       Daily non-repeating task list
│   ├── habit/        Add/edit habit form (name, kind, type), meditation timer
│   ├── apppicker/   Choose which installed apps to lock (Recommended/Most used/A-Z)
│   ├── settings/    Habits, apps, theme, rewards, bedtime, reminders, permissions
│   ├── paywall/     The Premium pitch + purchase flow, reused by onboarding
│   ├── block/       The full-screen lock cover (habit checklist or bedtime curfew)
│   ├── navigation/  NavHost, routes, shared bottom nav bar
│   └── theme/       Warm, friendly Material 3 theme (6 variants, 2 free/4 lootbox-unlockable)
└── util/            Date handling, installed-app listing + usage stats, permission
                     helpers, curated "recommended apps" list

backend/             Node/Express service -- photo verification (proxies to Claude
                     with a server-held key), accountability-buddy pairing/sync,
                     and Google Play purchase verification. See its own README.
```

**Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, WorkManager,
Navigation Compose, Play Billing, single-activity architecture on the client;
Node/Express + SQLite on the backend.

## Design

Warm and friendly rather than brutalist: flat concrete/paper neutrals, a generous
rounded corner scale (one shared `Shapes` token updates every card/button/field/dialog
app-wide), heavy display type, monospace numeric/label readouts, outlined rather than
shadowed cards. Every Material tonal role -- including the surface-container ladder
nav bars, sheets, dialogs and snackbars pull from -- is wired to that same
concrete/paper scale, so the whole app reads as one consistent material rather than a
themed layer over an unthemed one. `LockeCat` (`ui/components/LockeCat.kt`) is a small
flat-vector mascot, not an imported image, that shows up at a handful of meaningful
moments -- content on Home once nothing's owed, curious on first run and on an empty
habit list, excited on the lootbox, asleep only for the bedtime curfew -- rather than
as wallpaper. Six accent variants share that same shape/type language and only swap
color -- `Moss` (default), `Modern`, `Rust`, `Concrete`, `Ink`, `Receipt` -- with `Moss`
and `Modern` free and the rest won from the lootbox, or unlocked instantly with a theme
code entered in Settings → *Theme* (`domain/model/ThemeRedeemCode.kt`).

## Permissions and why

| Permission | Why |
|---|---|
| Usage Access (`PACKAGE_USAGE_STATS`) | Reads today's per-app foreground time for "use an app" habits, the app picker's "Most used" sort, and screen-time-based "Recommended" apps (onboarding asks for this first, so the app-picking step can use it). |
| Accessibility Service | Detects when you switch into a locked app so the cover can appear immediately. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Lets the lock screen actually cover the app underneath. |
| Notifications | Habit reminders, streak nudges, and the morning todo reminder (optional, toggleable in Settings). |
| Camera | Required for *photo verification* -- capture is camera-only, with no gallery-picker fallback, so a proof photo can't be swapped for an old one. |
| Internet | Talks to Locke's own backend (`backend/`) -- photo verification, accountability buddies, and Google Play purchase verification. |

All three special permissions are requested with plain-language explanations
during onboarding and can be revisited any time from Settings.

## Premium & the backend

Everything that used to either call a third party straight from the device (the
Anthropic API key for photo verification) or need a per-user-configured server (the
accountability-buddy scaffolding) now goes through Locke's own backend (`backend/`,
see its own README for running/deploying it) instead. There's no login -- each
install registers itself once and authenticates as that one device
(`data/remote/DeviceIdentityRepository.kt`).

**Free vs. Premium** -- deliberately generous: the free tier is meant to be a
complete, usable app on its own, not a crippled trial, so every limit below is sized
to cover normal day-to-day use and only actually bite someone pushing well past it.
Constants live in `PreferencesRepository`, enforced in
`ui/habit/AddEditHabitViewModel.kt` and `data/verification/BackendImageVerificationClient.kt`
client-side (for instant feedback) and mirrored server-side in `backend/src/routes/`
(the actual enforcement, defense in depth against a modified client):
- **Gating habits**: up to `MAX_FREE_GATING_HABITS` (5) at once for free -- most
  people run 2-4, and onboarding itself only ever starts one to three.
- **AI photo checking** (photo-verification habits + the morning check-in):
  `FREE_VERIFICATIONS_PER_MONTH` (3) free checks *every* month, not a one-time
  trial -- resets on the 1st, so the feature stays usable indefinitely on the free
  tier, just capped. Only a check that actually gets a verdict back (approved or
  rejected) spends one; a network/API failure doesn't.
- **Accountability buddies**: `MAX_FREE_BUDDIES` (1) real buddy connection for free
  -- enough to try pairing, sharing progress, and seeing theirs, end to end. Sharing
  itself is never gated once a connection exists, free or paid.
- Premium removes all three caps.

The CTA to upgrade (`ui/paywall/`) shows up wherever a free-tier limit is actually
hit (the 6th gating habit, the 4th photo check this month, a 2nd buddy), from
Settings' own "Premium" section at the top (which also shows the free tier's live
usage -- "2 of 3 checks left this month"), and once at the end of onboarding
(skippable -- the free tier is a complete app on its own).

**Local development:** point the app at a locally running backend
(`cd backend && npm install && npm start`) with a Gradle property:
```bash
./gradlew assembleDebug -PBACKEND_BASE_URL=http://10.0.2.2:8787/v1
```
(`10.0.2.2` is the Android emulator's alias for the host machine's `localhost`; use
your machine's LAN IP instead for a physical test device.) Without this, the app
points at a deliberately unresolvable placeholder host
(`app/build.gradle.kts`'s `backendBaseUrl`) -- every backend call fails cleanly with
"can't reach the backend" rather than silently hitting a hardcoded host nobody owns.

**Google Play Billing:** `data/billing/SubscriptionProducts.kt`'s three IDs
(`locke_premium_monthly`, `locke_premium_annual`, `locke_premium_lifetime`) must be
created in Play Console -> Monetize -> Products (two subscriptions, one in-app
product) with matching IDs, and the same three IDs are hardcoded in
`backend/src/routes/billing.js`'s `PRODUCT_TIERS` map -- keep all three in sync.
Testing purchases end-to-end requires a signed build installed via a Play Console
internal testing track (Play Billing refuses to return real products for a
debug-signed, sideloaded APK) with a license tester account.

## Building

Requires Android Studio (Koala or newer) or the command line with an Android SDK
installed (`compileSdk 35`, JDK 17). This repository ships its own Gradle wrapper:

```bash
./gradlew assembleDebug
```

### CI

`.github/workflows/android-build.yml` builds a **debug APK** on every push to `main`
or a `claude/**` branch, and on every pull request into `main`. Grab the result
from the run's **Artifacts** section (`locke-debug-apk`) -- no signing
config needed since it's a debug build. It's also the source of truth for build
health -- see the note in [`CONTRIBUTING`-style guidance below](#release-signing)
if you're setting this up somewhere that can't reach `dl.google.com`.

### Release signing

`assembleRelease` produces a signed, minified release APK, both locally and in CI,
once a signing key is set up:

1. Generate a keystore (or use one you already have):
   ```bash
   keytool -genkeypair -v -keystore locke-release.jks -alias locke \
     -keyalg RSA -keysize 2048 -validity 10950
   ```
2. **Local builds:** copy `keystore.properties.example` to `keystore.properties`
   (git-ignored) at the repo root and fill in the real `storeFile`/passwords/alias.
3. **CI:** add four repository secrets under Settings → Secrets and variables →
   Actions:
   - `RELEASE_KEYSTORE_BASE64` -- the keystore file, base64-encoded
     (`base64 -w0 locke-release.jks`)
   - `RELEASE_KEYSTORE_PASSWORD`
   - `RELEASE_KEY_ALIAS`
   - `RELEASE_KEY_PASSWORD`

   The workflow skips the release build/upload entirely (debug still runs) until
   these are set. Once they are, every run also uploads a
   `locke-release-apk` artifact.

**Keep the keystore and its passwords somewhere safe outside this repo** (a
password manager, a secrets vault) -- losing it means you can never publish an
update to the same Play Store listing again, and it must never be committed
(`.gitignore` already excludes `*.jks`, `*.keystore`, and `keystore.properties`).

## Known follow-ups

- The app was renamed from its pre-Locke working name (`com.habitsfirst.androidclone`)
  to `com.locke.app` -- `applicationId`, every Kotlin package/class, the Room database
  file (`locke.db`), and the DataStore preferences file (`locke_prefs`) all moved
  together. Since `applicationId` changed, this is a new app as far as Android is
  concerned: a device with a debug build installed from before this rename needs that
  old copy uninstalled first (`adb uninstall com.habitsfirst.androidclone.debug`) --
  installing the new one alongside or over it isn't possible, and there's no data
  migration between the two since none was ever published.
- Room's `AppDatabase` now has a real `Migration` for every version step 1 through
  9 (see `data/local/migrations/Migrations.kt`), wired in by `di/AppModule.kt`
  instead of `fallbackToDestructiveMigration()`, so an update no longer risks
  silently wiping local habit/todo history. There's deliberately no destructive
  fallback beyond v9 -- adding a new version without its own `Migration` now
  crashes loudly in development instead of silently dropping data, so bump the
  version and add its migration together the same way each existing step was.
- Uninstall/bypass friction was deliberately left out of this pass -- a natural
  next addition alongside the penalty engine and bedtime lock already in place.
- Home-screen habit reminder notifications beyond the morning todo reminder (the
  notification channel is created; scheduling per-habit reminders isn't wired up).
- The morning todo reminder and lootbox-reveal notification icon both reuse the
  launcher's adaptive-icon foreground drawable rather than a dedicated flat
  notification icon -- functional, but not to Android's notification-icon
  guidelines.
- Per-OEM launcher detection for the accessibility service is best-effort (it
  resolves the system's default `ACTION_MAIN`/`CATEGORY_HOME` handler at runtime
  rather than hardcoding a launcher package list, but heavily customized OEM
  launchers can still vary).
- No account system beyond per-device backend identity (`backend/`'s
  `POST /v1/devices`) -- premium entitlement and buddy pairings are tied to one
  device, not a person. Restoring a purchase onto a new device today means
  re-verifying that device's own Play Billing purchase (Play ties a purchase to
  the signed-in Google account across devices, so this works, but there's no
  cross-device buddy-list carryover). A real account system (Google Sign-In tied
  to the backend's device rows) is the natural next step if that friction matters.
- `backend/`'s SQLite storage and device-token-in-DataStore auth are sized for this
  app's likely scale, not high-traffic production load -- see that README's own
  "Known follow-ups" for the specific swaps (a hosted SQL database, pairing-code
  rate limiting, structured logging) worth making before that matters.

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

1. **Onboarding** -- pick which installed apps to lock, pick a few starter habits
   (steps, exercise, meditation, or a plain custom check-in), and grant three
   special permissions. Picking 2+ habits also asks you to rank them easiest-first
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
   today's gating habits are done, no active penalty lock is running, and (unless
   it's bedtime, which no token bypasses) you haven't redeemed a grace token.
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
9. **Daily lootbox** -- awarded once per day, the moment every gating habit is
   done. Weighted reward pool: a grace-period token (common, 1-minute unblock,
   redeemed from a lock screen, never during bedtime), a cosmetic gold star on
   today's heatmap cell (uncommon), a new theme accent unlock (uncommon), or a
   task-skip token (rare -- force-completes one gating habit for the day, redeemed
   from Settings).
10. **Todos** -- plain non-repeating one-off tasks for today, added and checked off
    inline on Home (there's no separate Todos tab). A periodic worker posts a
    reminder to fill them in once it's near your configured morning time (a
    ~15-minute-cadence check, not an exact alarm).
11. **Morning check-in** (Settings, optional) -- a daily proof-of-life photo, due by a
    configured time plus a grace window. Miss it and `PenaltyRepository` extends the
    block lock, same as an antihabit slip. It's a thin wrapper around the same
    capture/verify flow as photo verification below, not tied to any habit.
12. Progress resets automatically at midnight because completion is stored keyed by
    calendar date, not as a flag that has to be cleared.

### Photo verification

A *Photo* habit is gated on a proof photo instead of a manual honor-system toggle --
when setting it up you write what a proof photo should show (e.g. "a made bed"),
attach an example photo, or both. To
complete it for the day, tap the habit, take a photo with the camera, and submit
it — capture is camera-only by design, so a stored gallery photo can't stand in for
today's proof. `AnthropicImageVerificationClient` sends it
(downscaled, alongside the example photo if any) to the Claude Messages API and
marks the habit done only if the model approves, showing its one-sentence reasoning
either way. It calls the API directly from the device using an Anthropic API key
you provide yourself in Settings → *Photo verification*; nothing is sent anywhere
until a key is set. Photos are stored locally under the app's own storage
(`data/verification/`, `util/ImageStore.kt`) and never leave the device except as
part of that one verification request.

The morning check-in above shares this exact capture/verify UI
(`ui/components/PhotoVerificationCapture.kt`) and the same `ImageVerificationClient`
-- its own screen (`ui/proofoflife/`) just supplies a fixed prompt instead of a
habit's own rules, and confirms `ProofOfLifeRepository` instead of a habit on
approval. Its photos aren't kept once verified (`ImageStore.saveToCache`), since
there's nothing to show again later -- just today's yes/no.

## Project structure

```
app/src/main/java/com/habitsfirst/androidclone/
├── data/            Room entities/DAOs, repositories (habits, blocked apps,
│                    penalties, bedtime, lootbox, todos), DataStore preferences,
│                    verification/ (vision-model client for photo verification)
├── domain/model/    Plain Kotlin models (Habit, HabitKind, HabitType, BlockedApp,
│                    ThemeVariant, LootboxReward, Todo, ...)
├── di/              Hilt modules
├── service/         AccessibilityService, WorkManager workers (usage tracking,
│                    morning todo reminder), boot receiver
├── ui/
│   ├── onboarding/  4-step first-run flow
│   ├── home/        Dashboard + manual progress logging + lootbox reveal
│   ├── habits/      Gating/tracked/antihabit sections + heatmap
│   ├── todos/       Daily non-repeating task list
│   ├── habit/        Add/edit habit form (name, kind, type), meditation timer
│   ├── apppicker/   Choose which installed apps to lock (Recommended/Most used/A-Z)
│   ├── settings/    Habits, apps, theme, rewards, bedtime, reminders, permissions
│   ├── block/       The full-screen lock cover (habit checklist or bedtime curfew)
│   ├── navigation/  NavHost, routes, shared bottom nav bar
│   └── theme/       Softened eco-brutalist Material 3 theme (6 variants, 2 free/4 lootbox-unlockable)
└── util/            Date handling, installed-app listing + usage stats, permission
                     helpers, curated "recommended apps" list
```

**Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, WorkManager,
Navigation Compose, single-activity architecture.

## Design

Softened eco-brutalism: flat concrete/paper neutrals, a deliberate moderate corner
scale rather than fully rounded "pill" Material You shapes or the old flat 0dp knife
edges (one shared `Shapes` token updates every card/button/field/dialog app-wide),
heavy stamped display type, monospace numeric/label readouts, outlined rather than
shadowed cards. Every Material tonal role -- including the surface-container ladder
nav bars, sheets, dialogs and snackbars pull from -- is wired to that same
concrete/paper scale, so the whole app reads as one consistent material rather than a
themed layer over an unthemed one. Six accent variants share that same shape/type
language and only swap color -- `Moss` (default), `Modern`, `Rust`, `Concrete`, `Ink`,
`Receipt` -- with `Moss` and `Modern` free and the rest won from the lootbox, or
unlocked instantly with a theme code entered in Settings → *Theme*
(`domain/model/ThemeRedeemCode.kt`).

## Permissions and why

| Permission | Why |
|---|---|
| Usage Access (`PACKAGE_USAGE_STATS`) | Reads today's per-app foreground time for "use an app" habits and the app picker's "Most used" sort. |
| Accessibility Service | Detects when you switch into a locked app so the cover can appear immediately. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Lets the lock screen actually cover the app underneath. |
| Notifications | Habit reminders, streak nudges, and the morning todo reminder (optional, toggleable in Settings). |
| Camera | Required for *photo verification* -- capture is camera-only, with no gallery-picker fallback, so a proof photo can't be swapped for an old one. |
| Internet | Sends a submitted proof photo to the Claude API for *photo verification* habits — only used if you've set an API key in Settings, and only for that request. |

All three special permissions are requested with plain-language explanations
during onboarding and can be revisited any time from Settings.

## Building

Requires Android Studio (Koala or newer) or the command line with an Android SDK
installed (`compileSdk 35`, JDK 17). This repository ships its own Gradle wrapper:

```bash
./gradlew assembleDebug
```

### CI

`.github/workflows/android-build.yml` builds a **debug APK** on every push to `main`
or a `claude/**` branch, and on every pull request into `main`. Grab the result
from the run's **Artifacts** section (`habits-first-debug-apk`) -- no signing
config needed since it's a debug build. It's also the source of truth for build
health -- see the note in [`CONTRIBUTING`-style guidance below](#release-signing)
if you're setting this up somewhere that can't reach `dl.google.com`.

### Release signing

`assembleRelease` produces a signed, minified release APK, both locally and in CI,
once a signing key is set up:

1. Generate a keystore (or use one you already have):
   ```bash
   keytool -genkeypair -v -keystore habitsfirst-release.jks -alias habitsfirst \
     -keyalg RSA -keysize 2048 -validity 10950
   ```
2. **Local builds:** copy `keystore.properties.example` to `keystore.properties`
   (git-ignored) at the repo root and fill in the real `storeFile`/passwords/alias.
3. **CI:** add four repository secrets under Settings → Secrets and variables →
   Actions:
   - `RELEASE_KEYSTORE_BASE64` -- the keystore file, base64-encoded
     (`base64 -w0 habitsfirst-release.jks`)
   - `RELEASE_KEYSTORE_PASSWORD`
   - `RELEASE_KEY_ALIAS`
   - `RELEASE_KEY_PASSWORD`

   The workflow skips the release build/upload entirely (debug still runs) until
   these are set. Once they are, every run also uploads a
   `habits-first-release-apk` artifact.

**Keep the keystore and its passwords somewhere safe outside this repo** (a
password manager, a secrets vault) -- losing it means you can never publish an
update to the same Play Store listing again, and it must never be committed
(`.gitignore` already excludes `*.jks`, `*.keystore`, and `keystore.properties`).

## Known follow-ups

- The Kotlin package (`com.habitsfirst.androidclone`) still reflects the app's
  original working name from before the rebrand to Locke -- cosmetic only
  (`applicationId`, class names, DB/DataStore file names), but worth a deliberate
  rename pass before a Play Store listing, since `applicationId` becomes
  effectively permanent once published.
- Room's schema bump to v4 relies on `fallbackToDestructiveMigration()` (see
  `di/AppModule.kt`) rather than a real `Migration` -- fine pre-release, but must
  be replaced before this ships with real user data on device, or an update will
  silently wipe local habit/todo history.
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

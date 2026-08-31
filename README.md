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
   special permissions.
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
   completable inline so there's no reason to leave it. `Habits` is read-only
   progress and management: the heatmap plus every habit grouped by kind, each
   card colored by kind and tapped to edit (never to complete) -- one consistent
   tap behavior instead of a different action per habit type.
5. **Habit types** (independent of kind, above):
   - *Walk N steps* / *Exercise N minutes* -- logged manually from Home (a
     Health Connect-ready target package is already declared, so real step/workout
     sync is a small follow-up rather than a rearchitecture).
   - *Meditate N minutes* -- a built-in countdown timer.
   - *Use an app for N minutes* (the "use Duolingo" style habit) -- tracked
     automatically via `UsageStatsManager`, refreshed every 15 minutes and again
     immediately after you leave the tracked app.
   - *Custom check-in* -- a plain manual toggle.
6. **Heatmap** -- a GitHub-style contribution grid on the Habits tab: a Canvas-drawn
   grid shaded by the fraction of gating habits completed each day (5 buckets, like
   GitHub's), with a cosmetic gold-star overlay on days a lootbox awarded one. A day
   marked broken by a penalty reads as failed even if habits were later completed.
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
11. Progress resets automatically at midnight because completion is stored keyed by
    calendar date, not as a flag that has to be cleared.

## Project structure

```
app/src/main/java/com/habitsfirst/androidclone/
├── data/            Room entities/DAOs, repositories (habits, blocked apps,
│                    penalties, bedtime, lootbox, todos), DataStore preferences
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
│   └── theme/       Eco-brutalist Material 3 theme (4 lootbox-unlockable variants)
└── util/            Date handling, installed-app listing + usage stats, permission
                     helpers, curated "recommended apps" list
```

**Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, WorkManager,
Navigation Compose, single-activity architecture.

## Design

Eco-brutalist: flat concrete/paper neutrals, sharp 0dp corners everywhere (one shared
`Shapes` token flattens every card/button/field/dialog app-wide), heavy stamped
display type, monospace numeric/label readouts, outlined rather than shadowed cards.
Four accent variants share that same shape/type language and only swap color --
`Moss` (default), `Rust`, `Concrete`, `Ink` -- with everything but Moss won from the
lootbox.

## Permissions and why

| Permission | Why |
|---|---|
| Usage Access (`PACKAGE_USAGE_STATS`) | Reads today's per-app foreground time for "use an app" habits and the app picker's "Most used" sort. |
| Accessibility Service | Detects when you switch into a locked app so the cover can appear immediately. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Lets the lock screen actually cover the app underneath. |
| Notifications | Habit reminders, streak nudges, and the morning todo reminder (optional, toggleable in Settings). |

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

- Wire real Health Connect reads for the steps/exercise habit types (the
  dependency and manifest permissions are already in place; `HabitRepository`
  already stores an absolute daily value, so a sync worker just needs to call
  `setProgress` the same way `UsageTrackingWorker` does).
- The Kotlin package (`com.habitsfirst.androidclone`) still reflects the app's
  original working name from before the rebrand to Locke -- cosmetic only
  (`applicationId`, class names, DB/DataStore file names), but worth a deliberate
  rename pass before a Play Store listing, since `applicationId` becomes
  effectively permanent once published.
- Room's schema bump to v2 relies on `fallbackToDestructiveMigration()` (see
  `di/AppModule.kt`) rather than a real `Migration` -- fine pre-release, but must
  be replaced before this ships with real user data on device, or an update will
  silently wipe local habit/todo history.
- Wake-up proof-of-life check-in (a mandatory task at your wake time, with a
  penalty for missing it) and uninstall/bypass friction were deliberately left out
  of this pass -- both are natural next additions to the penalty engine and the
  bedtime lock already in place.
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

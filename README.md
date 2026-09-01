# Habits First (Android)

An independent Android implementation of the [habitsfirst.com](https://habitsfirst.com)
concept: **do your habits, then use your apps**. You pick the apps that eat your
time and the habits that matter more; the app locks those apps behind a full-screen
checklist until every habit for the day is done, then unlocks them automatically.

This is not affiliated with, endorsed by, or built from the source of the original
iOS app — it's a from-scratch Android app that reproduces the same idea using
Android's own APIs (Accessibility Service + `UsageStatsManager`, since Android has
no equivalent of iOS's Screen Time / Shortcuts APIs the original relies on).

## How it works

1. **Onboarding** — pick which installed apps to lock, pick a few starter habits
   (steps, exercise, meditation, or a plain custom check-in), and grant three
   special permissions.
2. **Home** — shows today's habits with live progress, your current streak, and
   how many apps are locked right now.
3. **The lock** — an `AccessibilityService` watches for foreground-app changes.
   The instant you switch into a locked app while habits are unfinished, it covers
   the screen with a checklist of what's left (`BlockOverlayActivity`). Finish your
   habits and the cover stops appearing — Android has no public API to prevent an
   app from launching outright, so this "detect, then cover" technique is what
   every Play Store app-blocker uses.
4. **Habit types**
   - *Walk N steps* / *Exercise N minutes* — logged manually from Home (a
     Health Connect–ready target package is already declared, so real step/workout
     sync is a small follow-up rather than a rearchitecture).
   - *Meditate N minutes* — a built-in countdown timer.
   - *Use an app for N minutes* (the "use Duolingo" style habit) — tracked
     automatically via `UsageStatsManager`, refreshed every 15 minutes and again
     immediately after you leave the tracked app.
   - *Custom check-in* — a plain manual toggle.
   - *Photo verification* — you describe what counts as done, add an example
     photo, or both; completing it means submitting today's proof photo, which a
     Claude vision model checks against those rules before the habit counts as
     done (see below).
5. Progress resets automatically at midnight because completion is stored keyed by
   calendar date, not as a flag that has to be cleared.

### Photo verification

A *Photo verification* habit is gated by a vision model instead of an honor-system
toggle: when setting it up you write what a proof photo should show (e.g. "a made
bed"), attach an example photo, or both. To complete it for the day, tap the habit,
take or pick a photo, and submit it — `AnthropicImageVerificationClient` sends it
(downscaled, alongside the example photo if any) to the Claude Messages API and
marks the habit done only if the model approves, showing its one-sentence reasoning
either way. It calls the API directly from the device using an Anthropic API key
you provide yourself in Settings → *Photo verification*; nothing is sent anywhere
until a key is set. Photos are stored locally under the app's own storage
(`data/verification/`, `util/ImageStore.kt`) and never leave the device except as
part of that one verification request.

## Project structure

```
app/src/main/java/com/habitsfirst/androidclone/
├── data/            Room entities/DAOs, repositories, DataStore preferences,
│                    verification/ (vision-model client for photo verification)
├── domain/model/    Plain Kotlin models (Habit, HabitType, BlockedApp, ...)
├── di/              Hilt modules
├── service/         AccessibilityService, WorkManager usage-tracking worker, boot receiver
├── ui/
│   ├── onboarding/  4-step first-run flow
│   ├── home/        Dashboard + manual progress logging
│   ├── habit/        Add/edit habit form, meditation timer
│   ├── apppicker/   Choose which installed apps to lock
│   ├── settings/    Manage habits/apps/permissions
│   ├── block/       The full-screen lock cover
│   ├── navigation/  NavHost + routes
│   └── theme/       Material 3 theme
└── util/            Date handling, installed-app listing, permission helpers
```

**Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, WorkManager,
Navigation Compose, single-activity architecture.

## Permissions and why

| Permission | Why |
|---|---|
| Usage Access (`PACKAGE_USAGE_STATS`) | Reads today's per-app foreground time for "use an app" habits. |
| Accessibility Service | Detects when you switch into a locked app so the cover can appear immediately. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Lets the lock screen actually cover the app underneath. |
| Notifications | Habit reminders and streak nudges (optional, toggleable in Settings). |
| Camera | Lets you take a proof photo directly for a *photo verification* habit (picking one from your gallery works too, without this). |
| Internet | Sends a submitted proof photo to the Claude API for *photo verification* habits — only used if you've set an API key in Settings, and only for that request. |

All three special permissions are requested with plain-language explanations
during onboarding and can be revisited any time from Settings.

## Building

Requires Android Studio (Koala or newer) or the command line with an Android SDK
installed (`compileSdk 35`, JDK 17). This repository ships its own Gradle wrapper:

```bash
./gradlew assembleDebug
```

> **Note on this repo's build environment:** the sandbox this project was originally
> authored in blocks network access to Google's Maven repository (`dl.google.com`),
> so the build couldn't be run end-to-end there — CI (see below) is what actually
> compiles it and is the source of truth for build health.

### CI

`.github/workflows/android-build.yml` builds a **debug APK** on every push to `main`
or a `claude/**` branch, and on every pull request into `main`. Grab the result
from the run's **Artifacts** section (`habits-first-debug-apk`) — no signing
config needed since it's a debug build.

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
   - `RELEASE_KEYSTORE_BASE64` — the keystore file, base64-encoded
     (`base64 -w0 habitsfirst-release.jks`)
   - `RELEASE_KEYSTORE_PASSWORD`
   - `RELEASE_KEY_ALIAS`
   - `RELEASE_KEY_PASSWORD`

   The workflow skips the release build/upload entirely (debug still runs) until
   these are set. Once they are, every run also uploads a
   `habits-first-release-apk` artifact.

**Keep the keystore and its passwords somewhere safe outside this repo** (a
password manager, a secrets vault) — losing it means you can never publish an
update to the same Play Store listing again, and it must never be committed
(`.gitignore` already excludes `*.jks`, `*.keystore`, and `keystore.properties`).

## Known follow-ups

- Wire real Health Connect reads for the steps/exercise habit types (the
  dependency and manifest permissions are already in place; `HabitRepository`
  already stores an absolute daily value, so a sync worker just needs to call
  `setProgress` the same way `UsageTrackingWorker` does).
- Home-screen habit reminder notifications (the notification channel is created;
  scheduling them is not yet wired up).
- Per-OEM launcher detection for the accessibility service is best-effort (it
  resolves the system's default `ACTION_MAIN`/`CATEGORY_HOME` handler at runtime
  rather than hardcoding a launcher package list, but heavily customized OEM
  launchers can still vary).

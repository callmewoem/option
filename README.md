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
5. Progress resets automatically at midnight because completion is stored keyed by
   calendar date, not as a flag that has to be cleared.

## Project structure

```
app/src/main/java/com/habitsfirst/androidclone/
├── data/            Room entities/DAOs, repositories, DataStore preferences
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

All three special permissions are requested with plain-language explanations
during onboarding and can be revisited any time from Settings.

## Building

Requires Android Studio (Koala or newer) or the command line with an Android SDK
installed (`compileSdk 35`, JDK 17). This repository ships its own Gradle wrapper:

```bash
./gradlew assembleDebug
```

> **Note on this repo's build environment:** the sandbox this project was authored
> in blocks network access to Google's Maven repository (`dl.google.com`), which is
> where the Android Gradle Plugin and all AndroidX/Compose/Room/Hilt artifacts are
> hosted — so the build could not be run end-to-end here. The Gradle wrapper,
> version catalog, and every source file were written and reviewed by hand against
> the documented APIs; running `./gradlew assembleDebug` on a normal machine with
> access to `dl.google.com` and `repo.maven.apache.org` is the first thing to do
> after cloning.

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

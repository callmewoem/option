# Locke — Project Summary Report

_Generated 2026-09-04_

## 1. What this project is

**Locke** is an independent Android app that reimplements the
[habitsfirst.com](https://habitsfirst.com) concept: *do your habits, then use your
apps*. The user picks which installed apps eat their time and which habits matter
more; Locke locks the chosen apps behind a full-screen checklist until every
gating habit for the day is complete, then unlocks them automatically. Layered on
top of that core loop: optional antihabits (habits where *not* doing something is
the win), a hard bedtime curfew with no bypass, a daily weighted-reward lootbox,
and a simple non-repeating todo list.

It is a from-scratch build (not derived from the original iOS app's source) that
reproduces the idea using Android-native APIs — an `AccessibilityService` plus
`UsageStatsManager` — since Android has no equivalent of iOS's Screen Time /
Shortcuts APIs.

Repository: `callmewoem/option` · Package: `com.habitsfirst.androidclone` ·
App name: **Locke** · Version: `1.0.0` (versionCode 1)

## 2. Core feature set

| Area | Summary |
|---|---|
| **Onboarding** | 4-step first-run flow: Usage Access → app picking (screen-time-ranked or curated fallback) → starter habit selection (with an "ease into it" ramp when 2+ habits are picked) → remaining permission grants. |
| **Home** | Dashboard of today's gating habits with live progress, current streak, and count of currently-locked apps; inline logging for every habit kind and today's todos. |
| **The lock** | `AppBlockAccessibilityService` watches foreground-app switches and covers a not-yet-unlocked app with `BlockOverlayActivity` — the same "detect, then cover" technique every Play Store blocker uses, since Android has no API to prevent a launch outright. |
| **Habit kinds** | *Gating* (blocks apps until done), *Tracked* (heatmap-only, never blocks), *Antihabit* (a logged day is a slip, penalized; silence is success). |
| **Habit types** | Timed (stopwatch), "use an app for N minutes" (auto-tracked via `UsageStatsManager`), Photo (vision-model-verified proof), Tally (manual check-in), Walk N steps (Health Connect sync). |
| **Stats** | GitHub-style Canvas heatmap (5-bucket shading, gold-star overlay for lootbox days), completion rate per habit, and average completion rate by day of week. |
| **Penalties** | Small reusable engine (`PenaltyRepository`): extend lock by N minutes, add a makeup habit, or mark a day's streak broken. Currently triggered by antihabit slips and missed morning check-ins. |
| **Bedtime lock** | Optional hard curfew window (wraps midnight) with no grace-token bypass, unlike every other lock condition. |
| **Limited unblocking** | Optional: shortens the post-completion unlock window to one hour instead of the rest of the day. |
| **Daily lootbox** | One weighted draw per day on habit completion: grace token (common), gold heatmap star (uncommon), new theme unlock (uncommon), task-skip token (rare). |
| **Todos** | Inline, non-repeating daily tasks on Home; a WorkManager-driven reminder nudges near a configured morning time. |
| **Morning check-in** | Optional daily proof-of-life photo due by a configured time + grace window, sharing the photo-verification pipeline; missing it triggers a penalty. |
| **URL/domain blocking** | Blocklists (`data/local/entity/BlockedDomainEntity.kt`, `data/repository/UrlBlockRepository.kt`) with premade lists (`util/PremadeBlocklists.kt`, assets `blocklists/social.txt`, `blocklists/porn.txt`) fetchable/refreshable via a background worker, plus whitelist/blacklist modes. |
| **Theming** | 6 accent variants (Moss, Modern, Rust, Concrete, Ink, Receipt) sharing one shape/type language; 2 free, 4 unlockable via lootbox or a redeem code. |

### Photo verification

A *Photo* habit (and the morning check-in) is completed by taking a camera-only
photo (no gallery picker, so an old photo can't stand in for today's proof).
`AnthropicImageVerificationClient` sends the photo — downscaled, with an optional
reference example photo — directly from the device to the **Claude Messages API**,
using an API key the user supplies themselves in Settings. The habit is marked
done only if the model approves, and its one-sentence reasoning is shown either
way. Nothing is sent anywhere until a key is configured; photos otherwise stay
local under the app's own storage.

## 3. Architecture & project layout

Single-activity Jetpack Compose app, MVVM, Hilt-based DI.

```
app/src/main/java/com/habitsfirst/androidclone/
├── data/            Room entities/DAOs, repositories, DataStore preferences,
│                    verification/ (vision-model client), billing/, healthconnect/
├── domain/model/    Plain Kotlin models (Habit, HabitKind, HabitType, BlockedApp, ...)
├── di/              Hilt modules (AppModule, VerificationModule, BillingModule)
├── service/         AccessibilityService, WorkManager workers, boot receiver
├── ui/
│   ├── onboarding/  5-screen first-run flow
│   ├── home/        Dashboard, manual progress logging, lootbox reveal
│   ├── habits/      Gating/tracked/antihabit sections + heatmap
│   ├── habit/       Add/edit form, timers, photo verification screen
│   ├── apppicker/   Choose apps to lock
│   ├── urlblock/    Domain/URL blocklist management
│   ├── settings/    Habits, apps, theme, rewards, bedtime, reminders, permissions
│   ├── block/       Full-screen lock cover
│   ├── proofoflife/ Morning check-in screen
│   ├── diagnostics/ In-app diagnostics screen
│   ├── navigation/  NavHost, routes, bottom nav bar
│   └── theme/       Material 3 theme (6 accent variants)
└── util/            Date handling, installed-app/usage-stats helpers, permission
                     helpers, curated recommended-apps list, blocklist fetcher
```

**Size:** 115 Kotlin source files across 25 packages, ~1,600+ lines counted in a
representative sample (full tree is larger; see `app/src/main/java`).

## 4. Tools & technology stack

| Category | Tool / Library | Version |
|---|---|---|
| Language | Kotlin | 2.0.21 |
| UI toolkit | Jetpack Compose (Material 3) | BOM 2024.12.01 |
| Build system | Gradle (wrapper) | 8.9 |
| Android Gradle Plugin | AGP | 8.5.2 |
| Annotation processing | KSP | 2.0.21-1.0.28 |
| DI | Hilt (Dagger) | 2.52 |
| Persistence | Room | 2.6.1 |
| Preferences | DataStore (Preferences) | 1.1.1 |
| Background work | WorkManager | 2.10.0 |
| Navigation | Navigation Compose | 2.8.5 |
| Concurrency | Kotlin Coroutines | 1.9.0 |
| Health data | Health Connect client | 1.1.0-alpha07 |
| Permissions UX | Accompanist Permissions | 0.34.0 |
| Image loading | Coil Compose | 2.7.0 |
| Networking | OkHttp | 4.12.0 |
| External API | Anthropic (Claude) Messages API — photo verification | — |
| Testing | JUnit 4, AndroidX Test/Espresso, Compose UI test | 4.13.2 / 1.2.1 / 3.6.1 |
| Target platform | `compileSdk`/`targetSdk` 35, `minSdk` 26, JDK 17 | — |
| CI | GitHub Actions (`.github/workflows/android-build.yml`) | — |

CI builds a debug APK on every push to `main` or a `claude/**` branch and on every
PR into `main`; it additionally builds and uploads a signed release APK once four
repository secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) are configured — otherwise that step
is skipped, not failed.

## 5. Permissions

| Permission | Purpose |
|---|---|
| Usage Access (`PACKAGE_USAGE_STATS`) | Per-app foreground time for "use an app" habits, app-picker sorting, screen-time-based recommendations. |
| Accessibility Service | Detects switches into a locked app so the cover appears immediately. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Lets the lock screen cover the app underneath. |
| Notifications | Habit reminders, streak nudges, morning todo reminder (optional). |
| Camera | Photo-verification habits — capture-only, no gallery fallback. |
| Internet | Sends a submitted proof photo to the Claude API — only when an API key is set. |
| `QUERY_ALL_PACKAGES` | Lists installed apps for the app picker. |
| Health Connect (`READ_STEPS`, `READ_EXERCISE`, `READ_SLEEP`) | Syncs step-based habits. |
| `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE*` | Restores blocking/workers after reboot. |

All special permissions are requested with plain-language rationale during
onboarding and can be revisited from Settings.

## 6. Design language

Warm/friendly rather than brutalist: flat concrete/paper neutrals, a single shared
rounded-corner `Shapes` token applied app-wide, heavy display type, monospace
numeric readouts, outlined (not shadowed) cards. `LockeCat`
(`ui/components/LockeCat.kt`) is a small flat-vector mascot that appears at a
handful of meaningful moments (content on a clear Home, curious on first run,
excited on the lootbox, asleep during bedtime curfew) rather than as decoration.

## 7. Recent development activity (last 10 commits)

```
68705f3 Split onboarding into 4 steps; base app recommendations on screen time (#23)
dfe8ccf Merge pull request #22 from callmewoem/claude/limited-unblock-option-ty0xg7
e534bd7 Add limited unblocking option for blocked apps
aad20ad Merge pull request #21 from callmewoem/claude/locke-ui-ux-redesign-et673c
b09c0e9 Merge pull request #20 from callmewoem/claude/whitelist-blacklist-options-56x2y9
9aa748b Replace the receipt/ledger motif with a warmer companion aesthetic
d8fc25a Fix CI: remove invalid matchParentSize import
8f1938e Add a distinctive ledger/receipt visual language across the app
847bb95 Redesign UI/UX: softened brutalism, full tonal system, bolder hero moments
3cd6fc8 Add a whitelist mode for app blocking, alongside the existing blacklist
```

## 8. Known follow-ups (from the project's own README)

- `applicationId`/package name (`com.habitsfirst.androidclone`) still reflects a
  pre-rebrand working name — cosmetic, but worth fixing before a Play Store
  listing since `applicationId` becomes effectively permanent once published.
- Room's v4 schema bump uses `fallbackToDestructiveMigration()` rather than a real
  `Migration` — must be replaced before shipping with real user data, or an
  update will silently wipe local history.
- No uninstall/bypass friction yet (a natural next addition alongside the
  penalty engine and bedtime lock).
- Per-habit reminder notifications beyond the morning todo reminder aren't wired
  up (channel exists, scheduling doesn't).
- Notification icons for the morning reminder and lootbox reveal reuse the
  launcher's adaptive-icon foreground rather than a dedicated flat icon.
- Per-OEM launcher detection for the accessibility service is best-effort
  (resolved at runtime via the system's default `ACTION_MAIN`/`CATEGORY_HOME`
  handler, not a hardcoded package list).

## 9. Building

```bash
./gradlew assembleDebug
```

Requires Android Studio (Koala+) or SDK + JDK 17 on the command line. Release
builds (`assembleRelease`) need a signing keystore configured either locally via
`keystore.properties` (see `keystore.properties.example`) or via the four CI
secrets listed above.

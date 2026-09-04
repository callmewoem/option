# Locke for iOS — Port Plan

This is a planning document, not code. It maps the existing Android app
(`app/src/main/java/com/habitsfirst/androidclone/`) feature-by-feature onto iOS
frameworks, calls out where iOS's sandboxing makes a 1:1 port impossible, and lays
out a phased build order. No Xcode project exists yet — this plan is meant to be
the spec for creating one.

## Why this isn't a straight port

Android gives Locke two very permissive tools: an `AccessibilityService` that sees
every foreground-app switch system-wide, and `UsageStatsManager`, which hands back
exact per-app usage minutes on request. iOS has no equivalent of either for a
regular App Store app. The nearest sanctioned mechanism is the **Screen Time
API** family (`FamilyControls`, `ManagedSettings`, `DeviceActivity`), and it works
on fundamentally different terms:

- **Apps are opaque tokens, not identifiable packages.** You never get a bundle ID,
  name, or icon for an app the user picks — only an `ApplicationToken` you can
  render (Apple supplies a `Label(token)` view) but not inspect. There is no
  "list every installed app" API at all (privacy — Apple removed it years ago).
  Selection happens entirely through Apple's own `FamilyActivityPicker` UI, which
  you can't restyle or reorder ("Most used" sort, "Recommended" curation — both
  gone; see [Onboarding / app picker](#onboarding--app-picker) below).
- **No live usage numbers reach your code.** `DeviceActivityReport` renders
  Apple's own sandboxed SwiftUI view inside an extension; your process never sees
  the minutes as data. The only thing your own code can react to is a
  `DeviceActivityMonitor` extension firing at scheduled interval boundaries or
  when a registered usage **threshold** is crossed (e.g. "10 cumulative minutes
  in this app today") — coarse, event-based, not a readable running total.
- **The block screen can't be arbitrary UI.** `ManagedSettings` "shields" a
  blocked app with a system-drawn screen you configure via `ShieldConfiguration`
  (title, subtitle, icon, up to two buttons) — not a `WebView`/SwiftUI tree you
  control. Locke's full-screen habit checklist has to live in the main app or a
  widget-like surface, reached by tapping the shield's button, not drawn inside
  the shield itself. This is the same pattern every production app in this space
  uses (Opal, One Sec, Freedom) — it's a UX regression from Android's true overlay,
  not a bug in this plan, and should be prototyped first (see
  [Phase 0](#phase-0--de-risk-the-screen-time-stack-1-2-days)) since exactly how
  the button hands off to the app has shifted across iOS versions.
- **Screen Time requires an Apple-granted entitlement.** Unlike Android's
  runtime-permission dialogs, `com.apple.developer.family-controls` must be
  requested from Apple and approved before it can ship — budget lead time for
  this before planning a release date.

None of this blocks a good app — it's exactly the constraint set every iOS
screen-time blocker ships inside — but it reshapes several Android features below
rather than translating them directly.

## Stack

| Concern | Android (current) | iOS (planned) |
|---|---|---|
| UI | Jetpack Compose (Material 3) | SwiftUI |
| Local persistence | Room + DataStore | SwiftData (min iOS 17) — see note below |
| DI | Hilt | Plain `@Observable` environment objects / a tiny hand-rolled container; Swift has no ergonomic Hilt equivalent worth pulling in a DI framework for at this size |
| Background work | WorkManager | `BGTaskScheduler` (`BackgroundTasks`) for periodic app-side work, `DeviceActivityMonitor` extension for scheduled Screen-Time-side callbacks |
| App blocking | `AccessibilityService` + `SYSTEM_ALERT_WINDOW` overlay | `FamilyControls` + `ManagedSettings` shield + `ShieldConfiguration`/`ShieldAction` extensions |
| Usage stats | `UsageStatsManager` | `DeviceActivity` (`DeviceActivityMonitor` extension, threshold events) + `DeviceActivityReport` extension for any user-facing usage UI |
| Health data | Health Connect | HealthKit |
| Camera capture | CameraX (camera-only capture) | `AVFoundation` custom capture screen (camera-only, no `UIImagePickerController` gallery fallback, mirroring Android's no-gallery rule) |
| Vision-model calls | `AnthropicImageVerificationClient` via OkHttp | Same Claude Messages API, `URLSession`, Swift port of the same client |
| API key storage | DataStore (documented as user-supplied) | Keychain — strictly better than Android's current storage, worth calling out as an intentional improvement, not scope creep |
| Billing | Play Billing Library | StoreKit 2 (`Product`, `Transaction`, `Transaction.currentEntitlements`) |
| Notifications | `NotificationManager` channels | `UserNotifications` (`UNUserNotificationCenter`) |
| Navigation | Navigation Compose | `NavigationStack` + `TabView` |
| Cross-process state (extensions ↔ app) | n/a (single process) | App Group shared container (`UserDefaults(suiteName:)` / shared SQLite file) |

**Data layer note:** SwiftData needs iOS 17+. `FamilyControls`' `.individual`
authorization (the mode a non-parental, self-directed app like Locke needs) is
iOS 16+. Recommend **minimum deployment target iOS 16** and Core Data instead of
SwiftData, since losing a year of device support to save writing an
`NSManagedObjectModel` isn't worth it for a habit tracker; revisit if the target
is later raised to 17+.

## Feature-by-feature mapping

### Domain model
Ports essentially 1:1 as Swift `struct`/`enum` — `Habit`, `HabitKind` (Gating /
Tracked / Antihabit), `HabitType` (Timed / App-usage / Photo / Tally / Steps /
Workout / Sleep), `BlockedApp`, `UrlBlockList` (+ `BlockMode`, `BlockListSource`),
`Todo`, `LootboxReward`, `ThemeVariant`, `ThemeRedeemCode`, `SubscriptionTier`,
`Entitlement`. No conceptual changes needed here — this layer doesn't touch any
iOS-specific constraint.

### Onboarding / app picker
Android's picker enumerates installed apps and sorts them Recommended/Most
used/A-Z with real icons and names. iOS has no installed-app listing API, so this
step becomes: present Apple's `FamilyActivityPicker` (multi-select, supports
categories as well as individual apps — arguably a win, since "block all of
Social Media" becomes a single tap instead of picking each app). Store the result
as a `FamilyActivitySelection` (tokens), not a package-name list. Drop "Most
used" sorting (no data source for it) and "Recommended" curation (can keep a
curated list of *categories* to suggest, e.g. "Social", "Games", since
`ActivityCategoryToken`s are just as valid a shield target as app tokens).

Habit picking, easiest-first ranking, and the ease-in ramp
(`EaseInRepository`-equivalent) port directly — pure app logic, no
platform dependency.

### Home / enforcement loop
`AccessibilityService` watching foreground-app switches → **not replicated
directly**; instead, the shield is simply kept "on" (`ManagedSettingsStore().shield.applications`
set to every token, `.shield.applicationCategories` for picked categories) any
time today's gating habits aren't done, and cleared the moment they are — the OS
handles interception, Locke's app doesn't need to watch anything live. On habit
completion: clear the shield synchronously from the main app (instant, since it's
just writing to the `ManagedSettingsStore`, no background round-trip). On a new
day / new penalty: re-apply it. This is actually simpler than Android's
service-based approach, at the cost of the shield screen's UI being fixed.

The shield's configured button opens the app (deep link via a custom URL scheme
or, cleaner, a `ShieldActionExtension` that hands off through the App Group and a
scene-activation) straight to the full-screen habit checklist — Locke's
`BlockOverlayActivity` equivalent, built as a normal SwiftUI view Presented
full-screen on launch when a shield handoff flag is set.

### Bedtime curfew
Same shield mechanism, but the *schedule* — not habit state — decides when it's
on. Implement with a `DeviceActivitySchedule` spanning the curfew window
(supports overnight wraps like 22:30–06:30 natively) and toggle the shield from
the `DeviceActivityMonitor` extension's `intervalDidStart`/`intervalDidEnd`
callbacks, so it's enforced even if the main app isn't running — matching
Android's "no bypass, no token lifts it" guarantee.

### "Use an app for N minutes" habit type
The one feature genuinely downgraded by the platform. Register a
`DeviceActivityEvent` with a threshold (e.g. cumulative 15 minutes today in the
target app's token) on the `DeviceActivityMonitor` extension; its
`eventDidReachThreshold` callback flips the habit to done via the shared App
Group store. For closer-to-live progress, register several thresholds at smaller
increments (e.g. every 1–2 minutes up to the target) — Apple caps the number of
monitored events, so this needs tuning per habit rather than being infinitely
granular like Android's 15-minute polling + on-exit refresh. Document this
explicitly in the UI copy (progress may lag by a minute or two) rather than
promising Android's live number.

### Photo verification & morning check-in
Ports directly. `AVFoundation` custom capture (no `UIImagePickerController`
gallery mode, keeping the Android "camera roll can't stand in for today's proof"
rule), same Claude Messages API call (downscale, attach example photo if any,
one-sentence reasoning surfaced either way), same local-only storage under the
app's own container (`Documents/verification/` via `FileManager`, App Group
container if the shield extension ever needs to reference it). Proof-of-life
photos still discarded after verification, same as Android's `saveToCache`.

### Steps / Workout / Sleep habit types
Direct `HealthConnectManager` → `HealthKitManager` port: `HKQuantityType`
stepCount and workout duration, `HKCategoryType` sleep analysis over a trailing
24h window, manual fallback entry kept identical to Android's.

### URL block lists
`UrlBlockList` (premade porn/social lists + custom lists, `GATED`/`PERMANENT`
modes) maps onto `ManagedSettingsStore().webContent.blockedByFilter` /
Safari-specific shielding via the same Screen Time stack — domains, not apps, so
this uses `WebDomainToken` via a `FamilyActivityPicker` `.webDomains` selection
for anything user-added, and a bundled domain list (same asset ported over) for
the premade lists applied via `blockedByFilter(.some(...))`. `PERMANENT` mode is
simplest of all here — just never included in the "lift on habit completion"
code path.

### Penalty engine
Pure logic (`PenaltyRepository`'s three primitives: extend lock, add makeup
habit, mark day broken) — ports as-is; "extend lock" becomes "push the shield's
clear-time forward," everything else unchanged.

### Daily lootbox, todos, stats/heatmap
No platform dependency beyond rendering — SwiftUI `Canvas` for the heatmap
(direct analog of Compose's `Canvas`-drawn GitHub-style grid), same weighted
reward pool and reward types (grace token, cosmetic star, theme unlock,
task-skip token), same todo list merged into Home rather than its own tab.

### Reminders & background work
Morning todo reminder and streak/habit nudges → local notifications scheduled
via `UNUserNotificationCenter`, kicked by a `BGAppRefreshTask` checking near the
configured time (mirrors the ~15-minute-cadence periodic check Android uses,
since iOS won't grant exact-alarm-style background wake either). Midnight
day-rollover: same date-keyed storage approach as Android (habit completion
keyed by calendar date, not a flag to clear) — no explicit reset task needed,
same as today.

### Billing
Three products (monthly, annual, lifetime) via StoreKit 2's `Product.products(for:)`
+ `Transaction.currentEntitlements` for restore/entitlement checks — no receipt
server needed for this app's scope, matching the simplicity of the current
`StubEntitlementRepository`/`EntitlementRepository` split (keep that seam: a
`StoreKitEntitlementRepository` behind the same protocol used elsewhere).

### Theme / design system
Six eco-brutalist accent variants (Moss, Modern free; Rust, Concrete, Ink,
Receipt lootbox-unlockable, or redeemable via code) — port as a SwiftUI
`EnvironmentKey`-based theme token set: flat neutrals, 0pt corner radius
everywhere (one shared shape token, same as Compose's single `Shapes` object),
heavy display type (a bundled condensed/stamped font, since SF Pro won't match —
check what Android's using and either bundle the same font or its closest OSS
equivalent), monospace numeric readouts (`SF Mono` is a fine native swap),
outlined rather than shadowed cards.

## Proposed project structure

```
ios/
├── Locke.xcodeproj
├── Locke/                       # main app target
│   ├── Domain/                  # Habit, HabitKind, HabitType, BlockedApp, ThemeVariant, ...
│   ├── Data/
│   │   ├── Persistence/         # Core Data stack, repositories
│   │   ├── ScreenTime/          # ManagedSettingsStore wrapper, FamilyActivitySelection storage
│   │   ├── HealthKit/
│   │   ├── Billing/             # StoreKit 2 wrapper + EntitlementRepository
│   │   └── Verification/        # Claude API client (Swift port of AnthropicImageVerificationClient)
│   ├── Services/                 # BGTaskScheduler jobs, notification scheduling
│   ├── UI/
│   │   ├── Onboarding/
│   │   ├── Home/
│   │   ├── Habits/               # gating/tracked/antihabit sections, heatmap
│   │   ├── Todos/
│   │   ├── HabitForm/
│   │   ├── AppPicker/            # wraps FamilyActivityPicker
│   │   ├── Settings/
│   │   ├── Block/                # full-screen checklist shown after shield handoff
│   │   └── Theme/
│   └── Util/
├── ShieldConfigurationExtension/ # customizes the blocked-app screen
├── ShieldActionExtension/        # handles the shield's button taps, hands off to the app
├── DeviceActivityMonitorExtension/ # interval + threshold callbacks (bedtime, app-usage habit progress)
├── DeviceActivityReportExtension/  # (only if/when a usage-stats screen is added — optional for v1)
└── LockeShared/                  # App-Group-shared code: models, Keychain/UserDefaults(suiteName:) helpers used by app + all extensions
```

## Phased build order

### Phase 0 — de-risk the Screen Time stack (1-2 days)
Before any UI work: a throwaway Xcode project that requests
`.individual` `FamilyControls` authorization, shields one app via
`FamilyActivityPicker` + `ManagedSettingsStore`, and confirms the
`ShieldActionExtension` → main-app handoff actually works end to end on a real
device (this cannot be verified in Simulator — Screen Time APIs require a
physical device signed with the real entitlement). Also file the
`com.apple.developer.family-controls` entitlement request with Apple now, since
approval lead time is the single biggest schedule risk in this whole plan and
gates every other phase's ability to test on-device.

### Phase 1 — foundation
Xcode project scaffold, Core Data model mirroring the Room schema, domain-model
Swift port, theme system, navigation shell (`TabView`: Home / Stats / Settings),
CI (`xcodebuild test` on a `macos` GitHub Actions runner, mirroring
`android-build.yml`'s pattern — build+test on push to `main`/`claude/**` and on
PRs into `main`).

### Phase 2 — core loop
Onboarding (habit picks, `FamilyActivityPicker`-based app selection, ease-in
ranking), Home (gating/tracked/antihabit sections, manual + tally logging),
shield apply/clear wired to habit completion, the full-screen checklist screen
shown on shield handoff, bedtime curfew via `DeviceActivitySchedule`.

### Phase 3 — measured habit types
Timed (stopwatch), app-usage-minutes (threshold events), Steps/Workout/Sleep via
HealthKit, Tally.

### Phase 4 — photo verification & proof of life
Camera-only capture screen, Claude API client port, morning check-in flow.

### Phase 5 — penalties, lootbox, stats
`PenaltyRepository` port, weighted lootbox reveal, heatmap + distribution stats
views.

### Phase 6 — URL blocking, billing, polish
Web-domain shields (premade + custom lists), StoreKit 2 entitlements, reminder
notifications, settings screens (theme picker/redeem codes, permissions
revisit), accessibility pass, App Store screenshots/listing copy.

## Open questions to settle before Phase 1 starts

1. **Minimum iOS version** — 16 (Core Data, wider device support) vs. 17+
   (SwiftData, `@Observable`, newer `DeviceActivity` APIs). This plan assumes 16;
   flag if that's wrong.
2. **Family Controls entitlement timeline** — has it been requested from Apple
   yet? This gates on-device testing for the entire blocking feature, so it
   should go out immediately regardless of when coding starts.
3. **Font licensing** — Android's "heavy stamped display type" needs either the
   same font file (if licensed for bundling) or a chosen OSS equivalent for iOS.
4. **Shield → app handoff mechanism** — custom URL scheme vs. the newer
   extension-context handoff APIs varies by iOS version support target; Phase 0
   should settle this concretely rather than this plan guessing.
5. **Package/bundle ID** — Android's README already flags its package name as a
   pre-rebrand leftover needing a deliberate rename; the iOS bundle ID
   (`com.???.locke`) should be decided once, since App Store bundle IDs are just
   as permanent as Android's `applicationId`.

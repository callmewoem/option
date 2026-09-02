# Locke — UX Issues Report

A screen-by-screen audit of the Locke Android app's UI layer (Jetpack Compose),
covering onboarding through daily use. Every finding below cites a specific
file and line and describes the concrete behavior observed in the code —
this is not a general checklist.

**Scope:** `app/src/main/java/com/habitsfirst/androidclone/ui/**` and
`app/src/main/res/values/strings.xml`, cross-checked against the product
description in `README.md`.

**36 findings** — 5 High, 13 Medium, 18 Low.

## Cross-cutting patterns

Four patterns recur across screens and are worth fixing systematically
rather than one finding at a time:

1. **Confirmation friction is inverted.** Trivial, reversible actions (deleting
   a habit) get an `AlertDialog`. Consequential, hard-to-reverse actions
   (logging an antihabit slip, enabling Hard Mode, resetting/backing out of a
   running timer) get none. See #4, #5, #17, #18, #31.
2. **String-resource discipline degrades screen by screen.** Home and
   onboarding are mostly resourced; Stats, Settings, and especially URL Block
   are almost entirely hardcoded literals. See #7, #13, #15, #20, #22, #26,
   #28, #33.
3. **Hard Mode's "why is this locked" messaging is inconsistent.** Add/Edit
   Habit explains a Hard-Mode-disabled control with a caption in three
   places; App Picker and Blocked Websites disable the equivalent controls
   with no explanation anywhere on the page. See #16, #23, #25.
4. **Silent failure is the default.** Invalid domain input, blank todo text,
   a denied camera permission, and unparseable time strings all fail with no
   visible feedback to the user. See #6, #24, #29, #32.

---

## Onboarding

### 1. Onboarding can be completed with zero permissions granted — silently defeating the app [High]
`ui/onboarding/OnboardingPermissionsScreen.kt:56-66`

The "Start locking in" button is gated only on `!state.isFinishing`, never on
`hasUsageAccess` / `hasAccessibility` / `hasOverlay`. A user can leave all
three permission cards showing "Not granted" and still finish onboarding
into a Home screen that looks fully configured (habits, streak, locked-app
count) while nothing actually locks anything. Nothing on Home re-surfaces
this — the only permission nudges live in Settings, unprompted. This
directly undermines the app's core promise with no warning that it isn't in
effect.

### 2. A failed `finishOnboarding()` permanently strands the Finish button [Medium]
`ui/onboarding/OnboardingViewModel.kt:113-149`

`isFinishing` is set to `true` before the coroutine body runs and is only
reset at the end of the (try-less) block. Any exception mid-flow leaves
`isFinishing = true` forever — the button stays disabled with no error
shown and no way to retry.

### 3. Onboarding uses raw strings where sibling screens use resources [Low]
`ui/onboarding/OnboardingPickAppsScreen.kt:88` (`"Recommended"`),
`ui/onboarding/OnboardingPickHabitsScreen.kt:133,139`
(`contentDescription = "Move easier"` / `"Move harder"`)

---

## Home

### 4. Logging an antihabit "slip" applies an irreversible penalty on a single, unconfirmed tap [High]
`ui/home/HomeScreen.kt:210-211`, `ui/home/HomeViewModel.kt:154-160`,
`ui/components/HabitCard.kt:54-55`

The entire `HabitCard` row is the tap target, styled identically to every
other (non-destructive) habit card. One mis-tap calls
`penaltyRepository.applyAntihabitSlipPenalty(...)`, extending the block lock
by 10 minutes — with no confirmation dialog, unlike deleting a habit
(`AddEditHabitScreen.kt:307-321`), which *does* confirm for a far less
consequential action.

### 5. Untapping a slip does not revert the penalty it already applied [High]
`ui/home/HomeViewModel.kt:154-160`

```kotlin
habitRepository.setAntihabitSlipLogged(habitId, logged)
if (logged) penaltyRepository.applyAntihabitSlipPenalty(habitName)
```

There is no `else` branch to undo the penalty, and `PenaltyRepository`
exposes no revert function at all. A user who taps an antihabit by accident
and immediately taps again to "undo" it sees the card return to its clean
state — but the 10-minute lock extension silently persists. The UI actively
misleads the user into thinking the mistake was fixed.

### 6. "Add todo" silently discards whitespace-only input but clears the field as if it succeeded [Low]
`ui/home/HomeScreen.kt:238-247`, `ui/home/HomeViewModel.kt:162-165`

`onAddTodo` early-returns on `title.isBlank()`, but the screen unconditionally
clears the input regardless of whether anything was actually added.

### 7. Extensive hardcoded, non-localized copy on Home despite partial resource use elsewhere on the same screen [Low]
`ui/home/HomeScreen.kt:245` (`"Add"`), `:294,299` (`"Today"`/`"Tomorrow"` chips),
`:326` (`"Tomorrow"` row label), `:389` (`"Skip tour"`), `:402`
(`"Next"`/`"Got it"`), `:456,461` (proof-of-life banner), `:497-519`
(photo-verification banner), `:574` (`"$lockedAppCount locked"`), `:592,595`
(empty-habits card)

### 8. "$lockedAppCount locked" has no pluralization and isn't localizable [Low]
`ui/home/HomeScreen.kt:574`

Built by string concatenation rather than a `plurals`/format resource — and
a matching resource, `home_blocked_apps` (`strings.xml:53`), already exists
unused (see #34), suggesting this was meant to go through resources.

---

## Log Progress Dialog

### 9. Confirming with an emptied field silently saves 0 [Low]
`ui/home/LogProgressDialog.kt:50` — `onConfirm(text.toIntOrNull() ?: 0)`

The field can be cleared entirely; confirming then writes `0` with no
warning. A user clearing the box meaning to cancel instead overwrites real
progress to zero.

### 10. Field label is a raw string, not a resource [Low]
`ui/home/LogProgressDialog.kt:43`

---

## Habits (Stats)

### 11. Heatmap color meaning is never explained on screen [Medium]
`ui/habits/HabitsScreen.kt:122-135`

Unlike "Completion by habit," which has an explicit `KindLegend()` (line
142), the heatmap has no legend at all. A first-time user sees a grid of
colored squares with no key for red / gold / shading gradient — pure
color-only signaling, also an accessibility problem for colorblind users.

### 12. Gold-star color replaces completion shading instead of overlaying it, contradicting the README [Low]
`ui/habits/HabitsScreen.kt:125-132` vs. README's description of the star as
"a cosmetic gold-star overlay"

```kotlin
date in state.goldStarDates -> secondary
date in state.scarredDates -> error
score == null -> emptyColor
else -> heatmapFractionColor(score, primary)
```

The branch order also means a day that is both gold-starred and later
broken renders as a plain gold-star day rather than "broken," since
`goldStarDates` is checked first.

### 13. Entire Stats screen bypasses `strings.xml` [Low]
`ui/habits/HabitsScreen.kt:72,75,140,146,174,195-201` — a stark
screen-level inconsistency next to Home and onboarding.

---

## Add/Edit Habit

### 14. Target-value field visually refuses to go blank [Medium]
`ui/habit/AddEditHabitScreen.kt:198-199`

```kotlin
onValueChange = { text -> text.toIntOrNull()?.let(viewModel::onTargetValueChanged) }
```

Clearing the field to retype a number makes `toIntOrNull()` return `null`;
the callback is skipped, state doesn't change, and the field (bound to
`state.targetValue.toString()`) snaps back to the old digits. The natural
"select all, retype" gesture appears broken.

### 15. "Locked by Hard Mode" duplicated as four separate literals, plus other pervasive hardcoded copy [Low]
`ui/habit/AddEditHabitScreen.kt:127` (`"Kind"`), `:144-148` (kind
descriptions), `:189,212,370,412` (`"Locked by Hard Mode"` ×4, each an
independent literal), `:229` (`"Choose app"`), `:246` (`"Frequency"`),
`:276` (`"Open timer"`), `:310-311` (delete-confirm copy), `:350`
(`"Every day"`), `:397-446` (photo-verification section)

A future copy change to "Locked by Hard Mode" requires editing four places,
and a translator would translate the same sentence four times.

### 16. Hard Mode explains itself here, but not on the two other screens with equivalent locks [Low]
`ui/habit/AddEditHabitScreen.kt:186-193,211-215` (captions present) vs.
`ui/apppicker/AppPickerScreen.kt:118-122` and
`ui/urlblock/UrlBlockScreen.kt:179-186,203-211` (no caption at all) — see
also #23, #25.

---

## Timed Habit Timer

### 17. Backing out of a running timer silently discards all unsaved elapsed time [High]
`ui/habit/TimedHabitTimerScreen.kt:50-54` (back arrow calls `onDone`
directly) + `ui/habit/TimedHabitTimerViewModel.kt:64-84,105-108`

`persistProgress()` is only called from `pause()` and on completion — never
from `onCleared()`, which just cancels the ticker job. Pressing back (top
bar or system) before ever pausing loses every second accumulated since the
last pause, with no warning. For a workout or meditation habit this can mean
losing an entire session's credit silently.

### 18. "Reset" has no confirmation and can overwrite already-persisted progress [Medium]
`ui/habit/TimedHabitTimerScreen.kt:104-108`,
`ui/habit/TimedHabitTimerViewModel.kt:86-89`

`onReset()` zeroes `elapsedSeconds` on a single unconfirmed tap (compare to
habit deletion, which *is* confirmed). Because `persistProgress()` writes
`elapsedSeconds / 60` on the next `pause()`, reset-then-pause overwrites the
day's stored progress back to 0 even if a prior session had already banked
real minutes.

### 19. "Mark complete" exists in resources/ViewModel but is never wired up — no manual completion path for Timed habits [Medium]
`strings.xml:104` (`timer_complete`),
`ui/habit/TimedHabitTimerViewModel.kt:91-97` (`onMarkComplete()`)

`TimedHabitTimerScreen.kt` never renders a button calling `onMarkComplete()`,
and `HomeScreen.kt:215` always routes `TIMED_MINUTES` habits to the timer
screen rather than the Log Progress dialog. A Timed habit can *only* be
completed by running Locke's own in-app stopwatch to the full duration —
there's no way to declare it done if the activity happened outside the app,
unlike every other measurable habit type. The orphaned string/method suggest
this affordance was planned and cut, leaving a real gap in the daily loop.

### 20. Fallback title/labels not localized [Low]
`ui/habit/TimedHabitTimerScreen.kt:49,76,86`

---

## Block Overlay / Lock Screen

No findings — the bedtime and permanent-block branches correctly omit any
bypass UI, matching the README's "no token bypasses" claim
(`ui/block/BlockScreen.kt:113-114`, `ui/block/BlockOverlayViewModel.kt:87-88`).

---

## URL Block (Blocked Websites)

### 21. Almost the entire screen bypasses `strings.xml` [Medium]
`ui/urlblock/UrlBlockScreen.kt:62,75,78-80,97,110,146,168-170,176,209,
223,228-229,235-236,240,257,267,280,286,297,307` — every string here is a
literal except `back`/`cancel`/`done`. The least-localized screen in the app.

### 22. Hard Mode disables controls with zero explanatory copy anywhere on the page [Medium]
`ui/urlblock/UrlBlockScreen.kt:175-186` (delete icon, switch),
`:200-211` (segmented mode buttons)

A user with Hard Mode on finds several controls inexplicably unresponsive
here, with no "Locked by Hard Mode" caption anywhere — unlike Add/Edit
Habit's equivalent locks (#16).

### 23. Adding an invalid domain silently fails with no error [Low]
`ui/urlblock/UrlBlockScreen.kt:272-281` +
`data/repository/UrlBlockRepository.kt:152-155`

```kotlin
suspend fun addDomain(listId: String, rawDomain: String) {
    val domain = normalizeDomain(rawDomain) ?: return
    ...
}
```

The UI clears the input field the moment it's non-blank, regardless of
whether normalization actually succeeded. A rejected domain looks
identical to a successfully added one — it just never appears in the list.

---

## App Picker

### 24. Hard Mode disables the block toggle with no explanatory text [Low]
`ui/apppicker/AppPickerScreen.kt:117-123` — same pattern as #22, no caption
anywhere on the screen (contrast Add/Edit Habit, #16).

### 25. "Search apps" label hardcoded [Low]
`ui/apppicker/AppPickerScreen.kt:67`

---

## Proof of Life (Morning Check-in)

### 26. Back arrow silently exits a due check-in with no warning that a penalty follows [Medium]
`ui/proofoflife/ProofOfLifeScreen.kt:47-51`

The back arrow calls `onDone` — the same callback used when verification
actually succeeds (`LaunchedEffect(state.isDone)` at line 39-41).
Functionally this just pops the screen (`confirmToday()` is only called on
approval, per `ProofOfLifeViewModel.kt:91`), but nothing tells the user that
backing out leaves the check-in undone and still treated as missed by the
penalty/grace-window logic. A user backing out "to do it in a minute" gets
no reminder of the stakes.

### 27. Title, body, and prompt copy all hardcoded [Low]
`ui/proofoflife/ProofOfLifeScreen.kt:46,65,68-69,86`

---

## Photo Verification (shared capture component)

### 28. Denying camera permission produces total silence [Medium]
`ui/components/PhotoVerificationCapture.kt:76-84`

```kotlin
val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
) { granted -> if (granted) { ... } }
```

No `else` branch. Denying the permission (including the real Android "Don't
ask again" flow) leaves the user back at the same "Take photo" button with
no feedback about what happened or how to fix it — contrast the
missing-API-key case a few lines down (`:151-153`), which does show an
error and an "Open Settings" button.

### 29. Inconsistent button sizing between the "ready to submit" and "rejected" states [Low]
`ui/components/PhotoVerificationCapture.kt:139-144` vs. `:130-133`

The not-yet-submitted row applies `Modifier.fillMaxWidth()` to only the
second button (`OutlinedButton(onRetake)` gets no modifier) — inside a `Row`
this measures the button against the full row width rather than filling
remaining space, producing visibly unbalanced button sizes that the
"rejected" state's row (both buttons unmodified) doesn't have.

---

## Settings

### 30. Hard Mode toggle — a multi-day, hard-to-reverse commitment — has zero confirmation [High]
`ui/settings/SettingsScreen.kt:321-326`, `ui/settings/SettingsViewModel.kt:220-222`

```kotlin
Switch(checked = state.hardModeEnabled, onCheckedChange = viewModel::onHardModeToggled, enabled = !toggleLocked)
```

Enabling this locks every current gating habit and blocked app/URL list as
permanently non-removable ("Gates and blocked apps can only be added, never
removed," line 305) and, per the cooldown copy, can't be toggled again for
days. This is one plain `Switch` flip with no `AlertDialog` — while deleting
a single habit (`AddEditHabitScreen.kt:307-321`) *does* get a confirmation.
The single most consequential control in the app has the least friction
protecting it.

### 31. Bedtime / reminder / proof-of-life times are raw free-text `HH:mm` fields with no validation [Medium]
`ui/settings/SettingsScreen.kt:483-496,513-521,543-551` +
`ui/settings/SettingsViewModel.kt:203-213`

No `TimePicker`, no format mask, no validation — `onBedtimeChanged` and
siblings pass the raw string straight to their repositories. Typing "9pm" or
"25:99" produces no error; if the downstream parser fails silently, the
bedtime curfew or morning check-in — both carrying real penalty
consequences — could stop working with no indication to the user.

### 32. Extensive hardcoded, non-localized copy across most of the Settings screen [Low]
`ui/settings/SettingsScreen.kt:177-188` (Theme), `:250-274` (Rewards),
`:277-354` (Bedtime/Reminders/Hard Mode/Ease-in), `:402-408` (Photo
verification), `:412-415` (Version) — the same half-resourced pattern as
other screens, at the largest scale in the app.

### 33. Two string resources exist for copy that was apparently never wired up [Low]
`strings.xml:86` (`settings_reset_streak_warning`, "Editing habits after
today resets your current streak safeguard."), `strings.xml:53`
(`home_blocked_apps`, "Locked apps"), `strings.xml:55`
(`home_all_done_subtitle`, "Unlocked for today.")

None of these three are referenced anywhere under `ui/**`.
`settings_reset_streak_warning` in particular implies editing a habit
*should* warn about a streak-safety consequence, but
`AddEditHabitScreen.kt`'s save flow shows no such warning today.

---

## Theme / Accessibility

### 34. In the Ink and Receipt themes, "secondary" and "error" accent colors are nearly indistinguishable — defeating the app's own "accent color is the only signal" design [High]
`ui/theme/Color.kt:64-67` (`InkSecondary80 = 0xFFFFB3AE`) vs. `:26-27`
(`Error80 = 0xFFFFB4A4`); `:84-87` (`CarbonSecondary80 = 0xFFFFB4A9`) vs. the
same `Error80`; light-mode equivalents `InkSecondary40 = 0xFFA6191A` /
`CarbonSecondary40 = 0xFFB3261E` vs. `Error40 = 0xFF8C1D14`

`ui/components/HabitKindAccent.kt:14-18` maps `TRACKED → secondary` and
`ANTIHABIT → error`, and `HabitCard.kt`'s own doc comment (lines 34-38)
states the accent bar is meant to be "the app's only 'which list is this'
signal." In the Ink theme, `secondary` (`#FFB3AE`) and `error` (`#FFB4A4`)
differ by a handful of RGB values — visually the same color. The Receipt
theme has the same near-collision. Both themes are lootbox-unlockable, not
rare. Users on either can't reliably tell a Tracked habit from an Antihabit
by color alone — exactly the scenario the design comment says should never
happen — and the same collision degrades the Stats heatmap's gold-star vs.
broken-streak-day distinction (`HabitsScreen.kt:128-129`, using `secondary`
and `error` respectively).

### 35. Bottom nav tab labels hardcoded [Low]
`ui/navigation/LockeBottomBar.kt:19-20` (`"Home"`, `"Stats"`)

---

## Suggested priorities

If addressing this incrementally, tackle in this order:

1. **#1, #30, #4/#5** — the three places where a single unconfirmed tap
   either defeats the app entirely or applies an un-undoable consequence.
   These undercut the core value proposition, not just polish.
2. **#34** — the color collision breaks the app's own stated accessibility
   design for two shipped themes.
3. **#17, #19** — the timer's silent data loss and the missing manual-complete
   path are real gaps in the core daily loop for Timed habits.
4. **#16/#22/#24, #28, #23/#31** — Hard Mode messaging consistency, silent
   input failures, and denied-permission handling.
5. Everything else — hardcoded-string cleanup — can be swept in one pass
   per screen once resources exist for the strings identified above.

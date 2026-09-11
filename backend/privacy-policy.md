# Locke Privacy Policy

_Last updated: 2026-09-10_

Locke ("the app") is an independent, from-scratch Android habit/app-blocking
app. This policy explains what data it collects, why, and how to control it.
It's written to describe the app's actual behavior, not boilerplate -- if the
code changes what's collected, this file (and its two copies, below) is
updated in the same change.

**This one file is the source of truth.** It's duplicated in two places that
can't simply link back here -- `backend/privacy-policy.md` (served at the
backend's own `GET /privacy`, since app stores require a public policy URL)
and the Android app's `app/src/main/assets/privacy_policy.md` (bundled so the
in-app "Privacy Policy" screen in Settings works offline). All three must say
the same thing; update all three together.

## No account, no login

Locke has no sign-up and no login. Nothing here is tied to your name, email,
or phone number. The only identifier that exists is a random, anonymous
**device ID** minted the first time the app needs to talk to its own backend
(`POST /v1/devices`) and stored only on your device. It identifies *an
install*, not *a person* -- reinstalling the app gets you a new one, with no
link back to the old one.

## What stays entirely on your device

Most of what Locke does never leaves your phone:

- Which apps you've chosen to lock, and your habit list, streaks, completion
  history, todos, and settings -- stored locally (Room/SQLite), never
  uploaded anywhere.
- **Accessibility Service** data (which app is currently in the foreground) --
  used live, on-device, to decide whether to show the lock screen. Never
  transmitted, never logged, never stored beyond the current instant.
- **Usage access** (`UsageStatsManager`) data -- per-app screen-time minutes,
  used on-device to track "use an app for N minutes" habits and to recommend
  apps to lock during onboarding. Never transmitted.
- Proof photos for photo-verification habits and the morning check-in are
  held on-device until you submit one for checking (see below), and any photo
  a habit keeps as its own "example" reference stays local as well.
- Health Connect data (steps, workouts, sleep), if you turn that sync on --
  read directly from Health Connect into your local habit progress, never
  transmitted.

## What Locke's backend sees

A few features need a small server component (`backend/`) instead of talking
to a third party straight from the app, or a per-user server nobody wants to
run. Each one sends only what it needs to do its job:

- **AI photo checking** (`POST /v1/verify-photo`): when you submit a proof
  photo for a photo-verification habit or the morning check-in, it's sent to
  Locke's backend, which forwards it to Anthropic's Claude API for a single
  approve/reject verdict, then returns just that verdict to your device. The
  backend does not store submitted photos -- they're forwarded and discarded,
  not written to any database or disk on the server. They are, however, sent
  to Anthropic to produce the verdict; see [Anthropic's privacy
  policy](https://www.anthropic.com/legal/privacy) for how a received API
  request is handled on their end.
- **Accountability buddies** (`POST /v1/daily-summary`, `POST /v1/buddies`,
  `POST /v1/pairing-codes`): opt-in. If you turn on "share daily stats," only
  a small daily summary -- date, habits completed, total habits, current
  streak -- is uploaded for your paired buddy to see. No habit names, no
  habit content, no photos. Buddy pairing itself uses a short-lived,
  random pairing code, not any personal detail.
- **Google Play Billing** (`POST /v1/purchases/verify`, `GET
  /v1/entitlement`): if you subscribe to Premium, the purchase token Google
  Play gives your device is sent to the backend to verify the purchase
  against the Play Developer API and store your entitlement (what tier
  you're on, until when). No payment details (card numbers, etc.) ever pass
  through Locke's backend or app -- Google Play handles the transaction
  itself.
- **Experiment assignment** (`GET /v1/experiments`): the backend returns which
  variant of a small number of A/B tests (pricing presentation, free-tier
  limits, whether a given onboarding step is shown) your device is bucketed
  into, keyed only by your anonymous device ID. See "A/B testing" below.
- **Analytics** (`POST /v1/analytics/events`): see "Analytics" below.

## Analytics

Locke logs a small number of product-analytics events -- things like "the
paywall was shown," "a purchase completed," "the free gating-habit limit was
hit" -- each a fixed event name plus a few non-identifying properties (e.g.
which A/B variant you're in, which premium feature you bounced off of). These
events:

- **Never** include your device ID's link to any real-world identity (there
  isn't one), a photo, a habit's name or text you typed, or anything else
  free-form.
- Are queued on-device and uploaded in small batches to Locke's own backend
  (never a third-party analytics vendor).
- Are used only to understand how the app's own features and experiments are
  performing -- never sold, never shared beyond running the app.

**You can turn analytics off** at any time in Settings -> Privacy ->
"Share anonymous usage analytics." Turning it off stops any further event from
being queued or sent -- it does not affect anything the app does, and nothing
already sent is retroactively deleted by this toggle.

## A/B testing

Locke occasionally runs small experiments -- e.g. testing two ways of
presenting subscription pricing, or two different free-tier habit limits --
to learn what actually works, the same way most software does. Your device is
bucketed into one variant of each active experiment, deterministically and
anonymously, keyed by your device ID; which variant you're in may itself be
included as a property on the analytics events above, so results can be
compared across variants. There's no separate opt-out for experiments beyond
the analytics toggle above (with analytics off, your device is still
assigned a variant so the feature it affects still works, but that
assignment is never reported anywhere).

## Data retention & deletion

Since there's no account, there's no "delete my account" flow -- uninstalling
the app removes everything stored on your device. To also remove what's
associated with your device ID on the backend (queued analytics events,
buddy pairing/summary rows, entitlement record), contact the developer with
your device ID (visible in Settings -> Diagnostics) and ask for it to be
deleted.

## Changes to this policy

If what Locke collects changes, this file changes with it, in the same
commit as the code change -- there's no separate legal review process for an
independent app this size, but the intent is that this document never lags
behind the actual behavior.

## Contact

This is an independent project, not affiliated with any company. Open an
issue on the project's repository for privacy questions.

# Technical Specification — Daily Collective Sitting (V1)

**Product:** Vipassana Timer (`com.vipassana.silenttimer`)  
**Source of truth (product):** `feature1.md`  
**Status:** Implementation specification — **do not implement until this doc is approved**  
**App baseline:** Kotlin, Jetpack Compose, Material 3, local `TimerService` foreground timer, SharedPreferences meditation log, no network, no user accounts

---

## 1. Purpose of this document

Translate `feature1.md` into an implementable technical design for the existing Android app.

This document specifies:

- What to build in V1
- How it maps onto the current codebase
- Data models, APIs, state machines, and client modules
- Timer integration (reuse `TimerService`; do not fork a second timer)
- Offline, timezone, privacy, and notification behavior
- Phased delivery and open decisions

It does **not** authorize implementation.

---

## 2. Current application baseline

### 2.1 What exists today

| Area | Current implementation |
|---|---|
| UI | Compose screens in `ui/`: `HomeScreen`, `TimerScreen`, `CompletionScreen`, `CalendarLogScreen`, `AwarenessScreen`, settings, support |
| Navigation | Ad-hoc Compose state in `MainActivity` + modal drawer (no Navigation component) |
| Timer engine | `TimerService` foreground service (`ACTION_START` / `ACTION_STOP`, prep countdown, gongs, wake lock) |
| ViewModel | `TimerViewModel` binds service, exposes `StateFlow`s, `startTimer(context, durationMillis)` |
| Local log | `MeditationLogStore` — daily totals (`date → millis + sessionCount`) in SharedPreferences |
| Durations | Home presets: 15, 30, 45, 60, 120 minutes + custom |
| Network | None (`AndroidManifest` has no `INTERNET`) |
| Identity | None |
| Backend | None |

### 2.2 Invariants this feature must not break

1. Solo Vipassana Timer remains fully usable without a Collective Sitting commitment.
2. Meditation countdown, prep, gongs, wake lock, and completion logging stay in `TimerService`.
3. Collective Sitting is a **session controller** around the existing timer, not a second timer engine.
4. A sit that loses network must continue locally.
5. No surveillance (camera, mic, motion, biometric, phone-use monitoring).
6. No social features listed as V1 exclusions in `feature1.md` §3.

---

## 3. V1 scope (engineering)

### 3.1 In scope

- Recurring daily commitment (days + local start time + duration)
- Deterministic Meditation Hall grouping
- Pre-sit Hall (T−5 min), check-in (T±5 min), sitting, complete (end±5 min)
- Anonymous avatars + aggregate live count
- Attendance + personal continuity metrics (not streaks)
- Minimal reminders (15 min, 5 min, start, end)
- Offline-resilient timer + queued attendance sync
- Home-screen Collective Sitting CTA
- Practice history calendar + personal dashboard
- Anonymous auth sufficient to attach attendance to a device/user

### 3.2 Out of scope (V1)

Buddy system, chat, DMs, feed, photos, snaps, likes, leaderboards, points, badges, public rankings, teacher tools, AI companion, public history, names/profiles from Hall, fuzzy time matching, fake participants.

### 3.3 Recommended duration set for Collective Sitting

Reuse existing timer durations. Collective Sitting picker exposes:

- 30, 45, 60 minutes (required)
- 90 minutes: **include if easy** (timer already accepts arbitrary millis; Home currently has 15 / 120 / custom but not 90). V1 picker: **30 / 45 / 60 / 90**. Solo Home presets stay unchanged.

---

## 4. Architecture

### 4.1 Layering

```text
Compose UI (Hall, commitment, dashboard)
        │
CollectiveSittingController  (client session FSM)
        │
   ┌────┴────────────────────┐
   │                         │
TimerService            CollectiveSittingRepository
(existing local timer)       │
                             ├── local Room / DataStore (schedule, pending attendance)
                             └── CollectiveSittingApi (HTTPS + realtime)
                                        │
                                 Session backend
                                   ├── Attendance (durable)
                                   └── Hall presence (ephemeral)
```

The existing timer stays independently functional. Collective Sitting **starts** `TimerService` with `durationMillis` and observes completion; it does not copy countdown logic.

### 4.2 New modules (package layout)

Proposed under `com.vipassana.silenttimer.collective`:

```text
collective/
  domain/          models, session key, attendance FSM, metrics
  data/            local store, API, presence, sync queue
  schedule/        commitment, next sit, timezone policy
  hall/            presence snapshot, avatar assignment
  notify/          reminder scheduling
  ui/              Compose screens for this feature
  CollectiveSittingViewModel.kt
  CollectiveSittingController.kt
```

Do not put Collective Sitting state into `TimerViewModel`. Keep a dedicated ViewModel; `MainActivity` / nav host coordinates “start existing timer from collective session.”

### 4.3 Backend (new; required for live Hall)

The app has no server. V1 needs a small backend. Recommended shape:

- **Auth:** anonymous Firebase Auth (or equivalent: device-issued UID persisted locally, server-minted). No phone/email required in V1.
- **Durable data:** Firestore or Postgres (schedules optional; attendance required).
- **Live Hall:** Firestore listeners **or** a single WebSocket/RTDB node per `session_id`.
- **Time:** server timestamps (`serverTimestamp` / NTP-equivalent) as authority for session open/close windows.

If Firebase is chosen, add:

- `INTERNET` permission
- Firebase Auth + Firestore (+ optional Realtime Database for presence)
- Play Services availability handling

**Decision recorded as default for this spec:** Firebase (anonymous Auth + Firestore + RTDB presence) to ship Hall + attendance without operating a custom cluster. Swap-out via `CollectiveSittingApi` interface.

---

## 5. Domain model

### 5.1 Session matching key (deterministic, V1)

```text
session_key = local_date | timezone_id | start_hhmm | duration_minutes
```

Example: `2026-09-06|Asia/Kolkata|06:30|30`

- `local_date`: ISO date in the user’s commitment timezone
- `timezone_id`: IANA id (`Asia/Kolkata`)
- `start_hhmm`: 24h clock time, minutes precision
- `duration_minutes`: integer

No fuzzy matching. Users with different timezones or start times are **never** in the same Hall in V1, even if UTC instants coincide.

`session_id` = stable hash or the key itself (URL-safe). Backend may store both.

### 5.2 Entities

#### User (server + local)

| Field | Notes |
|---|---|
| `user_id` | Anonymous auth UID |
| `timezone` | IANA; updated on explicit timezone policy |
| `avatar_id` | Assigned once, or hashed from `user_id` into a finite palette |
| `created_at` | Server |

No display name, photo, phone, email in V1.

#### SittingSchedule (local source of truth; optional server copy)

| Field | Type | Notes |
|---|---|---|
| `schedule_id` | UUID | |
| `user_id` | string | |
| `days_of_week` | set 1–7 | ISO; default all 7 |
| `start_time` | `LocalTime` | clock time, not instant |
| `duration_minutes` | int | 30/45/60/90 |
| `timezone` | IANA | |
| `active` | bool | |
| `updated_at` | instant | |

V1: **one active schedule per user**. Changing schedule is a confirm flow, not a silent preference edit.

Day presets map to `days_of_week`:

- Every day → `{1..7}` (default)
- Weekdays → `{1..5}`
- Weekends → `{6,7}`
- Custom → user-selected set, non-empty

#### SittingSession (server-authoritative per hall)

| Field | Notes |
|---|---|
| `session_id` | from matching key |
| `local_date` | |
| `timezone` | |
| `scheduled_start` | UTC instant **and** local clock |
| `scheduled_end` | `start + duration` |
| `duration_minutes` | |
| `state` | see §7 |
| `created_at` | lazy-create on first Hall join |

Sessions are created on demand when the first client enters or checks in. Empty halls are not pre-materialized for every possible key.

#### Attendance (durable)

| Field | Notes |
|---|---|
| `attendance_id` | UUID |
| `session_id` | |
| `user_id` | |
| `hall_entry_time` | nullable |
| `check_in_time` | nullable |
| `start_status` | `ON_TIME` \| `LATE` \| `MISSED` |
| `completion_time` | nullable |
| `completion_status` | `ON_TIME` \| `EARLY` \| `LATE` \| `NONE` |
| `final_status` | see §6 |
| `client_timer_started_at` | local, for offline reconciliation |
| `client_timer_ended_at` | local |
| `synced` | local flag |

Unique constraint: `(session_id, user_id)`.

#### HallPresence (ephemeral; not attendance)

| Field | Notes |
|---|---|
| `session_id` | |
| `user_id` | |
| `avatar_id` | denormalized for render |
| `state` | `PRESENT` \| `CHECKED_IN` \| `SITTING` |
| `last_seen` | server time; expire if stale |

TTL: **90 seconds** without heartbeat → drop from Hall. Completing/leaving removes presence immediately.

**Never** derive live Hall occupancy from historical attendance.

---

## 6. Attendance state machine (user)

```text
SCHEDULED
    → ENTERED_HALL          (join Hall in [T-5, T+5] or earlier pre-sit from T-5)
    → CHECKED_IN            ("I'm Sitting" in [T-5, T+5])
    → SITTING               (timer started; may also start LATE)
    → COMPLETED             ("Complete Sit" or timer-end confirm)

Exceptional:
    MISSED_CHECK_IN         (no check-in by T+5; still may Late Start)
    LATE_START              (check-in after T+5; timer still allowed)
    INCOMPLETE              (checked in / sitting, no completion by session close)
    CANCELLED               (user cancelled commitment for that day — V1: not exposed as a button unless needed)
    ABANDONED               (left Hall before check-in; or process death before check-in)
```

### 6.1 Window constants

Let `T` = scheduled start, `E` = scheduled end = `T + duration`.

| Window | Interval | Behavior |
|---|---|---|
| Pre-sit / Enter Hall | `[T-5min, E]` | Presence allowed; not counted as sitting until check-in |
| On-time check-in | `[T-5min, T+5min]` | `start_status = ON_TIME` |
| Late check-in | after `T+5min`, before `E` | `start_status = LATE`; CTA “Begin Sit”; still allowed |
| On-time complete | `[E-5min, E+5min]` | `completion_status = ON_TIME` |
| Early complete | before `E-5min` | `EARLY`; still allowed |
| Late complete | after `E+5min` (until session close) | `LATE`; still allowed |
| Missed | no check-in by `T+5` and user never starts | `final_status = MISSED` |

Session close: **`E + 30 minutes`** (backend). After close, presence node is deleted; late completion may still be accepted as `LATE` until close, then rejected (local timer can still finish solo).

### 6.2 Start / completion discipline (examples)

| Scheduled | Actual | Status |
|---|---|---|
| Start 06:30 | 06:28 | On-time |
| Start 06:30 | 06:34 | Late (still in ±5) |
| Start 06:30 | 06:41 | Late start (outside window; allowed) |
| End 07:00 | 07:01 | On-time |
| End 07:00 | 06:58 | Early completion (allowed) |

The system **records**; it does not block practice.

### 6.3 Final status resolution

| Condition | `final_status` |
|---|---|
| Checked in + completed | `COMPLETED` |
| Completed but start or end outside preferred windows | still `COMPLETED` (flags on start/completion status) |
| Checked in, never completed by close | `INCOMPLETE` |
| Never checked in | `MISSED` |
| Left before check-in | `ABANDONED` (do not count as missed sit if they never committed that day via check-in — **product default:** no Hall visit without check-in = `MISSED` only if the day was a scheduled sit day. Hall browse without check-in still `MISSED` for that scheduled day.) |

**Product default for V1:** a scheduled sit day without check-in is `MISSED`, even if they entered the Hall.

---

## 7. Collective session lifecycle (server)

```text
SCHEDULED  (virtual; may not exist as a row)
    → OPEN         (first presence or T-5, whichever first with a client)
    → CHECK_IN     (window open; overlapping with OPEN)
    → ACTIVE       (T reached; at least used as “timer should start for checked-in users”)
    → COMPLETION   (E reached; Complete Sit CTA)
    → CLOSED       (E+30min or last presence gone after E+5, whichever policy)
```

**Client must not decide collective start.** Client:

1. Fetches `scheduled_start` / `scheduled_end` as UTC from server (or computes from key + timezone tables but **validates** with server time offset).
2. Maintains `clock_offset = serverNow - deviceNow` (skew sample on each API call).
3. Uses `deviceNow + offset` for window checks.

If offline at T: start **local** timer at local T using commitment; mark attendance `pending_sync`; Hall count frozen/unavailable.

---

## 8. Timer integration

### 8.1 Required hooks (existing)

`TimerViewModel.startTimer(context, durationMillis)` → `TimerService.ACTION_START` + `EXTRA_DURATION`.

Existing behavior to keep:

- 8s prep countdown (`PREP_TIME_MILLIS`)
- Pre-end dong for durations ≥ 30 min
- End gongs
- `MeditationLogStore.addSession` on complete (solo daily totals **remain**; Collective Sitting has its own attendance store)

### 8.2 Collective Sitting controller

On successful check-in / late begin:

```text
CollectiveSittingController.onBeginSit(durationMs)
    → TimerViewModel.startTimer(context, durationMs)
    → observe hasCompleted / user Complete Sit
```

Do **not**:

- Reimplement `CountDownTimer`
- Bypass prep/gongs (reuse them; Hall UI can stay visible **under** or **instead of** `TimerScreen` — see §10)
- Pause/resume (current timer has stop, not pause; V1 collective sits are not pausable)

### 8.3 Dual logging

On collective complete:

1. Existing `MeditationLogStore` still records duration (practice minutes calendar stays truthful).
2. Collective `Attendance` records discipline fields.

Solo sits without a collective session do not create attendance rows.

### 8.4 Suggested service extras (minimal, later PR)

Optional, not required for first slice:

- `EXTRA_SESSION_ID` for notification copy (“Collective sit”)
- Suppress **start/end reminder notifications** while `isTimerRunning` (feature1.md §34)

Prep and gongs are part of the timer, not “Collective Sitting notifications.”

---

## 9. Timezones and DST

- Store commitment as **local clock time + IANA timezone**.
- Default policy if OS timezone changes: **keep the same local clock time** (06:30 stays 06:30 in the new zone). Prompt once: “Your sit remains 6:30 in [new zone].”
- Session key uses the **commitment timezone**, not a guessed GPS zone.
- DST: `ZonedDateTime.of(localDate, startTime, zone)` for that calendar day. If the local time is skipped (spring-forward), shift **forward** to the next valid local time and show a one-line notice. If duplicated (fall-back), use the **earlier** offset.
- Server stores both `scheduled_start_utc` and the key fields so Hall grouping stays on the key, not on UTC coincidence.

---

## 10. Client UX / screens

Keep visual language of current theme (`ui/theme`). Calm, static, low stimulation.

### 10.1 Navigation

Add screens; keep drawer. Suggested routes/states:

| Screen | Role |
|---|---|
| `HomeScreen` (existing) | Add TODAY card for collective sit |
| `CommitmentSetupScreen` | Slot → exact start → duration → days → save |
| `ChangeScheduleScreen` | Current vs new + Confirm change |
| `MeditationHallScreen` | Avatars, count, countdown, CTAs |
| `CollectiveSittingScreen` | Minimal during-sit overlay (or Hall + clock) |
| `SitCompleteScreen` | Calm summary (not existing celebratory copy) |
| `MissedSitScreen` | Next sit time |
| `PracticeDashboardScreen` | 30-day metrics + commitment |
| `CollectiveHistoryCalendar` | ✓ / ~ / • (can extend `CalendarLogScreen` or sit beside it) |
| Notification settings | Toggle 15m / 5m / start / end |

Primary CTAs (singular):

| Phase | CTA |
|---|---|
| Home, before window | Join Sit (enabled from T−5; visible earlier as disabled or “starts at 6:30”) |
| Pre-sit | Enter Hall |
| Check-in window | I'm Sitting |
| After check-in, before T | You're in. (no second action) |
| After T if not yet started | I'm Sitting / Begin Sit |
| Late | Begin Sit |
| After E | Complete Sit |

### 10.2 Home card

With commitment:

```text
TODAY
6:30 AM · 30 min
17 people sitting   // live if in window; else omit or “—”
[Join Sit]
```

Without commitment:

```text
Create your daily sitting commitment
[Choose a time]
```

### 10.3 Hall

- Custom sitting avatars (not emoji). Finite palette (e.g. 24 variants): body/hair/cloth/skin/posture, understated, static.
- Cap rendered avatars at **N = 24**. If `count > 24`, show 24 representatives + aggregate “127 sitting”.
- Empty: single user avatar + “You are the first one here.” **Never** fake people.
- Count copy:
  - Pre-sit: “N people are here” / “N people are preparing”
  - Sitting: “N people sitting with you”
- No join/leave toasts, no names, no profiles.
- Count may update; **no attention-grabbing animation** (crossfade number at most).

### 10.4 During meditation

Extremely minimal: time of day, remaining time (from `TimerService.timeLeftInMillis`), own avatar, a few others, aggregate count. Goal: put the phone down.

Do not fire Collective Sitting reminder notifications during an active sit.

### 10.5 Completion copy

```text
Sit complete
30 minutes
6:30–7:00 AM
17 people sat with you.
[Done]
```

Forbidden: Congratulations, streak flames, badges, points, hype.

### 10.6 Missed

```text
You missed today's sit.
Your next scheduled sit:
Tomorrow · 6:30 AM
```

No shame, no required explanation.

### 10.7 Metrics (personal)

Last 30 **scheduled** days (not rolling “opened the app”):

- Sits completed: `completed / scheduled`
- Started on time: `on_time_starts / scheduled`
- Completed on time: `on_time_completions / scheduled`

Calendar: ✓ on-time complete, ~ completed outside window, • missed. No public ranking.

Collective (optional on dashboard): “People sitting today” (global count) and “People sitting with you” (hall). No individual scores.

### 10.8 Schedule change

Two-step confirm showing current vs new commitment. Persist only on Confirm. Treat as a deliberate act (copy, not a technical lock-out).

---

## 11. Notifications

Use `AlarmManager` / `WorkManager` for local alarms from the commitment (works offline). Optional FCM later; **V1 = local**.

| Offset | Copy |
|---|---|
| T−15 min | Your 6:30 AM sit is coming up. |
| T−5 min | Your sit starts in 5 minutes. |
| T | Your sit is starting. |
| E | Your sit is complete. |

All four independently configurable; default **on**.

Do not post these if `TimerService` is already running a sit (start/end especially).

Need `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` policy for Android 12+; degrade to inexact if denied. Existing `POST_NOTIFICATIONS` already requested in `MainActivity`.

Channel: separate from `VipassanaTimerChannel` so users can mute reminders without muting the live timer notification.

---

## 12. Connectivity

| Network-dependent | Network-independent |
|---|---|
| Enter Hall | `TimerService` countdown |
| Live count / presence heartbeat | Local attendance draft |
| Upload attendance | Completion confirm on device |

On disconnect mid-sit: continue meditating; queue attendance; sync on reconnect (idempotent upsert by `session_id + user_id`).

Hall UI if offline: show last known count as stale **or** hide count and show “You’re sitting. Presence will update when you’re back online.” Prefer **hide live count** rather than a stale social number.

---

## 13. Privacy and security

Other participants may see: anonymous `avatar_id`, aggregate count, co-presence.

They must not see: phone, email, location, history, personal stats, photos, names.

Client:

- No camera/mic/activity recognition permissions for this feature
- HTTPS only
- Do not log PII
- Presence documents contain only `user_id`, `avatar_id`, `state`, `last_seen`

Security rules (Firestore example):

- Authenticated read of a session’s **presence list** and **participant_count**
- User can write only their own presence and their own attendance
- No query of another user’s attendance collection
- `participant_count` maintained by Cloud Function or increment/decrement on presence write (clients must not set the global count arbitrarily)

Avatar: derive `avatar_id = hash(user_id) % PALETTE_SIZE` so identity is not a chosen “look at me” profile. Optional later: user picks from palette without names.

---

## 14. APIs (logical)

Implement behind `CollectiveSittingApi`. Firebase can map 1:1 without a custom HTTP gateway.

### 14.1 `GET /me` / upsert user

Creates anonymous user + avatar assignment.

### 14.2 `PUT /schedules/active`

Body: days, start, duration, timezone. Server copy optional for analytics; **client is scheduling authority for reminders**.

### 14.3 `POST /sessions/{session_id}/presence`

Heartbeat every 20–30s while Hall is foregrounded. Body: `state`, `avatar_id`.

### 14.4 `GET /sessions/{session_id}`

Returns `scheduled_start_utc`, `scheduled_end_utc`, `state`, `participant_count`, `presence_preview` (up to 24 avatars).

Realtime: listen to the same document/node.

### 14.5 `PUT /sessions/{session_id}/attendance`

Idempotent. Body: hall_entry, check_in, start_status, completion_*, client timestamps.

Server validates windows using **server clock**, not client-claimed status. Client-proposed status is a hint; server overwrites.

### 14.6 `GET /me/attendance?from&to`

For dashboard/calendar.

### 14.7 `GET /stats/today` (optional)

Global sits-in-progress count. Aggregate only.

Rate-limit presence writes. Reject attendance after `CLOSED`.

---

## 15. Local persistence

Add a small store (Room **or** DataStore + JSON, consistent with current prefs style).

**Recommendation:** DataStore/SharedPreferences for schedule + notification prefs; Room or a JSON file for attendance queue and 30-day cache (mirrors `MeditationLogStore` simplicity).

Must persist:

- Active `SittingSchedule`
- `user_id`, `avatar_id`
- Clock offset
- Pending attendance upserts
- Last Hall snapshot (optional)
- Notification toggles
- Missed/completed flags for local calendar if sync delayed

---

## 16. Avatar assets

- Custom vector or PNG sitting figures (not emoji).
- Palette size 16–32.
- Static. If any motion, ≤ subtle breathing; default **static**.
- Hall layout: scattered sitting positions on a calm field; no camera/orbit/3D world.
- Large sessions: random stable subset (hash user_id) so the same people don’t flicker every heartbeat.

---

## 17. Analytics (product success; no gamification UI)

Instrument privately (if analytics exists later; V1 may log only server-side aggregates):

**Primary:** scheduled sit completion rate = completed / scheduled.

**Secondary:** on-time start rate; on-time completion rate; 30-day continuity.

**Collective:** count of sessions with ≥2 real checked-in participants.

Do not use DAU as the north-star. Do not show these as public rankings.

A/B (future, not V1 code): Timer only vs +schedule vs +live presence.

---

## 18. Integration points in existing files

| File | Change (when implementing) |
|---|---|
| `AndroidManifest.xml` | `INTERNET`; exact alarm if used; reminder receiver; optional FCM |
| `app/build.gradle.kts` | Firebase/OkHttp, WorkManager, Room/DataStore as chosen |
| `MainActivity.kt` | New screens; home CTA; deep link from reminder |
| `HomeScreen.kt` | TODAY collective card; do not remove duration grid |
| `TimerViewModel.kt` | No collective FSM; maybe `onCollectiveComplete` callback only if needed |
| `TimerService.kt` | Optional extras; suppress reminder collisions; keep engine |
| `MeditationLogStore.kt` | Unchanged contract; still log minutes |
| `CalendarLogScreen.kt` | Optional second layer for collective symbols **or** new screen |
| Theme | Reuse; Hall must stay low-contrast/calm |

---

## 19. Implementation phases (suggested PR slices)

Do not start until this spec is approved.

| Phase | Deliverable | Notes |
|---|---|---|
| P0 | Domain models, session key, local schedule, commitment UI, home card (offline, no Hall) | Shipable as “daily commitment + reminders” |
| P1 | Reminder alarms + missed copy | No backend |
| P2 | Anonymous auth, session API, attendance upsert, dashboard/calendar | Hall count can be REST poll |
| P3 | Live presence + Hall UI + avatars + check-in FSM | Core product |
| P4 | Wire check-in → `TimerService`; complete sit; dual logging; offline queue | Timer reuse |
| P5 | Timezone/DST, large-hall cap, notification mute during sit, polish copy | Hardening |

Each phase must keep solo timer working.

---

## 20. Testing

### 20.1 Unit

- Session key formatting
- Window classification (on-time / late / early / missed) with fixed clocks
- DST skipped/ambiguous local times
- Day-of-week matching
- Metrics over 30 scheduled days (not 30 calendar days if custom days)

### 20.2 Integration

- Check-in upsert idempotency
- Presence TTL expiry
- Offline complete → sync
- Timer start from controller does not double-log minutes incorrectly

### 20.3 UI / product

- Empty Hall copy, never fake users
- No social chrome
- Complete Sit copy has no celebration
- Reminders do not fire during active `TimerService` sit
- Schedule change requires confirm

### 20.4 Device

- Process death mid-sit: `TimerService` continues; attendance still completable
- Airplane mode after check-in
- Timezone change overnight

---

## 21. Open decisions (resolve before/during P0)

| ID | Topic | Spec default |
|---|---|---|
| D1 | Backend | Firebase anonymous Auth + Firestore + RTDB presence |
| D2 | 90-minute collective option | Include |
| D3 | One vs many schedules | One active schedule |
| D4 | Hall before T−5 | CTA visible; Enter Hall enabled only at T−5 |
| D5 | Enter Hall without check-in at end of window | Count day as `MISSED` |
| D6 | Timezone change | Keep local clock time + one prompt |
| D7 | Avatar | Hash(`user_id`) into palette; not user-customized in V1 |
| D8 | Global “people sitting today” | Optional; hide if backend cost is high |
| D9 | Navigation | Extend `MainActivity` state first; Navigation-Compose only if screen graph gets painful |
| D10 | Prep countdown in collective sits | Keep existing 8s prep |

---

## 22. Non-goals recap (engineering checklist)

- No second timer
- No chat, profiles, photos, reactions, leaderboards, streaks as primary UX
- No fake occupancy
- No fuzzy grouping
- No surveillance APIs
- No blocking users from sitting late or completing early

---

## 23. Acceptance criteria (V1)

1. User can save a recurring commitment (days, local start, duration, timezone).
2. Changing it requires an explicit confirm step.
3. At T−5, user can enter a Hall keyed by date+zone+start+duration.
4. Live count reflects real presence only; empty Hall is honest.
5. “I'm Sitting” in ±5 min starts the **existing** `TimerService` for the committed duration.
6. Late start is allowed and recorded as late.
7. Complete Sit in end±5 is on-time; outside window still allowed and recorded.
8. Offline after check-in: timer runs; attendance syncs later.
9. Dashboard shows completed / on-time start / on-time complete over last 30 scheduled sits, not a flame streak.
10. Reminders are optional and do not interrupt an in-progress sit.
11. Solo timer and meditation log still work with no commitment and no network.

---

## 24. Mapping to `feature1.md` sections

| Product § | This spec |
|---|---|
| 1–3, 54–55 | Philosophy, scope, journey |
| 4–7 | Schedule domain + setup/change UI |
| 8, 50 | Session key |
| 9–23 | Hall, avatars, identity, presence |
| 16–18, 24–31 | Attendance FSM, discipline |
| 19, 47–48 | Timer integration |
| 27–28, 44–46 | Metrics and history |
| 32–34 | Home CTA, notifications |
| 35–36 | Timezone, connectivity |
| 37–39 | Privacy, no social, no surveillance |
| 40–43 | Data model and lifecycles |
| 49 | Server time |
| 51–53 | Empty hall, success metrics, experiment (future) |

# Meditation Hall Platform — Product & Technical Design

## 1. Overview

### 1.1 Product concept

A platform for collective meditation where anyone can create a **Meditation Hall**, schedule a sitting, and invite others to meditate together.

The platform intentionally does **not** provide participant audio, participant video, or live conversation during meditation.

Instead, it creates a feeling of collective human presence through:

- A shared meditation start time
- A shared authoritative timer
- Optional hall-controlled meditation audio
- A visual representation of people currently sitting
- Optional participant sitting snapshots, otherwise avatars
- Individual meditation history
- Hall-level attendance/history
- A support system connecting regular practitioners with people who request support

The product is not intended to become a conventional social network.

### 1.2 Product thesis

> The fundamental unit of the platform is not communication. It is presence.

The product should create the feeling:

> "I am not meditating alone."

while preserving the silence and inward focus of meditation.

---

# 2. Product Goals

## 2.1 Primary goals

1. Make it extremely easy to find and join a meditation sitting.
2. Allow anyone to create a meditation hall.
3. Create a strong sense of collective presence without participant audio/video.
4. Synchronize all participants to the same meditation timeline.
5. Build a reliable personal meditation record.
6. Build a useful hall/community record.
7. Detect declining consistency and offer support without publicly shaming users.
8. Connect people requesting support with willing regular practitioners.
9. Enable private, silent support sittings.
10. Keep the experience quiet, focused, lightweight, and privacy-conscious.

## 2.2 Non-goals

The initial product should not attempt to become:

- A video-conferencing platform
- A social media platform
- A messaging platform
- A public discussion forum
- A meditation-content marketplace
- A meditation-teacher certification platform
- A live-streaming platform
- A competitive leaderboard
- A streak/gamification product

---

# 3. Core Product Principles

## 3.1 Presence over communication

The platform should communicate:

- Who is here
- Who is sitting
- How many people are sitting
- Whether someone is consistently showing up
- Whether someone needs support

It should not encourage:

- Conversation during meditation
- Social reactions
- Likes
- Comments
- Attention-seeking
- Competitive behavior

## 3.2 Silence is a product feature

During an active meditation:

- No participant microphone
- No participant audio
- No participant video
- No chat
- No reactions
- No social notifications
- No distracting animations

Only the hall's configured meditation audio may play.

## 3.3 Collective action, individual practice

Every participant is meditating individually, but the platform makes the collective activity visible.

The experience should balance:

> "This is my meditation"

with:

> "Other people are doing this with me."

## 3.4 Consistency is for support, not status

Attendance data should not be primarily used to rank people.

A regular practitioner should become a potential source of support.

A struggling practitioner should be offered support privately.

## 3.5 User control over visibility

A user controls whether their real sitting snapshot or avatar is displayed.

A user should never be forced to expose a personal photograph.

---

# 4. Core User Journeys

## 4.1 Discover and join

1. User opens the app.
2. User sees currently active and upcoming meditation halls.
3. User browses/searches halls.
4. User opens a hall.
5. User sees:
   - Name
   - Description
   - Schedule
   - Duration
   - Expected/current participants
   - Audio information
   - Hall creator
   - Hall history
6. User joins the hall.
7. The hall appears under the user's joined halls.
8. User receives a reminder 15 minutes before the next sitting.
9. User enters the meditation hall.
10. At the scheduled start:
    - Shared timer starts
    - Configured audio starts
    - Sitting state becomes active
11. User meditates.
12. At the end:
    - Audio ends
    - Timer ends
    - Session is recorded
    - Personal meditation log is updated
    - Hall session log is updated.

## 4.2 Create a hall

1. User selects "Create Hall".
2. User provides:
   - Hall name
   - Description
   - Duration
   - Date/time
   - Recurrence
   - Time zone
   - Optional audio
   - Visibility
3. User creates the hall.
4. Hall receives a unique shareable link/code.
5. Creator shares it.
6. People join.
7. The platform sends reminders.
8. Session automatically begins at the scheduled time.

## 4.3 Request support

1. User's consistency declines or the user manually requests support.
2. Platform explains privately:
   - Recent attendance pattern
   - Option to request support
3. User chooses "Request Support".
4. User specifies optional preferences:
   - Preferred sitting time
   - Duration
   - Language
   - Same hall / any hall
5. Matching system finds eligible supporters.
6. User is offered a match.
7. Both users accept.
8. A private support relationship is established.
9. A private meditation room/session can be created.

## 4.4 Become a supporter

1. User opens Support settings.
2. User enables:
   - "I'm willing to support someone."
3. User specifies:
   - Available times
   - Preferred duration
   - Maximum people supported
   - Optional language/preferences
4. Platform considers the user an eligible supporter.
5. Matching engine may propose support requests.
6. User accepts or declines.
7. Private support sessions become available.

---

# 5. Core Entities

## 5.1 User

Represents an individual account.

Fields:

- id
- displayName
- avatar
- sittingSnapshot
- timezone
- createdAt
- privacySettings
- notificationSettings
- supportSettings
- accountStatus

## 5.2 Meditation Hall

A persistent meditation community/event definition.

Fields:

- id
- creatorId
- name
- description
- visibility
- duration
- schedule
- timezone
- audioConfiguration
- participantVisibilityConfiguration
- createdAt
- status

A hall can contain many sessions.

## 5.3 Hall Membership

Represents a user's relationship with a hall.

Fields:

- hallId
- userId
- joinedAt
- status
- notificationEnabled
- displayMode

Display mode:

- snapshot
- avatar
- hidden

## 5.4 Meditation Session

A single occurrence of a hall's meditation.

Fields:

- id
- hallId
- scheduledStart
- actualStart
- scheduledEnd
- actualEnd
- status
- participantCount
- audioConfigurationSnapshot

Statuses:

- scheduled
- starting
- active
- completed
- cancelled

## 5.5 Attendance

Represents participation in a session.

Fields:

- sessionId
- userId
- joinedAt
- leftAt
- completionStatus
- attendedDuration
- expectedDuration

## 5.6 Personal Meditation Log

Derived from completed meditation sessions.

Fields:

- userId
- sessionId
- hallId
- date
- duration
- completionStatus
- source

## 5.7 Hall Log

Aggregated history for a hall.

Contains:

- Number of sessions
- Total sittings
- Unique participants
- Attendance distribution
- Regular participants
- Recent participation trends

## 5.8 Support Request

Fields:

- id
- requesterId
- createdAt
- status
- preferredSchedule
- preferredDuration
- preferences
- matchedSupporterId

Statuses:

- open
- matched
- accepted
- declined
- expired
- cancelled
- completed

## 5.9 Support Relationship

Fields:

- id
- supporterId
- supportedUserId
- createdAt
- status
- sessionCount

## 5.10 Private Support Session

A private meditation session between supporter and supported user.

Fields:

- id
- supportRelationshipId
- scheduledStart
- duration
- status
- attendance

The same silent meditation rules apply.

---

# 6. Meditation Hall

## 6.1 Hall properties

Minimum viable hall:

- Name
- Description
- Start time
- Duration
- Recurrence
- Time zone
- Audio/no audio
- Public/private visibility

Later:

- Maximum participants
- Meditation type
- Tradition
- Difficulty/experience level
- Language
- Host notes
- Custom start/end bells
- Custom audio timeline

## 6.2 Hall visibility

### Public

Discoverable by everyone.

### Unlisted

Accessible through share link/code but not discoverable.

### Private

Only invited users can join.

The initial implementation can support Public + Private and add Unlisted later.

---

# 7. Audio Architecture

## 7.1 Principle

Participants must never transmit audio.

Only the hall's configured audio is played.

## 7.2 Audio types

Initial:

- No audio
- Bell
- Uploaded audio file

Future:

- Multiple audio tracks
- Timed bell events
- Guided meditation
- Ambient sound
- Audio playlists

## 7.3 Synchronization

The server stores:

- `scheduledStart`
- `audioStartOffset`
- Audio asset/version

Clients calculate playback position from server time.

Example:

```text
serverStartTime = 06:00:00
currentServerTime = 06:17:23

audioPosition = 17:23
timerPosition = 17:23
```

A participant joining late should immediately seek to the correct position.

## 7.4 Audio ownership

The hall creator owns/configures the hall audio.

The system should maintain an explicit policy for uploaded audio rights and permitted content.

---

# 8. Shared Timer

## 8.1 Server authoritative time

The timer must not depend on local device start time.

Server state:

```text
sessionId
scheduledStart
actualStart
duration
status
```

Client:

```text
elapsed = synchronizedServerTime - actualStart
remaining = duration - elapsed
```

## 8.2 Clock synchronization

Client periodically estimates server clock offset.

A lightweight request/response mechanism is sufficient for MVP.

The system should tolerate:

- Device clock errors
- Temporary network loss
- App backgrounding
- Late joining
- Reconnection

## 8.3 Session state

Possible states:

```text
SCHEDULED
STARTING
ACTIVE
COMPLETED
CANCELLED
```

---

# 9. Human Presence

This is a core feature rather than decoration.

## 9.1 Active participant display

During a session, the hall displays:

- Current participant count
- Participant avatars/snapshots
- Optional names
- Join/leave presence

## 9.2 Display modes

Each participant chooses:

### Snapshot

A static photograph representing them while sitting.

### Avatar

System/user-selected avatar.

### Hidden

The user participates but is not visually represented.

## 9.3 Snapshot behavior

A snapshot is not live video.

The product should clearly communicate:

> "This is a snapshot representing me while sitting."

This avoids creating an expectation of live visual communication.

## 9.4 Presence animation

Presence changes should be subtle.

Examples:

- Someone joins
- Someone leaves
- Participant count changes
- A face/avatar appears briefly

No attention-grabbing effects.

---

# 10. Meditation Hall UI

The active meditation screen should be intentionally minimal.

Conceptual structure:

```text
------------------------------------------------
                Morning Vipassana

                     42:17

                 31 sitting

          🙂   👤   🧘   🙂   👤
       👤   🙂   🙂   👤   🧘

------------------------------------------------
```

Controls should be minimal:

- Leave
- Audio control if permitted
- Optional session information

No social controls during meditation.

---

# 11. Notifications

## 11.1 Required notifications

### 15-minute reminder

> Morning Vipassana starts in 15 minutes. 27 people are expected to sit with you.

### Session starting

Optional, depending on user settings.

### Hall cancellation

Required.

### Schedule change

Required.

## 11.2 Notification philosophy

Notifications should reinforce collective practice rather than create pressure.

Avoid:

- Shame
- Competitive language
- Streak loss
- Aggressive reminders

---

# 12. Attendance

## 12.1 Joining

A user is considered present when they enter the active session.

## 12.2 Leaving

Record:

- Leave timestamp
- Duration attended

## 12.3 Completion

A session can have configurable completion thresholds.

For example:

```text
completed if attended >= 90% of expected duration
```

The exact threshold should be configurable and evaluated after real-world usage.

## 12.4 Late joining

Late joining is allowed.

Example:

```text
Session: 60 min
User joins at +12:37

Timer shows:
47:23 remaining
```

---

# 13. Personal Meditation Log

The personal log should provide a factual record, not a gamified score.

Example:

```text
September 2026

Sep 1   Morning Vipassana     60 min   ✓
Sep 2   Morning Vipassana     60 min   ✓
Sep 3   Morning Vipassana     18 min   Partial
Sep 4   —                     —        —
Sep 5   Evening Sit            60 min   ✓
```

Useful aggregate information:

- Total sessions
- Total meditation time
- Current consistency
- Historical consistency
- Sessions by hall
- Sessions by duration
- Calendar view

---

# 14. Hall Log

The hall should have its own history.

Example:

```text
Morning Vipassana

Sessions held: 184
Total sittings: 3,821
Unique practitioners: 117

Regular practitioners

Nitin       164 sessions
Prashant    151 sessions
Rahul       143 sessions

Current session
31 people sitting
```

## 14.1 Privacy

Hall logs should not expose sensitive behavioral information unnecessarily.

Do not publicly label someone:

- "Struggling"
- "Inactive"
- "Failing"
- "Unreliable"

Instead, use attendance information only where it provides genuine community value.

---

# 15. Consistency & Support

## 15.1 Purpose

Consistency analytics exist primarily to:

1. Help users understand their own practice.
2. Identify when someone may benefit from support.
3. Identify regular practitioners who may be able to support others.

## 15.2 Detecting struggle

The system may consider:

- Recent missed sessions
- Declining attendance
- Reduced completion
- Long absence after regular participation
- Self-declared difficulty

Do not expose a binary "struggling" label.

## 15.3 Support invitation

Example:

> You've missed several recent sittings.
>
> If maintaining your practice has become difficult, you can ask someone from the community to sit with you.
>
> [Request Support]

The user can always ignore this.

## 15.4 Manual support request

Users should also be able to request support regardless of automated detection.

---

# 16. Supporter System

## 16.1 Becoming a supporter

User enables:

> "I'm willing to support another practitioner."

Configuration:

- Available days
- Available time windows
- Typical duration
- Maximum simultaneous support relationships
- Language
- Preferred hall/community
- Whether matching can occur outside their own halls

## 16.2 Matching

Initial matching priority:

1. Same hall
2. Compatible schedule
3. Compatible duration
4. Compatible timezone
5. Language
6. Supporter availability
7. Existing support load

Later, matching can incorporate additional signals.

## 16.3 Support relationship

A support relationship should not automatically imply:

- Messaging
- Friendship
- Following
- Social visibility

Its primary purpose is:

> "We sit together."

---

# 17. Private Support Meditation Hall

A private support room is structurally similar to a normal hall but has exactly two participants initially.

Properties:

- Supporter
- Supported user
- Start time
- Duration
- Optional shared audio
- Shared timer
- Presence snapshots/avatars

Rules:

- No participant audio
- No participant video
- No live chat during sitting

Communication outside the meditation experience can be added later if necessary.

---

# 18. Realtime Architecture

The product does not require WebRTC for the MVP.

Core realtime requirements are:

- Presence
- Session state
- Server time
- Join/leave events
- Timer synchronization

A WebSocket connection is sufficient.

Conceptual architecture:

```text
                 Mobile App
                     |
              HTTPS + WebSocket
                     |
              Backend API
                     |
          +----------+----------+
          |                     |
       Database          Realtime Service
          |                     |
          |              Session Presence
          |              Timer State
          |              Join/Leave
          |
       Object Storage
          |
       Audio / Photos
```

---

# 19. Suggested Technology Stack

Because the existing Vipassana Timer application is Android/Kotlin-based, the first version can reuse the engineering knowledge and potentially shared components from that codebase while remaining a separate product.

## 19.1 Mobile

- Kotlin
- Jetpack Compose
- Android-first MVP

## 19.2 Backend

Reasonable choices:

- Kotlin + Spring Boot
- Kotlin/Ktor
- Node.js/TypeScript

The backend should expose:

- REST APIs
- WebSocket realtime API

## 19.3 Database

PostgreSQL.

## 19.4 Realtime

WebSocket-based service.

Possible implementation:

- Backend-managed WebSockets initially
- Redis/pub-sub when horizontal scaling becomes necessary

## 19.5 Object storage

S3-compatible storage for:

- User snapshots
- Avatars if stored remotely
- Hall audio

## 19.6 Push notifications

Firebase Cloud Messaging.

## 19.7 Authentication

Initial options:

- Google Sign-In
- Email/password or magic link
- Phone authentication later if needed

Avoid requiring excessive profile information.

---

# 20. Backend API Concept

Example endpoints:

```text
POST   /auth/...
GET    /halls
POST   /halls
GET    /halls/{hallId}
PATCH  /halls/{hallId}
DELETE /halls/{hallId}

POST   /halls/{hallId}/join
DELETE /halls/{hallId}/leave

GET    /halls/{hallId}/sessions
GET    /sessions/{sessionId}
POST   /sessions/{sessionId}/join
POST   /sessions/{sessionId}/leave

GET    /me/meditation-log
GET    /me/stats

POST   /support/request
GET    /support/request
POST   /support/request/{id}/cancel

POST   /supporter/availability
GET    /support/matches
POST   /support/matches/{id}/accept
POST   /support/matches/{id}/decline

GET    /support/relationships
POST   /support/relationships/{id}/sessions
```

WebSocket events:

```text
SESSION_STATE
PARTICIPANT_JOINED
PARTICIPANT_LEFT
PARTICIPANT_COUNT_CHANGED
SESSION_STARTED
SESSION_COMPLETED
SERVER_TIME
```

---

# 21. Data Model

Simplified relational model:

```text
users
  |
  +---- hall_memberships ---- halls
  |                              |
  |                              +---- hall_schedules
  |                              |
  |                              +---- meditation_sessions
  |                                         |
  |                                         +---- attendances
  |
  +---- meditation_logs
  |
  +---- support_requests
  |
  +---- supporter_profiles
                 |
                 +---- support_relationships
                              |
                              +---- support_sessions
```

Important design principle:

**Attendance should be event-level data.**

Aggregate statistics should be derived rather than treated as the source of truth.

---

# 22. Security & Privacy

## 22.1 Minimum personal data

Only collect information required for:

- Account
- Hall participation
- Notifications
- Meditation history
- Support matching

## 22.2 Photos

Users explicitly control whether their snapshot is displayed.

## 22.3 Hall visibility

Private halls must not expose participant information to non-members.

## 22.4 Support privacy

Support relationships should not become publicly searchable.

## 22.5 Audio

Audio assets should have controlled access when associated with private halls.

---

# 23. Failure Handling

The experience must continue gracefully when network connectivity is imperfect.

## 23.1 Temporary network loss

During active meditation:

- Timer continues locally using last synchronized server time.
- Audio continues.
- UI continues.
- Reconnect in background.

## 23.2 Reconnection

On reconnect:

1. Synchronize server time.
2. Retrieve session state.
3. Correct timer position if necessary.
4. Reconcile attendance.

## 23.3 App backgrounding

The app should continue the meditation session appropriately within Android background/audio constraints.

The user should not lose the meditation record simply because the application UI was backgrounded.

---

# 24. Hall Lifecycle

```text
CREATE
  |
  v
SCHEDULED
  |
  v
STARTING
  |
  v
ACTIVE
  |
  v
COMPLETED
```

Cancellation:

```text
SCHEDULED -> CANCELLED
STARTING  -> CANCELLED
```

A recurring hall generates separate session records for every occurrence.

---

# 25. Recurring Sessions

A hall may have:

- One-time session
- Daily
- Weekly
- Selected weekdays

Example:

```text
Morning Vipassana
Every day
06:00
60 minutes
Asia/Kolkata
```

The schedule should be stored independently from individual sessions.

This makes it possible to change future schedules without rewriting historical records.

---

# 26. Discovery

The home screen should primarily answer:

> "Where can I sit?"

Possible sections:

### Sitting now

Halls currently active.

### Starting soon

Halls starting within the next few hours.

### My halls

Halls the user has joined.

### Recommended

Potential future feature based on:

- Schedule
- Duration
- Previous participation
- Location/timezone
- Meditation type

Avoid algorithmic engagement optimization.

The recommendation objective is **finding a suitable sitting**, not maximizing app usage.

---

# 27. Sharing

Every public/unlisted hall should have a shareable link.

Example conceptual link:

```text
meditation.app/h/morning-vipassana-abc123
```

Opening the link:

1. Opens app if installed.
2. Otherwise opens web landing page.
3. User can join after authentication.

---

# 28. MVP Scope

The first production version should be substantially smaller than the complete vision.

## MVP includes

### Account

- Sign in
- Basic profile
- Avatar
- Snapshot
- Privacy setting

### Halls

- Create hall
- Public/private hall
- Description
- Schedule
- Duration
- Join/leave
- Share link

### Sessions

- Scheduled sessions
- 15-minute reminder
- Shared server-authoritative timer
- Participant count
- Participant avatars/snapshots
- Join/leave presence
- Session completion

### Audio

- No audio
- One hall-controlled audio file
- Synchronized playback

### Logs

- Personal meditation history
- Basic hall attendance history
- Session statistics

### Support

- Opt in as supporter
- Request support
- Basic matching
- Private support session

---

# 29. Explicitly Defer From MVP

Do not build initially:

- Messaging
- Comments
- Likes
- Followers
- Public social profiles
- Leaderboards
- Achievements
- Gamification
- Complex recommendation engine
- AI meditation coach
- Live video
- Live participant audio
- Screen sharing
- Recording
- Web client as a full-featured client
- Complex mentor hierarchy
- Paid subscriptions
- Marketplace
- Advanced analytics

---

# 30. Important Product Experiments

Before investing heavily in infrastructure, validate these assumptions.

## Experiment 1 — Does presence matter?

Compare:

A. Shared timer + participant count

versus

B. Shared timer + participant faces/avatars

Measure:

- Session completion
- Repeat attendance
- User-reported feeling of connectedness

## Experiment 2 — Snapshot vs avatar

Test whether real human snapshots materially increase the feeling of collective presence.

## Experiment 3 — Support

Test:

> "Would you like someone to sit with you?"

Measure acceptance rate.

## Experiment 4 — Regular practitioner willingness

Measure:

> What percentage of highly consistent users are willing to sit with someone who is struggling?

This determines whether the support marketplace has enough supply.

## Experiment 5 — Hall creation

Measure whether ordinary practitioners—not only the founder—create halls.

If users naturally create halls, the platform has a potentially strong network effect.

---

# 31. Metrics

Avoid maximizing conventional engagement.

Primary metrics:

### Collective meditation

- Sessions started
- Total meditation minutes
- Unique practitioners
- Average participants/session
- Session completion rate

### Retention

- 7-day return
- 30-day return
- Repeat hall participation
- Number of active halls

### Community health

- Percentage of users participating consistently
- Percentage willing to support
- Support requests
- Successful matches
- Support sessions completed

### Product quality

- Session synchronization failures
- Audio synchronization failures
- Notification delivery
- Reconnection success
- Crash-free sessions

---

# 32. Network Effects

The platform has two potentially reinforcing loops.

## Hall loop

```text
Create Hall
    ↓
Invite people
    ↓
People sit together
    ↓
Hall becomes regular
    ↓
More people discover it
    ↓
More people join
```

## Support loop

```text
Regular practitioner
        ↓
Offers support
        ↓
Supports struggling practitioner
        ↓
Struggling practitioner becomes regular
        ↓
Eventually becomes supporter
        ↓
Community support capacity increases
```

The second loop is particularly interesting because **the people helped by the system can eventually become contributors to the system.**

---

# 33. Potential Future Features

Only after the core experience works.

## 33.1 Hall reputation

Not star ratings.

Instead:

- Number of sessions successfully conducted
- Number of regular participants
- Community longevity

## 33.2 Hall hosts

Hosts can maintain recurring halls.

## 33.3 Multiple meditation traditions

The platform should remain technically neutral.

Possible hall categories:

- Vipassana
- Breath meditation
- Mindfulness
- Samatha
- Zen
- Prayer/quiet sitting
- Silent sitting
- Custom practice

The platform provides infrastructure, not doctrine.

## 33.4 Local communities

Optional location-based discovery without requiring precise location.

## 33.5 Time-zone communities

Useful for global halls.

## 33.6 Offline resilience

A user should be able to continue a sitting through temporary connectivity loss.

## 33.7 Calendar integration

Allow users to add recurring halls to their calendar.

---

# 34. Architectural Relationship With the Vipassana Timer

The existing Vipassana Timer application should be treated as a **source of reusable technology and meditation UX knowledge**, not as the product itself.

The new application should be a separate product with:

- Separate application identity
- Separate backend
- Separate data model
- Shared libraries where useful
- Shared timer implementation where technically appropriate

Potential shared modules:

```text
MeditationCore
 ├── Timer
 ├── Bell
 ├── Audio playback
 ├── Meditation session model
 └── Meditation statistics

MeditationHallApp
 ├── Hall
 ├── Sessions
 ├── Presence
 ├── Social discovery
 ├── Support
 └── Community logs
```

This avoids coupling the two products while allowing proven meditation functionality to be reused.

---

# 35. Recommended Development Sequence

## Phase 1 — Core engine

Build:

- Authentication
- User profile
- Hall creation
- Hall discovery
- Hall membership
- Session scheduling
- Shared timer
- Presence
- Attendance
- Personal log

## Phase 2 — Human presence

Add:

- Snapshots
- Avatars
- Display preferences
- Presence animations
- Hall participant visualization

## Phase 3 — Audio

Add:

- Audio upload
- Audio storage
- Synchronized playback
- Start/end bells
- Audio permissions/configuration

## Phase 4 — Community

Add:

- Hall history
- Regular participant information
- Community statistics
- Sharing
- Better discovery

## Phase 5 — Support

Add:

- Supporter profile
- Support request
- Matching
- Private support rooms
- Support session history

This sequencing ensures that the product is already useful before the most socially complex feature is introduced.

---

# 36. Core UX Philosophy

The app should feel:

- Calm
- Human
- Quiet
- Reliable
- Simple
- Non-competitive
- Non-addictive
- Community-oriented

The most important moments are:

### Before meditation

> "People are gathering."

### During meditation

> "People are sitting with me."

### After meditation

> "I showed up."

### When struggling

> "Someone can sit with me."

### When becoming regular

> "I can sit with someone else."

That is the complete product loop.

---

# 37. Definition of Success

The product succeeds if a user can say:

> "I normally find it difficult to meditate alone. When I open the app and see 20–30 other people sitting, I feel like I'm part of something. I don't need to talk to them. I just sit."

And a second user can say:

> "I've been practicing regularly. When someone struggled, I was able to sit with them. I didn't need to teach them anything. I simply showed up."

That is the product.


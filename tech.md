# Meditation Hall Platform — Technical Design & Implementation

**Status:** Proposed  
**Client:** Android-first  
**Mobile:** Kotlin + Jetpack Compose  
**Backend:** Kotlin + Ktor  
**Infrastructure:** Oracle Cloud Infrastructure (OCI)  
**Database:** PostgreSQL  
**Realtime:** WebSocket  
**Cache / ephemeral state:** Redis  
**Push notifications:** Firebase Cloud Messaging (FCM)  
**Object storage:** OCI Object Storage initially  
**Deployment:** Docker on OCI

## 1. Purpose

This document defines the technical architecture and implementation requirements for the Meditation Hall platform.

The application enables people to discover and create meditation halls, join scheduled sessions, receive reminders, meditate together using a synchronized timer, listen to optional hall-controlled audio, see other participants through snapshots or avatars, maintain individual and hall histories, request/offer support, and conduct private silent support sessions.

The application deliberately does not provide participant audio, participant video, or live conversation during meditation.

## 2. Infrastructure Decision

The initial product uses Oracle Cloud Infrastructure for application/backend hosting, PostgreSQL for durable data, Redis for realtime/ephemeral state and coordination, OCI Object Storage for audio and snapshots, Firebase Cloud Messaging for Android push notifications, Firebase Crashlytics for Android crash reporting, and Docker for deployment.

OCI currently documents Always Free compute resources including an Ampere A1 allocation equivalent to 2 OCPUs and 12 GB RAM, subject to current account/region limits. OCI also documents Always Free storage and networking resources. This is suitable for the initial small-scale deployment.

## 3. Architecture

```text
                         Android App
                              |
              +---------------+---------------+
              |                               |
            HTTPS                          WebSocket
              |                               |
              +---------------+---------------+
                              |
                     Oracle Cloud VM
                              |
                     Kotlin / Ktor API
                              |
       +----------------------+----------------------+
       |                      |                      |
   PostgreSQL              Redis              OCI Object Storage
       |                      |                      |
 durable data         presence/cache          audio/photos
       |
 background scheduler
       |
       +---------------------> Firebase FCM
                              |
                              v
                         Android devices
```

Firebase is deliberately used selectively rather than as the application backend.

## 4. OCI Deployment

Initial deployment:

```text
Docker
├── reverse-proxy
├── meditation-api
├── meditation-worker
├── postgresql
└── redis
```

OCI Object Storage remains outside the VM for media.

Start with one VM and one modular application deployment. Do not introduce Kubernetes, microservices, Kafka, or service mesh.

PostgreSQL and Redis must never be exposed directly to the public internet.

## 5. Android Stack

- Kotlin
- Jetpack Compose
- Coroutines / Flow
- ViewModel
- Navigation
- Room
- DataStore
- WorkManager
- Media3
- Firebase Cloud Messaging
- Firebase Crashlytics

Suggested structure:

```text
app/
  core/
    common/
    network/
    database/
    auth/
    media/
    notifications/
    time/

  domain/
    user/
    hall/
    session/
    attendance/
    meditation/
    support/

  feature/
    home/
    discover/
    hall/
    createhall/
    session/
    meditationlog/
    support/
    profile/
    settings/
```

## 6. Backend Stack

Use a modular monolith with Kotlin/Ktor.

```text
backend/
  auth/
  users/
  halls/
  schedules/
  sessions/
  attendance/
  realtime/
  audio/
  notifications/
  meditation/
  support/
  scheduler/
  infrastructure/
```

HTTP routes should not access the database directly. Use services and repositories.

## 7. Core Entities

```text
User
Hall
HallMembership
HallSchedule
MeditationSession
Attendance
MeditationLog
SupportProfile
SupportRequest
SupportMatch
SupportRelationship
SupportSession
AudioAsset
UserDevice
Notification
```

## 8. User

```text
User
----
id: UUID
displayName: String
avatarUrl: String?
snapshotUrl: String?
timezone: String
createdAt: Instant
updatedAt: Instant
status: ACTIVE | SUSPENDED | DELETED
```

Authentication can initially use Google Sign-In and/or email authentication. The Ktor backend remains the application's authorization authority.

## 9. Hall

```text
Hall
----
id: UUID
creatorId: UUID
name: String
description: String
visibility: PUBLIC | PRIVATE | UNLISTED
durationSeconds: Int
timezone: String
status: ACTIVE | PAUSED | ARCHIVED
createdAt: Instant
updatedAt: Instant
```

Schedules are separate:

```text
HallSchedule
------------
id
hallId
scheduleType
startLocalTime
timezone
daysOfWeek
startDate
endDate
enabled
```

Schedule types:

```text
ONCE
DAILY
WEEKLY
WEEKDAYS
CUSTOM
```

## 10. Meditation Session

A Hall is the recurring concept. A Session is one actual sitting.

```text
MeditationSession
-----------------
id
hallId
scheduledStart
actualStart
scheduledEnd
actualEnd
durationSeconds
status
createdAt
```

Status:

```text
SCHEDULED
STARTING
ACTIVE
COMPLETED
CANCELLED
```

Historical sessions are immutable after completion except for explicit administrative correction.

## 11. Session State

```text
              SCHEDULED
                  |
                  v
              STARTING
                  |
                  v
                ACTIVE
               /                    /                     v          v
         COMPLETED    CANCELLED
```

Only the backend changes session state.

## 12. Timer Synchronization

Never send timer ticks from the server.

The server provides:

```text
actualStart
duration
server time
```

The client calculates:

```text
elapsed = synchronizedServerTime - actualStart
remaining = duration - elapsed
```

Provide:

```text
GET /v1/time
```

and estimate clock offset from request/response timing.

Late joining is therefore trivial: a user joining 13 minutes into a 60-minute session immediately sees approximately 47 minutes remaining.

## 13. Realtime

Use a WebSocket endpoint:

```text
wss://api.example.com/v1/realtime
```

Events:

```text
SESSION_STATE
SESSION_STARTED
SESSION_COMPLETED
PARTICIPANT_JOINED
PARTICIPANT_LEFT
PARTICIPANT_UPDATED
```

Do not use WebSockets for timer ticks.

## 14. Presence

Presence is ephemeral and belongs in Redis.

Example:

```text
session:{sessionId}:participants
```

with TTLs.

PostgreSQL stores durable attendance.

Clients send a heartbeat approximately every 30–60 seconds. Use a grace period before treating a disconnected client as absent.

## 15. Joining

```text
POST /v1/sessions/{sessionId}/join
```

The server:

1. Authenticates.
2. Verifies access.
3. Verifies session.
4. Creates/updates attendance.
5. Adds realtime presence.
6. Returns authoritative session state.

## 16. Leaving

```text
POST /v1/sessions/{sessionId}/leave
```

Store server-side leave time and broadcast `PARTICIPANT_LEFT`.

If the application crashes, reconcile attendance from heartbeat/reconnection/session-end information.

## 17. Attendance

```text
Attendance
----------
id
sessionId
userId
joinedAt
leftAt
lastSeenAt
attendedDurationSeconds
completionStatus
createdAt
updatedAt
```

Statuses:

```text
COMPLETED
PARTIAL
NO_SHOW
UNKNOWN
```

Initial completion threshold can be 90% of expected duration, but this must remain configurable.

## 18. Audio

Hall audio configuration:

```text
HallAudio
---------
type: NONE | FILE | BELL
assetId
startOffsetSeconds
```

Use OCI Object Storage.

Upload flow:

```text
Android
   |
   | request signed upload URL
   v
Backend
   |
   v
OCI Object Storage
```

The backend should not proxy large media uploads.

Cache audio locally before or during the session.

Playback position is derived from session time:

```text
audioPosition = currentTime - session.actualStart
```

A future audio timeline can support start bells, guided audio, silence and end bells.

## 19. Human Presence

Participant representation:

```text
SNAPSHOT
AVATAR
HIDDEN
```

Snapshots are static images, never video.

Default should be avatar. Users explicitly opt into snapshot display.

Use signed/short-lived media URLs for private media.

## 20. Hall Discovery

```text
GET /v1/halls
```

Initial sections:

```text
Sitting Now
Starting Soon
My Halls
```

Do not build recommendation infrastructure initially.

## 21. Sharing

Use a stable short share code:

```text
https://meditation.app/h/{shareCode}
```

Support Android App Links.

## 22. Notifications

Use Firebase Cloud Messaging for:

- 15-minute meditation reminders
- Hall cancellation
- Schedule changes
- Support matches
- Private support-session reminders

FCM provides the Android client integration and server-side messaging infrastructure.

Store device registrations:

```text
UserDevice
----------
id
userId
fcmToken
platform
appVersion
lastSeenAt
enabled
```

Register/update tokens on app startup/login and token refresh.

## 23. Notification Scheduler

A backend worker runs periodically:

```text
Every minute
   ↓
Find sessions starting in ~15 minutes
   ↓
Find subscribed participants
   ↓
Send FCM
```

Use idempotency keys such as:

```text
notification:{sessionId}:{userId}:15min
```

to prevent duplicate sends.

## 24. Personal Meditation Log

```text
MeditationLog
-------------
id
userId
sessionId
hallId
date
durationSeconds
completionStatus
createdAt
```

Attendance is the source of truth.

## 25. Hall Statistics

Initial statistics:

```text
sessionCount
totalAttendance
uniqueParticipants
totalMeditationSeconds
```

Calculate directly from PostgreSQL initially; introduce aggregates only when necessary.

## 26. Consistency

Expose transparent metrics:

```text
sessionsExpected
sessionsAttended
sessionsCompleted
recentAttendanceRate
longestGap
currentParticipationTrend
```

Do not create a mysterious single meditation score.

## 27. Support

The system can privately detect:

- Attendance decline
- Consecutive missed sessions
- Long gaps after regular practice
- Manual support requests

Never publicly label someone as struggling.

A support request contains:

```text
preferredDuration
preferredDays
preferredTime
timezone
language
```

## 28. Supporter

```text
SupporterProfile
----------------
userId
enabled
availableDays
availableTimeWindows
preferredDuration
maxActiveRelationships
timezone
language
updatedAt
```

A user explicitly opts in.

## 29. Matching

Initial deterministic matching:

```text
1. Same hall
2. Compatible schedule
3. Compatible duration
4. Timezone
5. Language
6. Supporter capacity
7. Existing relationship constraints
```

Do not use ML for MVP.

## 30. Private Support Session

Reuse the normal meditation session infrastructure.

Constraints:

```text
maxParticipants = 2
visibility = PRIVATE
```

Reuse timer, audio, presence, attendance and notifications.

## 31. Database

PostgreSQL tables:

```text
users
user_devices
user_preferences

halls
hall_memberships
hall_schedules
hall_audio
audio_assets

meditation_sessions
attendance
meditation_logs

supporter_profiles
support_requests
support_matches
support_relationships
support_sessions

notifications
notification_deliveries
```

Important indexes:

```sql
CREATE INDEX idx_sessions_hall_start
ON meditation_sessions(hall_id, scheduled_start);

CREATE INDEX idx_sessions_start_status
ON meditation_sessions(scheduled_start, status);

CREATE INDEX idx_memberships_user
ON hall_memberships(user_id);

CREATE INDEX idx_memberships_hall
ON hall_memberships(hall_id);

CREATE INDEX idx_attendance_user
ON attendance(user_id);

CREATE INDEX idx_attendance_session
ON attendance(session_id);

CREATE INDEX idx_support_requests_status
ON support_requests(status);
```

## 32. API

Version all APIs:

```text
/v1/...
```

Representative endpoints:

```text
POST   /v1/auth/...
GET    /v1/time

GET    /v1/halls
POST   /v1/halls
GET    /v1/halls/{id}
PATCH  /v1/halls/{id}
POST   /v1/halls/{id}/join
DELETE /v1/halls/{id}/leave

GET    /v1/sessions/{id}
POST   /v1/sessions/{id}/join
POST   /v1/sessions/{id}/leave

GET    /v1/me/meditation-log
GET    /v1/me/stats

POST   /v1/support/requests
GET    /v1/support/requests
POST   /v1/support/requests/{id}/cancel

POST   /v1/supporter/profile
GET    /v1/support/matches
POST   /v1/support/matches/{id}/accept
POST   /v1/support/matches/{id}/decline

GET    /v1/support/relationships
POST   /v1/support/relationships/{id}/sessions
```

Use a consistent error model:

```json
{
  "error": {
    "code": "HALL_ACCESS_DENIED",
    "message": "You do not have access to this hall.",
    "requestId": "..."
  }
}
```

## 33. Idempotency

Important mutation operations must tolerate retries:

```text
create hall
join session
leave session
accept support match
send notification
```

Use client-provided idempotency keys where necessary.

## 34. Offline Behavior

If the network disappears during meditation:

1. Continue timer.
2. Continue audio.
3. Continue UI.
4. Reconnect silently.
5. Reconcile attendance later.

Never turn a temporary network failure into a disruptive meditation error.

## 35. Room / Local Cache

Cache:

```text
joined halls
upcoming sessions
current session
audio metadata
user profile
recent meditation log
```

Server remains authoritative.

## 36. Client Session Engine

Create a reusable:

```text
SessionEngine
-------------
start()
sync()
getElapsed()
getRemaining()
handleSessionStarted()
handleSessionCompleted()
```

Keep it independent of Compose.

This is a strong candidate for reuse from the existing Vipassana Timer codebase.

## 37. Audio Engine

Create:

```text
MeditationAudioPlayer
```

with:

```text
prepare(asset)
playAt(position)
pause()
resume()
seek()
stop()
```

Use Android Media3.

## 38. Security

Authentication:

- HTTPS only
- Secure token storage
- Token rotation
- Refresh-token revocation

Authorization:

```text
authenticated user
        ↓
resource
        ↓
permission
```

Never trust client-provided ownership, user IDs, attendance duration, or support permissions.

Storage:

- Signed URLs
- Access-controlled downloads
- Rate-limited uploads

Never expose PostgreSQL or Redis publicly.

## 39. Abuse Prevention

MVP:

- Report hall
- Report user
- Block user
- Remove participant from owned/private hall
- Admin moderation
- Rate limiting

## 40. Observability

Backend:

```text
requestId
userId
endpoint
duration
status
```

Metrics:

```text
API latency
API errors
WebSocket connections
WebSocket reconnects
session starts
session completion
attendance reconciliation
FCM failures
audio upload failures
support match success
```

Android:

- Firebase Crashlytics
- Technical session/audio/network events where useful

Avoid unnecessary behavioral analytics.

## 41. Scheduler

Do not rely on in-memory JVM timers.

Use a worker backed by PostgreSQL and Redis coordination.

Responsibilities:

```text
Generate sessions
Start sessions
Complete sessions
Send reminders
```

Jobs must be idempotent.

## 42. Recurring Sessions

Generate concrete session records for roughly the next 30 days.

This simplifies:

- Discovery
- Notifications
- Cancellation
- Attendance
- History

Schedules remain the source of future recurrence.

## 43. Time Zones

Store Hall schedules using IANA timezone IDs:

```text
Asia/Kolkata
America/New_York
Europe/London
```

Store concrete timestamps in UTC.

Recurrence calculations must use the Hall's local timezone.

## 44. Backups

PostgreSQL requires:

- Automated daily backup
- Retention policy
- Off-machine copy
- Periodic restore test

A backup is not considered valid until restoration has been tested.

## 45. Performance Targets

Initial targets:

```text
API p50 < 150ms
API p95 < 500ms
Session join < 2 seconds
Realtime presence propagation < 1 second
```

The timer must remain smooth during temporary disconnection.

## 46. Scalability

Start with:

```text
One OCI VM
One API
One worker
One PostgreSQL
One Redis
```

Scale later to multiple API instances behind a load balancer if required.

Redis becomes the shared realtime/cache layer.

The application should remain stateless apart from PostgreSQL, Redis and Object Storage.

## 47. Large Halls

Initial design should support normal groups.

Potential future strategy:

```text
1–100
show participants

100–500
participant grid + count

500+
count + sampled presence
```

Do not prematurely optimize for massive concurrency.

## 48. CI/CD

CI:

```text
format
lint
unit tests
integration tests
Android build
backend build
Docker build
```

Environments:

```text
development
staging
production
```

Never use production data in development.

## 49. Infrastructure as Code

Use Terraform for OCI resources where practical:

- Compute
- Networking
- Security rules
- Object Storage
- DNS
- Backups/monitoring where applicable

## 50. Reuse From Vipassana Timer

The new application remains a separate product.

Evaluate the existing app for reuse of:

```text
Meditation timer engine
Audio playback
Bell system
Session duration model
Meditation completion logic
Local persistence
Statistics
Meditation UX components
```

Potential shared module:

```text
MeditationCore
       |
       +---- Vipassana Timer
       |
       +---- Meditation Hall
```

Do not force Vipassana-specific assumptions into the generic core.

## 51. Repository Structure

```text
meditation-platform/

  android/
    app/
    core/
    feature/

  backend/
    auth/
    users/
    halls/
    sessions/
    realtime/
    attendance/
    audio/
    notifications/
    support/
    scheduler/

  infrastructure/
    docker/
    terraform/
    deployment/

  docs/
    design.md
    tech.md
    api.md
```

## 52. Explicitly Out of Scope

Do not build initially:

- WebRTC
- Participant video
- Participant audio
- Screen sharing
- Recording
- Blockchain
- Decentralized identity
- Federation
- P2P networking
- Microservices
- Kubernetes
- Kafka
- ML matching
- Social feed
- Likes
- Comments
- Followers
- Leaderboards
- Complex recommendation engine

## 53. Critical Architecture Decision

```text
                  SERVER
                     |
             authoritative
             session timeline
                     |
          +----------+----------+
          |                     |
       CLIENT A              CLIENT B
          |                     |
     local timer            local timer
     local audio            local audio
     local UI               local UI
```

The server determines when the meditation starts. Each client determines what to render at that point in time.

## 54. Final Technology Stack

### Android

```text
Kotlin
Jetpack Compose
Coroutines / Flow
Room
DataStore
Media3
WorkManager
Firebase Cloud Messaging
Firebase Crashlytics
```

### Backend

```text
Kotlin
Ktor
WebSocket
PostgreSQL
Redis
```

### OCI

```text
OCI Compute
OCI Object Storage
OCI Networking
OCI DNS
OCI Monitoring
Terraform
Docker
```

### Firebase

```text
Firebase Cloud Messaging
Firebase Crashlytics
```

Firebase is deliberately limited to services where it provides clear value. The application's authoritative data remains on the Oracle-hosted backend.

## 55. MVP Development Sequence

### Milestone 1 — Foundation

- Android project
- Ktor backend
- OCI deployment
- PostgreSQL
- Redis
- Authentication
- CI/CD

### Milestone 2 — Halls

- Hall creation
- Hall discovery
- Hall details
- Join/leave
- Membership
- Share links

### Milestone 3 — Sessions

- Schedule engine
- Session generation
- Session state machine
- Server time
- Shared timer
- Attendance

### Milestone 4 — Realtime

- WebSocket
- Session subscription
- Participant presence
- Join/leave
- Reconnection

### Milestone 5 — Audio

- OCI Object Storage
- Audio upload
- Media3 playback
- Synchronization
- Local caching

### Milestone 6 — Human presence

- Avatar
- Snapshot
- Display mode
- Participant visualization

### Milestone 7 — Logs

- Personal history
- Hall history
- Statistics

### Milestone 8 — Notifications

- FCM
- Device registration
- 15-minute reminders
- Cancellation
- Schedule changes

### Milestone 9 — Support

- Supporter profile
- Support request
- Matching
- Private support session

## 56. Definition of Done

The MVP is complete when:

- Users can authenticate.
- Users can create and share halls.
- Users can discover and join halls.
- Recurring schedules generate sessions.
- Participants receive 15-minute reminders.
- Sessions start automatically.
- Connected clients display the same meditation timeline.
- Late joiners see the correct remaining time.
- Hall audio starts at the correct position.
- Participants can use snapshot/avatar/hidden modes.
- Presence updates in realtime.
- Temporary network loss does not destroy the meditation experience.
- Attendance is recorded using server timestamps.
- Personal meditation history is generated.
- Hall history is generated.
- Users can opt into support.
- Users can request support.
- Matching produces valid matches.
- Private support sessions work using the same meditation engine.
- Authorization prevents unauthorized access.
- Critical APIs are idempotent.
- PostgreSQL is backed up and restore-tested.
- Android crashes are observable.
- Backend failures are observable.
- Automated tests cover scheduling, timer, attendance, realtime and support matching.
- Production deployment is repeatable.

## 57. Final Recommendation

Build the first version as a **single modular monolith on Oracle Cloud**, backed by PostgreSQL and Redis.

Use Firebase only for:

- Push notifications
- Crash reporting

Use OCI for:

- Compute
- Backend
- PostgreSQL
- Redis
- Object Storage
- Networking

This gives the product a simple, inexpensive and controllable infrastructure while keeping the Android application independent of any backend vendor.

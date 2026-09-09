# Meditation Sangha

**Meditation Sangha** is a separate product from [Vipassana Timer](https://play.google.com/store/apps/details?id=com.digital.sanghaworld). It is not a rebrand of that timer, not an official Vipassana organization app, and not affiliated with any center or teacher.

This repo is the Meditation Sangha Android app plus a Meditation Hall API for shared sittings. Timer, gongs, and a private log exist so a sit can still happen without a hall; the product is **collective presence**, not a standalone timer listing.

Gradle root project name is **SanghaWorld**. Current Android version: **1.8** (`versionCode` 9).

> **Package note:** The Android `applicationId` is still `com.digital.sanghaworld` from earlier scaffolding. Play listing and launcher name for *this* product should be **Meditation Sangha**. A new package is required before a distinct Play Store listing from Vipassana Timer.

## What’s in this repo

| Path | Role |
| --- | --- |
| `app/` | Android app (Kotlin, Jetpack Compose, Material 3) |
| `backend/` | Separate Ktor API (halls, sessions, auth). Not included in the root Gradle build. |
| `Play-Store/` | Draft Play Console copy and screenshots (update for Meditation Sangha before publish) |
| `design.md` / `tech.md` / `feature1.md` | Product and platform design. Some docs still say “Vipassana Timer”; treat that as reuse of timer UX, not the product name. Broader than what ships today. |

## Android app

Launcher name: **Meditation Sangha**.

### Features

- **Meditation halls** (backend + Google Sign-In) — browse or create halls, join by share code, hall detail, hall log, community support. Shared schedule and sit; no chat, video, or participant audio during a sit.
- **Sit timer** — presets (15 / 30 / 45 minutes, 1 hour, 2 hours) and custom duration. Short settling countdown, start gong, analog ring + remaining time. For sits longer than 30 minutes, a gong 5 minutes before the end. End of sit: three full gong strikes with a pause between them.
- **Foreground service** — timer continues with the screen off or the app in the background; notification while a sit is running. Wake lock during active sessions.
- **Gong settings** — Dhamma (default), Classic, Temple. Preview and persist the chosen sound (`app/src/main/res/raw/`).
- **Be Aware Always** — reminder gongs over a chosen span of hours at a chosen interval.
- **Meditation log** — completed sits stored on-device as `meditation_log.json`, daily totals and sit count, calendar view, delete a day.
- **Donate** — optional Google Play Billing donations (`donation_unit`). No paywall.

### Tech

- Kotlin, Jetpack Compose, Material 3
- Foreground service (`TimerService`, media playback type)
- Local JSON log in app internal files
- Google Sign-In (Credential Manager) and REST + WebSocket client for halls
- Play Billing for donations

### Prerequisites

- Android Studio (recent stable)
- Android SDK **36** (`compileSdk` / `targetSdk` 36, `minSdk` 24)
- JDK 17 (Android Studio bundled JDK)

### Local config

Create or edit `local.properties` (not committed):

```properties
sdk.dir=...
GOOGLE_WEB_CLIENT_ID=<Web client ID from Google Cloud>
API_BASE_URL=http://10.0.2.2:8080
```

`GOOGLE_WEB_CLIENT_ID` and `API_BASE_URL` are injected as `BuildConfig` fields. Emulator default API base is `http://10.0.2.2:8080` (host machine port 8080).

### Build

From the repo root:

```bash
./gradlew assembleDebug
```

On Windows: `gradlew.bat assembleDebug`.

Release builds minify and shrink resources.

### Run

1. Open this folder in Android Studio.
2. Select an emulator or device.
3. Run the `app` configuration.

Notification permission is requested on Android 13+. Internet is required for sign-in and halls, not for a local sit.

### App layout

```text
app/src/main/java/com/digital/sanghaworld/
  MainActivity.kt, TimerViewModel.kt
  audio/          Gong catalog and preview
  auth/           Google Sign-In, token store
  billing/        Donations
  hall/           Hall API client, store, session engine
  logging/        Local meditation log
  service/        Timer foreground service
  ui/             Compose screens (home, timer, log, gongs, awareness, halls, donate)
```

## Backend (Meditation Hall API)

Standalone Gradle project. Kotlin + Ktor. Default store is H2; set `JDBC_URL` for PostgreSQL.

```bash
cd backend
gradlew.bat run
```

API base: `http://localhost:8080/v1`

See [backend/README.md](backend/README.md) for Google OAuth, JWT, Docker, and production notes.

OAuth Android package currently matches `applicationId`: `com.digital.sanghaworld`.

## Play Store

This product must ship as its **own** listing (**Meditation Sangha**), not as an update to Vipassana Timer. That requires a new `applicationId` and listing assets.

Draft copy still lives in `Play-Store/` and may still mention the older timer name until those files are rewritten.

Suggested listing title (30-character limit): `Meditation Sangha` (17 characters).

## Notes

- Sit log stays on the device unless you use halls (then attendance goes through the API after sign-in).
- Design docs describe a larger platform (FCM, Redis, object storage, snapshots). Those are not all implemented; treat `design.md` / `tech.md` as intent, not current runtime.

## License

No license specified yet.

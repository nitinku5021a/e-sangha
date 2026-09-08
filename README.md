# Vipassana Timer

A minimalist Vipassana meditation timer for Android built with Kotlin and Jetpack Compose.

## Features
- Preset durations plus a custom timer dialog.
- Short preparation countdown before the session starts.
- Start, pre-end, and end gongs (foreground service keeps the timer alive).
- Wake lock during active sessions.
- Local meditation log with daily totals.

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- Foreground service + notifications
- Local JSON storage in app internal files

## Getting Started
## Play Store
Package: `com.digital.sanghaworld` (new listing).

### Prerequisites
- Android Studio (Hedgehog or newer recommended)
- Android SDK 35
- JDK 17 (Android Studio bundled JDK works)

### Build
```bash
./gradlew assembleDebug
```

### Run (Android Studio)
1. Open the project folder in Android Studio.
2. Select an emulator or connected device.
3. Click Run.

## Project Structure
- `app/src/main/java/com/digital/sanghaworld` - core app logic
- `app/src/main/java/com/digital/sanghaworld/ui` - Compose UI
- `app/src/main/java/com/digital/sanghaworld/service` - timer foreground service
- `app/src/main/java/com/digital/sanghaworld/logging` - meditation log storage

## Notes
- The meditation log is stored in the app's internal files as `meditation_log.json`.
- Notification permissions are required on Android 13+.

## License
No license specified yet.

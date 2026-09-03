# TimeUp

A chess clock for your day, for Android 16+. A clean-room reimplementation of the idea behind
[OwnTime](https://owntime.app) (iPhone), built for a personal Pixel install.

- Define a few **priorities**, each with a **daily time budget** and a colour.
- Only **one clock runs at a time**; starting one pauses the other.
- When a budget is spent a **real alarm** rings, through silent mode and Do Not Disturb.
- At local **midnight everything resets**; a running session keeps running.
- **No account, no server, no analytics, no INTERNET permission.** Data is an append-only event log
  in a SQLite file you can export with one tap.

The design document is in [`docs/PRD.md`](docs/PRD.md).

## Install it on your phone

Every push builds APKs in [GitHub Actions](../../actions); every push to `main` also publishes them
on the [**latest** release](../../releases/tag/latest).

1. On the phone, open the latest release and download `timeup-release.apk`.
2. Open the download; allow installing from that source when asked.
3. Open TimeUp, create a priority. It asks for notification and "Alarms & reminders" permissions:
   grant both, that is what makes the alarm ring on time and over the lock screen.

With a cable instead: `adb install -r timeup-release.apk`.

**Signing.** With no secrets configured, CI signs with a throwaway debug key, so each new build
needs an uninstall before installing the next one. To get in-place updates, create a key once and
store it in the repository secrets:

```sh
scripts/make-keystore.sh            # prints the four secrets to set
```

Secrets: `TIMEUP_KEYSTORE_BASE64`, `TIMEUP_KEYSTORE_PASSWORD`, `TIMEUP_KEY_ALIAS`, `TIMEUP_KEY_PASSWORD`.
From then on every build is signed with your key and `adb install -r` / tapping the APK updates the app.

## Build locally (no Android Studio)

Needs JDK 17+ and the Android SDK command-line tools.

```sh
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
export ANDROID_HOME=/path/to/sdk

./gradlew test                       # JVM tests for the fold / overlap / alarm logic
./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug               # onto a connected phone (USB, or `adb pair` over Wi-Fi)
./gradlew checkNoInternetPermission  # build-time guarantee that the app is offline
```

The debug build installs side by side with the release build as `pub.mkm.timeup.debug`.

## Layout

| Module | What |
|---|---|
| `domain/` | Pure Kotlin: event types, the fold, day overlap, `DayView` (remaining, alarm time). No Android imports; tests in `domain/src/test`. |
| `app/` | Android app: SQLDelight store, Compose UI, `AlarmManager` scheduling, ringing foreground service, Live Update notification, Glance widget, Quick Settings tile, shortcuts, intents, export, backup rules. |

## How it works, briefly

- **State is derived, never counted.** Remaining time is always `budget − Σ overlap(session, today)`,
  computed from stored timestamps and the clock. Reboots and process death cannot lose time.
- **Append-only event log** (`events` table) is the truth. `priorities` and `sessions` are
  materialised from it on every start.
- **No background service while a session runs.** `AlarmManager.setAlarmClock` carries the alarm, an
  ongoing Live Update notification carries the countdown; a foreground service exists only while
  the alarm rings.
- **Midnight, reboot, time-zone change, clock change** all trigger the same recompute-and-reschedule.

## Automation

- Quick Settings tile: toggle (stop, else start the last-used priority).
- Launcher shortcuts: Toggle, and a dynamic Start shortcut per priority.
- Widget: day ring plus the list; tap a row to start/stop.
- Intents (Tasker, MacroDroid, adb):

```sh
adb shell am start -n pub.mkm.timeup/.control.ActionActivity -a pub.mkm.timeup.action.START --es priority "Deep Work"
adb shell am start -n pub.mkm.timeup/.control.ActionActivity -a pub.mkm.timeup.action.TOGGLE
adb shell am start -n pub.mkm.timeup/.control.ActionActivity -a pub.mkm.timeup.action.LOG --es priority Reading --ei minutes 40
```

The exported broadcast receiver with the same actions is guarded by a signature-level permission
for future first-party modules (watch).

## Permissions

`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `POST_PROMOTED_NOTIFICATIONS`,
`FOREGROUND_SERVICE(_SPECIAL_USE)`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`. No `INTERNET`;
`checkNoInternetPermission` fails the build if it ever appears. The merged manifest also carries
`ACCESS_NETWORK_STATE`, contributed by WorkManager (a dependency of the Glance widget library); it
only allows reading connectivity state, not using the network.

## Export

Menu → Export database checkpoints the WAL and hands the live `timeup.db` to the share sheet.
The `meta` table carries a `README` row with the overlap rule so a notebook reproduces the app's
numbers.

## Not in this build

- Wear OS companion and phone ↔ watch sync (PRD §9).
- AppFunctions / Gemini agent access (PRD §8.2): the library is still alpha and the Gemini side is
  a private preview. The intent surface above covers automation today.
- Google Drive appdata sync (PRD §10.B). Android Auto Backup is configured (§10.A).

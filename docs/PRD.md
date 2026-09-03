# OwnTime for Android — Product Requirements & Design

Status: draft v0.1 — 2026-09-03
Target: personal build for a Pixel 9 (Android 16+), optional Wear OS companion
Source product: https://owntime.app (iPhone + Apple Watch, by StaLabs). The author has said there will be no Android version.

---

## 1. What we are building

A clean-room Android reimplementation of OwnTime: a "chess clock for your day".

- You define a few **priorities** (e.g. Deep Work, Family, Reading), each with a **daily time budget** and a colour.
- Only **one clock runs at a time**. Starting one pauses whatever was running.
- When a budget is spent, a **real alarm** rings (through silent mode and Do Not Disturb), not a notification.
- At local **midnight everything resets**.
- **No account, no server, no analytics.** Data lives on the device, in an event log, exportable as a plain SQLite file.

Additions beyond the original, all optional and off by default:

- Backup / sync through the user's own Google account (Section 10).
- Gemini / agent integration via Android AppFunctions (Section 8).
- Wear OS companion (Section 9).

Naming: use a different name and icon (working name below: **DayClock**). OwnTime is someone else's trademark; the concept is not.

## 2. What the original does (extracted from owntime.app, its support and privacy pages, and the HN post)

Why the author built it (from the HN post): to balance competing priorities in his life, based on the "roles" idea from *The 5 Choices* (2015), after repeatedly failing at time blocking because his role needs too much flexibility. Budgets instead of blocks: you decide *how much* per day, not *when*. The whole point is to be kicked out of the running role when its time is up — which is why a real alarm (AlarmKit) and a watch surface were the two features he considered essential. The app is deliberately strictly scoped to one day, has no statistics, will not be extended or turned into a service, and costs a one-time $1.99.

Design consequences for us: keep the same narrow scope (no weekly budgets, no scheduling, no stats), treat the alarm as the core feature rather than a nice-to-have, and keep the "all data local and open" promise as a hard requirement, not a setting.

Facts we are reproducing, in the original author's own framing:

| Area | Original behaviour |
|---|---|
| Model | Priorities with name, colour, daily budget. Budgets are "not much more than a few mutually exclusive countdown timers". Influence: "roles" from *The 5 Choices*. |
| Clock | Start one → the running one pauses. Stop when done, or start the next. Midnight resets everything. |
| Overview screen | A colour-coded budget **donut** for the day plus a list of priorities with time remaining on each. Light and dark mode. |
| Priority editor | Pick colour, set daily budget (hours + minutes), optional **wrap-up** notification N minutes before the budget runs out. |
| Alarm | "An alarm, not a notification." Uses the system alarm framework so it gets through Focus and silent mode. Fires the moment the budget is spent while a session is running. Alarm permission is requested when the first priority is created. |
| Wrap-up notification | Optional, per priority, time-sensitive notification a few minutes before. If alarms are turned off system-wide, the editor offers a wrap-up at 0 minutes as a fallback. |
| Live surface | Live Activity on iPhone while a session runs; widgets. Known limitation: a session started on the watch while the phone is asleep may not get a Live Activity because there is no server to push it. |
| Watch | Ring for the whole day ("hours still yours" in the centre), single-priority focus ring, list with swipe → Log / Edit, create priority on the watch. Works standalone. |
| Sync | Phone ↔ watch directly over WatchConnectivity. "No primary copy and no server." |
| Voice / automation | Every action is a Siri phrase and a Shortcuts action: start / resume / stop / suspend / toggle / log past time (asks minutes). Toggle = stop running, else start the last priority (for the Action Button). |
| Log past time | Add minutes you already spent on a priority. |
| Export | One tap exports the whole event log as the SQLite DB the app actually runs on. Written to a temp folder, handed to the share sheet, deleted on next launch. No statistics in the app on purpose. |
| Privacy | No SDKs, no analytics, no ad IDs, no server. Deleting the app deletes the data. |
| Business | One-time purchase. |

Things the public pages do **not** specify (we decide, see Section 12 for what to verify):
whether the session keeps running after the alarm, how over-budget time is shown, what happens when a budget is edited mid-day, whether deleting a priority keeps its history, how phone and watch behave when both would ring.

## 3. Goals, non-goals, principles

Goals
1. Same day-to-day behaviour as the original on a phone, with the same privacy guarantee: no network call is ever made by the base app.
2. Alarms that actually wake you: exact, through silent mode and DND, on the lock screen.
3. Data in a plain SQLite file that outlives the app.
4. Works with Claude Code from a terminal: Gradle CLI build, no Android Studio.

Non-goals
- Statistics, charts, reports (export instead, like the original).
- Multi-user, teams, sharing.
- iOS.
- Publishing on Play (personal install first; keep the design Play-compatible anyway).

Principles
- **State is derived, never counted.** The app never runs a counter. Remaining time is always computed from stored timestamps and the current clock. This is what makes process death, reboots, and sync safe.
- **Append-only event log** is the source of truth. Tables you see in the UI are materialised views of the log.
- **No background service while a session runs.** AlarmManager + a countdown notification carry the session; a foreground service only exists while the alarm is ringing.
- **Base build has no INTERNET permission.** Optional modules (backup, watch) add exactly what they need.

## 4. Platform decisions

| Decision | Choice | Why |
|---|---|---|
| minSdk | 36 (Android 16) | Pixel 9 is on 16+. Gives Live Updates and AppFunctions without fallbacks. Lower to 34 later if you want to share the APK. |
| Language / UI | Kotlin + Jetpack Compose (Material 3) | Everything on the feature list is a platform API (AlarmManager, notifications, Glance, Wear Data Layer, AppFunctions). Rust would mean JNI wrappers for all of it and would buy nothing here. |
| Storage | SQLite via **SQLDelight** (or Room) | SQLDelight keeps the schema as plain `.sq` files you control, which is what you want when the DB file *is* the export format. |
| Widgets | Jetpack Glance | Compose-style app widgets. |
| Watch | Wear OS module, Compose for Wear, Data Layer API | Only path for phone ↔ watch communication. |
| Build | Gradle wrapper + Android cmdline-tools + JDK 17, `adb` over USB/Wi-Fi | No Android Studio needed. See Section 11. |

## 5. Domain model and semantics

### 5.1 Entities

**Priority**
- `id` (ULID), `name`, `color` (ARGB int, chosen from a fixed palette of ~10), `budget_seconds` (1 min … 24 h), `wrap_up_minutes` (nullable; 0 allowed), `sort_order`, `archived_at` (nullable).

**Session**
- `id` (ULID), `priority_id`, `started_at` (epoch ms, UTC), `ended_at` (nullable while running), `kind` (`live` | `logged`), `device_id`, `tz` (IANA zone id when started; for reproducible exports).

**Device**
- `id` (random UUID generated on first run), `kind` (`phone` | `watch`), `label`.

### 5.2 Invariants

1. At most **one open session** across all devices at any time (chess clock rule).
2. `started_at < ended_at` when closed. Zero-length sessions are dropped.
3. A day is the interval `[local midnight, next local midnight)` in the device's **current** time zone.
4. **Spent time for (priority, day)** = sum over that priority's sessions of the overlap of `[started_at, ended_at or now)` with the day. A session that crosses midnight is not split in storage; the overlap rule does the right thing.
5. **Remaining** = `budget_seconds − spent`. Can go negative (over budget).
6. **"Hours still yours"** (ring centre) = sum of `max(remaining, 0)` over non-archived priorities.

### 5.3 Event log

Every change is an event. The UI state is `fold(events sorted by (ts, id))`.

```
PriorityCreated  {priority_id, name, color, budget_seconds, wrap_up_minutes, sort_order}
PriorityUpdated  {priority_id, ...changed fields}           (last writer wins per field)
PriorityArchived {priority_id}
SessionStarted   {session_id, priority_id, at}              (fold: close any open session at `at`)
SessionStopped   {session_id, at}
TimeLogged       {session_id, priority_id, minutes, at}     (fold: session kind=logged, [at − minutes, at])
SessionDeleted   {session_id}                               (undo for a mistaken log)
```

Event envelope: `id` (ULID, gives creation order and uniqueness), `device_id`, `ts` (epoch ms), `tz`, `type`, `payload` (JSON).

Why: it makes sync trivial (exchange missing events, union by id, refold) and makes the export self-describing.

### 5.4 Merge rule for two open sessions

After merging logs from two devices you can end up with two open sessions (each device started one while disconnected). Deterministic fix inside the fold: when a `SessionStarted` is folded, every currently open session is closed at that event's `at`. Since events are folded in `(ts, id)` order, the later start wins and the earlier one is closed at the later start time. This is exactly what happens on a single device, so no special case is needed.

Clock skew between devices is small (both NTP-synced); a few seconds of error in a day budget is acceptable and is documented.

### 5.5 Time and calendar edge cases

- **Midnight**: the running session continues; only the accounting rolls over. A scheduled "midnight tick" reschedules the alarm for the new day (Section 7.5).
- **Time zone change** (`ACTION_TIMEZONE_CHANGED`): day boundaries move; recompute and reschedule. Historical days in the export are interpreted with the `tz` stored on each event.
- **Wall clock jump** (`ACTION_TIME_CHANGED`): recompute and reschedule. Store `elapsedRealtime` alongside `ts` in `SessionStarted` so a large backwards jump while a session is running can be detected and the user warned (rare; not worth more than a warning).
- **Reboot** (`BOOT_COMPLETED`): reschedule alarms from DB state; if a session was open, the countdown notification is re-posted.
- **Budget edited while running**: remaining recomputes immediately, alarm rescheduled. If already over the new budget, the alarm fires at once (matching "the moment the budget is spent").

## 6. Screens (phone)

Match the original's information design; use Material 3 for the chrome. Light and dark themes.

### 6.1 Overview (home)

- Top: **day donut**. One segment per non-archived priority, in priority colour. Segment length ∝ budget; the filled portion ∝ spent. Over-budget segments are fully filled with a hatched or darker edge. Centre: "hours still yours" as `Hh MMm`, and below it the running priority's name if any.
- Below: **priority list**, one row each: colour dot, name, remaining time (`1h 20m`, or `−12m` in error colour when over), and a play/stop icon. The running row is highlighted and its time ticks every second.
- Tap a row → start it (and stop whatever ran). Tap the running row → stop.
- Swipe a row → **Log** (past time) and **Edit**.
- Overflow menu: New priority, Export database, Backup (Section 10), Settings, About.
- Empty state: one sentence and a "Create your first priority" button.

### 6.2 Priority editor (create / edit)

- Name (text), colour (palette grid), daily budget (hour + minute pickers), wrap-up toggle + minutes stepper.
- Archive (edit mode only) with confirmation. Archiving hides the priority and keeps history.
- On **first** create: explain and request the alarm + notification permissions (Section 7.6). If exact alarms are denied, show a persistent "Alarms are off" line that deep-links to the system setting and offer wrap-up at 0 minutes.

### 6.3 Log past time

- Priority (preselected when reached via swipe), minutes (numeric, quick chips 5/15/30/60), optional end time (default now). Creates a `logged` session.

### 6.4 Alarm screen

- Full-screen activity launched via full-screen intent when the budget is spent. Shows priority name and colour, "Budget spent", and two buttons: **Stop [priority]** (closes the session) and **Keep going** (silences the alarm, session stays open and goes negative). Both also dismiss the notification.
- Ringing stops on its own after 2 minutes (leaves the session open, posts a normal notification).

### 6.5 Settings

- Alarm sound (system alarm ringtone picker), vibrate, alarm auto-stop duration.
- Toggle: Assistant / agent access (AppFunctions), off by default.
- Backup section (Section 10).
- Watch section (Section 9), only shown if the Wear module is present.
- "Your data": export, delete everything.

### 6.6 Onboarding

None beyond the empty state. Permission prompts are contextual (first priority, first session).

## 7. Timekeeping and alarms on Android

This is the part that differs most from iOS. Map of concepts:

| iOS (original) | Android (this app) |
|---|---|
| AlarmKit alarm | `AlarmManager.setAlarmClock()` + foreground service ringing on the **alarm audio stream** + full-screen intent |
| Time-sensitive notification | High-importance notification channel |
| Live Activity | Android 16 **Live Update** (promoted ongoing notification with a countdown chronometer) |
| Widgets | Glance app widgets + Quick Settings tile |
| Siri / Shortcuts | AppFunctions + app shortcuts + explicit intents |

### 7.1 Scheduling the budget alarm

When a session starts (or anything changes remaining time):

```
end = now + remaining(priority, today)      // may be in the past → fire immediately
alarmManager.setAlarmClock(AlarmClockInfo(end, showIntent), firePendingIntent)
```

- `setAlarmClock` is exact, exempt from Doze, and shows the alarm icon in the status bar like a real alarm clock. No battery-optimisation exemption needed.
- One alarm at a time is enough (only one session runs). Cancel and reschedule on every relevant change.
- Permission: declare `SCHEDULE_EXACT_ALARM`. It is denied by default for new installs on Android 14+; check `canScheduleExactAlarms()` and send the user to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` on first priority creation. Listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` to reschedule when granted. (`USE_EXACT_ALARM` would skip the prompt, but Play restricts it to alarm-clock/calendar apps; for a sideloaded personal build either works.)
- If the permission is revoked: the app schedules nothing exact, shows "Alarms are off" in the editor, and falls back to an inexact wrap-up notification at 0 minutes via `setAndAllowWhileIdle` (may be a few minutes late — say so in the UI).

### 7.2 Ringing

On fire (BroadcastReceiver → start foreground service):

- Foreground service (type `specialUse`, subtype declared as "alarm ringing"; `mediaPlayback` also works). Starting an FGS from the background is allowed here because the trigger is an alarm-clock alarm.
- Play the chosen ringtone with `AudioAttributes(USAGE_ALARM)` on `STREAM_ALARM`, looping. The alarm stream is independent of ring volume, so silent/vibrate mode does not mute it. DND lets "Alarms" through by default (user-controllable in DND settings; document this).
- Vibrate with an alarm pattern.
- Post a notification on channel `alarm` (IMPORTANCE_HIGH, `CATEGORY_ALARM`, no channel sound since the service plays it) with a **full-screen intent** to the Alarm screen and Stop / Keep going actions.
- Full-screen intent permission: `USE_FULL_SCREEN_INTENT`. Granted by default on Android 14+ for sideloaded apps; check `canUseFullScreenIntent()` and offer `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`. If not granted, the heads-up notification still appears and the sound still plays.
- Auto-stop after 2 minutes.
- Fire at most once per (priority, day). Re-crossing the budget after "Keep going" does not ring again.

### 7.3 Wrap-up notification

- Scheduled at `end − wrap_up_minutes` with `setExactAndAllowWhileIdle` (or piggyback on the same `setAlarmClock` if you prefer one PendingIntent that checks which one is due).
- Channel `wrapup`, IMPORTANCE_HIGH, default notification sound. Does not bypass silent mode — matches the original ("gets through Focus, but not the ringer switch").
- Cancelled if the session stops first.

### 7.4 Running-session surface (Live Activity equivalent)

While a session runs, post one **Live Update**:

- `NotificationCompat.ProgressStyle` (or standard style), `setOngoing(true)`, `setRequestPromotedOngoing(true)`, manifest permission `POST_PROMOTED_NOTIFICATIONS`, title = priority name.
- Countdown without updates: `setUsesChronometer(true)`, `setChronometerCountDown(true)`, `setWhen(end)`. The system renders the ticking time; the app does nothing until the state changes.
- Progress bar = spent / budget, updated only when the app touches the notification anyway (start/stop/edit) plus a cheap 10-minute inexact refresh if you want the bar to move.
- Actions: **Stop**, **Switch…** (opens app). Tapping opens Overview.
- Shows as a status-bar chip, at the top of the shade, and on the lock screen. Check `canPostPromotedNotifications()`; if the user disabled Live Updates for the app it degrades to a normal ongoing notification.
- No foreground service is needed for this notification; it survives the app process being killed because it is owned by the system until cancelled.

Improvement over the original: a session started on the watch **can** post this from the background, because Android does not require a server push for it. The phone receives the Data Layer event and posts the Live Update.

### 7.5 Midnight tick

- Schedule an inexact `setAndAllowWhileIdle` at the next local midnight + 1 s. On fire: recompute remaining for the running session, reschedule the budget alarm and wrap-up, refresh the Live Update and widgets, reschedule the next tick.
- Also run the same recompute on `BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_CHANGED`, and whenever the app comes to the foreground.

### 7.6 Permissions summary

| Permission | When asked | Purpose |
|---|---|---|
| `POST_NOTIFICATIONS` (runtime) | first priority created | alarm, wrap-up, Live Update |
| `SCHEDULE_EXACT_ALARM` (special access) | first priority created | budget alarm |
| `USE_FULL_SCREEN_INTENT` | manifest, granted by default | alarm over lock screen |
| `POST_PROMOTED_NOTIFICATIONS` | manifest | Live Update |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | manifest | ringing |
| `RECEIVE_BOOT_COMPLETED` | manifest | reschedule after reboot |
| `VIBRATE` | manifest | alarm |
| `INTERNET` | **absent** in base build | — |

## 8. Voice, shortcuts, and Gemini

### 8.1 Actions to expose

Same six as the original's Siri set, plus two read-only ones useful for agents:

| Function | Parameters | Behaviour |
|---|---|---|
| `startSession` | `priority?` (name, fuzzy match) | Start; if omitted, start the last-used priority |
| `stopSession` | `priority?` | Stop the running session (error if none) |
| `toggleSession` | — | Stop if running, else start last-used |
| `logPastTime` | `priority?`, `minutes` | Add a logged session |
| `listPriorities` | — | Names, colours, budgets, remaining today |
| `getStatus` | — | Running priority (if any), remaining, ends-at |

### 8.2 AppFunctions (the Siri/Shortcuts equivalent)

Android 16 has **AppFunctions**: apps register Kotlin functions (annotated `@AppFunction`, compiled by KSP into a schema) in an OS-level registry, and callers holding `EXECUTE_APP_FUNCTIONS` — agents, assistants, Gemini — can discover and invoke them. Google describes it as the on-device equivalent of MCP tools. As of May 2026 the Gemini side is a private preview with trusted testers, so build it and wait for general availability; it costs little.

Implementation
- Jetpack `androidx.appfunctions` library + KSP. One `AppFunctionService` bound with `BIND_APP_FUNCTION_SERVICE` (system-only; never remove that permission).
- Functions above, with clear descriptions — the agent decides when to call based on descriptions, not names.
- Behind the Settings toggle "Assistant / agent access". When off, the service reports no functions.

Privacy note for the policy: when a user talks to Gemini, the spoken request and whatever the function returns (priority names, remaining minutes) go to Gemini and may be processed off-device by Google. That is the user's choice per invocation, the same as Siri, but say it plainly and keep the toggle off by default.

### 8.3 Shortcuts and automation (works today, no Gemini needed)

- **Quick Settings tile**: "Toggle" — same semantics as the original's Action Button. Long-press opens the app.
- **App shortcuts**: static `toggle`, dynamic per-priority `start` shortcuts (pinnable to the home screen).
- **Explicit intents** for Tasker / MacroDroid / Wear tiles: an exported `BroadcastReceiver` guarded by a signature-level permission, actions `START`, `STOP`, `TOGGLE`, `LOG` with extras. Same handlers as the AppFunctions.
- **Glance widget** (2×2 and 4×2): ring + list, tap row to start/stop.
- **Google Assistant App Actions** (`shortcuts.xml` capabilities): optional; AppFunctions is the successor and the one Gemini will use. Skip unless you use the classic Assistant.

### 8.4 Other Gemini angles considered

- **Gemini Extensions** (cloud-side connectors): not applicable — there is no server to connect to.
- **Gemini Nano on-device** via ML Kit GenAI / AICore (Pixel 9 supports it): could parse a free-text log entry ("40 min reading after lunch") into `logPastTime`, or write a one-paragraph weekly summary from the local DB, entirely on-device. Nice, not core. Park it behind a feature flag if at all.
- Nothing in the app should call the Gemini API over the network.

## 9. Watch

### 9.1 Which watch

The Wear OS module only runs on a Wear OS watch (Pixel Watch, Galaxy Watch, etc.). A screenless Fitbit tracker has no third-party app surface, so nothing below applies to it; it is a hardware prerequisite, not a software one.

### 9.2 Watch app (Compose for Wear)

Mirror the original's five watch screens:
1. **Day ring** with "hours still yours" in the centre; tap → list. Rotary crown / bezel scrolls.
2. **Focus ring** for one priority (swipe horizontally between priorities).
3. **List**: colour-coded rows with remaining time; tap = start/stop.
4. **Row swipe** → Log / Edit.
5. **Create priority**: name (voice or keyboard), hours+minutes picker, colour.

Plus:
- **Tile**: ring + toggle button.
- **Complication**: remaining time on the running priority (or "hours still yours").
- **Ongoing Activity** while a session runs (shows on the watch face and in the recents ring).
- **Alarm on the watch**: `setAlarmClock` on the watch too, haptic + sound. Both devices ring when both have the session; dismissing on one sends a "silence" message to the other.
- Works standalone (no phone): same event log, same fold.

### 9.3 Phone ↔ watch sync

Only the **Wearable Data Layer API** (`play-services-wearable`) is allowed for this; sockets are not. Requirements: same package name and signing key on both apps.

Protocol (event-log exchange, no primary copy):
- Each device publishes a `DataItem` per calendar month per device: `/log/{deviceId}/{yyyy-mm}` containing that device's events for that month (JSON, gzip). ~10 events/day ≈ tens of KB/month, under the 100 KB DataItem limit; use an `Asset` if a month ever exceeds it.
- On `onDataChanged`, the receiver unions the events by id, refolds, reschedules alarms, refreshes surfaces.
- Immediate actions (start/stop from the watch) additionally go over `MessageClient` for latency, but the DataItem is the durable record; messages are just a hint to sync now.
- On (re)connect, both sides compare their `/log/*` items and pull what they lack. Data Layer already replicates items to a connected node, so this is mostly free.

**Privacy delta vs WatchConnectivity.** Google's docs say Data Layer traffic uses the Bluetooth link when present, and otherwise is routed through a Google-owned cloud node, end-to-end encrypted, and that apps should assume it may at some point use Google servers. So "never through the cloud" cannot be promised the way the original promises it. Two options, pick one and write it in the policy:
- **Option A (simple)**: accept it and state: "sync between your phone and watch uses Google Play services; when Bluetooth is unavailable it may pass, end-to-end encrypted, through Google's servers."
- **Option B (stricter)**: encrypt every DataItem payload with an app-level key shared at pairing (show a QR / 6-digit code on the phone, enter on the watch; key in Android Keystore on both). Google then relays ciphertext only. Small amount of code; recommended if you want parity with the original's claim.

Play services on the phone is a dependency of the watch module only; the base phone app does not include `play-services-wearable`.

## 10. Backup and sync through the user's Google account (optional)

Three mechanisms, different trade-offs. Recommendation: **A on by default, C as the "your file" path, B only if you want live multi-phone sync.**

### A. Android Auto Backup (zero code, restore-on-reinstall)

- Android backs the app's data directory up to the user's Google Drive nightly; up to 25 MB, not counted against Drive quota; on Android 9+ the backup is **end-to-end encrypted with the device lock screen**, so Google cannot read it.
- Configure `data_extraction_rules.xml`: include the DB and its WAL/shm (or checkpoint before backup), exclude cache/export temp, set `disableIfNoEncryptionCapabilities="true"` so the backup is skipped rather than uploaded unencrypted when there is no lock screen.
- Restores only on reinstall or new-device setup. Not sync. Good enough for "I lost my phone".

### B. Google Drive appDataFolder (real multi-device sync)

- OAuth via Credential Manager, scope `drive.appdata` only (hidden app folder in the user's Drive, invisible to the user's other Drive content and to other apps).
- Push the per-device per-month event-log files (same format as the watch sync) as objects, **encrypted client-side** (AES-GCM) with a key derived from a user passphrase (Argon2id). Google stores ciphertext only. Losing the passphrase loses the backup; say so.
- Pull others' files on app open and on a periodic `WorkManager` job (network-constrained). Union + refold. This is the only mechanism that gives phone + tablet + second phone the same state.
- Adds `INTERNET` and the Drive REST client to a separate build flavour.

### C. User-chosen folder via Storage Access Framework (no Google code at all)

- `ACTION_OPEN_DOCUMENT_TREE`; the user picks any folder — including a Google Drive folder through the Drive app's document provider, a Syncthing folder, or local storage.
- The app writes the encrypted (or plain, user's choice) event-log files there and reads sibling devices' files. Sync is whatever the folder's owner does; the app never talks to a network.
- Fits the original's "where it goes is up to you" stance and needs no OAuth or `INTERNET`.

### Privacy policy wording (draft)

"By default the app stores everything on your device only. If you turn on Backup, Android's standard backup copies the app's database to your Google account, end-to-end encrypted with your screen lock; the developer has no access. If you turn on Folder sync, the app writes encrypted files to a folder you choose and never contacts the network itself."

## 11. Export, data file format, and build setup

### 11.1 Export (matches original)

- Overflow → Export database. Steps: `PRAGMA wal_checkpoint(TRUNCATE)`; copy `dayclock.db` to `cacheDir/export/dayclock-YYYY-MM-DD.sqlite`; share via `FileProvider` + `ACTION_SEND` (`application/vnd.sqlite3`). Delete the export folder on next app start.
- The file contains `events` (the truth), materialised `priorities` and `sessions`, `devices`, and a `meta` table with `schema_version`, `app_version`, `exported_at`, `device_id`. Anyone can `SELECT` on it; include a `README` row in `meta` with the overlap rule for daily spent time so notebook users get the same numbers as the app.

### 11.2 Schema (SQLite)

```sql
CREATE TABLE events (
  id TEXT PRIMARY KEY,           -- ULID
  device_id TEXT NOT NULL,
  ts INTEGER NOT NULL,           -- epoch ms UTC
  tz TEXT NOT NULL,              -- IANA zone at the time
  type TEXT NOT NULL,
  payload TEXT NOT NULL          -- JSON
);
CREATE INDEX events_ts ON events(ts, id);

CREATE TABLE priorities (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, color INTEGER NOT NULL,
  budget_seconds INTEGER NOT NULL, wrap_up_minutes INTEGER,
  sort_order INTEGER NOT NULL, archived_at INTEGER
);
CREATE TABLE sessions (
  id TEXT PRIMARY KEY, priority_id TEXT NOT NULL,
  started_at INTEGER NOT NULL, ended_at INTEGER,
  kind TEXT NOT NULL CHECK (kind IN ('live','logged')),
  device_id TEXT NOT NULL, tz TEXT NOT NULL
);
CREATE INDEX sessions_open ON sessions(ended_at) WHERE ended_at IS NULL;
CREATE TABLE devices (id TEXT PRIMARY KEY, kind TEXT NOT NULL, label TEXT);
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
```

`priorities` and `sessions` are rebuilt from `events` on schema migration or after any sync merge; day-to-day they are updated incrementally by the same fold code.

### 11.3 Building without Android Studio

```
# once
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
# project: Gradle wrapper, Kotlin, AGP, compose compiler plugin
./gradlew :app:installDebug          # phone over adb (USB or `adb pair` over Wi-Fi)
./gradlew :wear:installDebug         # watch over adb Wi-Fi debugging
./gradlew test                       # JVM tests for the fold/overlap/merge logic
```

- Keep the domain (`events`, fold, overlap, alarm-time computation) in a pure Kotlin/JVM module with no Android imports so Claude Code can run its tests instantly.
- Sign debug/release with your own keystore; the same key must sign phone and watch APKs.

## 12. Things to verify against the original (or decide)

1. After the alarm, does the session keep running (goes negative) or stop? (This PRD: keeps running; "Stop" on the alarm screen closes it.)
2. Is over-budget time displayed as negative, or clamped at 0? (This PRD: negative, in error colour.)
3. Editing a budget mid-day — immediate effect? (This PRD: yes.)
4. Deleting a priority — history kept? (This PRD: archive, keep history.)
5. Does a `logged` session that crosses midnight count on the day of the end time or by overlap? (This PRD: overlap.)
6. Does the alarm re-fire if you keep going and cross again after logging? (This PRD: once per priority per day.)
7. Both devices ringing at once — desired? (This PRD: both ring, dismiss propagates.)

The HN post confirms the scope and motivation (Section 2) but says nothing about the event-log format, sync conflict handling, or the Android decision, so Sections 5.3–5.4 and 9.3 remain our own design.

## 13. Milestones

| M | Scope | Done when |
|---|---|---|
| M1 Core | domain module + tests, SQLDelight, Overview, editor, start/stop, budget alarm, midnight tick, reboot | You can live on it for a week |
| M2 Surfaces | Live Update, wrap-up, Glance widget, QS tile, shortcuts, export | Never need to open the app to start/stop |
| M3 Agents | AppFunctions + intents + settings toggle | `adb shell` can invoke each function; Gemini when preview opens |
| M4 Backup | Auto Backup rules; SAF folder sync; (optional) Drive appdata flavour | Reinstall restores; second device converges |
| M5 Watch | Wear app, tile, complication, Data Layer sync, watch alarm, optional payload encryption | Start on watch → phone shows Live Update within seconds |

## 14. Acceptance tests (domain module, all JVM)

- Overlap rule: session 23:30–00:30 gives 30 min to each day.
- Merge: device A starts P1 at 10:00, device B starts P2 at 10:05 while disconnected; after merge P1 is closed at 10:05, P2 open.
- Budget 60 min, logged 45 min at 09:00, session started 12:00 → alarm at 12:15; editing budget to 30 min at 12:05 → alarm fires immediately.
- Time zone change from Europe/Rome to America/New_York at 20:00 local → "today" recomputed, no session lost, alarm rescheduled.
- Export DB: `SELECT` reproduces the app's remaining minutes for a fixture day.
- Base APK manifest contains no `INTERNET` permission (build-time check).

## 15. Sources consulted

- owntime.app (home, Support, Privacy — last updated 2 Sept 2026)
- HN "Show HN: OwnTime" (id 49528506) — author's post text (motivation, scope, pricing)
- Android developer docs: Live Updates (Android 16), AppFunctions overview (Gemini private preview as of May 2026), Schedule exact alarms / SCHEDULE_EXACT_ALARM changes, Auto Backup (E2E with lock screen on Android 9+, 25 MB), Wearable Data Layer overview (cloud node caveat), Full-screen intent limits (Android 14)

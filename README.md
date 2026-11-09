# RiseUp

An Android alarm that cannot be snoozed, cannot be dismissed from the bed, and only
stops when you scan a barcode that lives in another room.

Built for the A+B setup:

- **A** — the phone charges in the bathroom overnight, so the alarm is not within reach.
- **B** — dismissing the alarm requires scanning an enrolled barcode (any barcode you
  already own: toothpaste, coffee tin, shampoo, a book's ISBN). The default plan puts
  that object in the kitchen, so the alarm drags you bathroom → kitchen.
- **Guard** — 90 seconds after you dismiss, a "STILL UP?" screen asks for 40 steps.
  Ignore it for 150 seconds and the full alarm comes back, so walking back to bed
  costs you the morning twice.

## Install (sideload)

1. Download the latest APK from the [GitHub Releases page](https://github.com/mrjshzk/riseup/releases/latest) to the phone (USB, Drive, Telegram-to-self — anything).
2. Tap it in Files. Android will ask to allow installs from that app:
   **Settings → Apps → Special access → Install unknown apps → (your file manager) → Allow**.
3. Open RiseUp and grant camera, activity recognition and notifications when asked.
   The home screen shows the next alarm, the dismiss-code state and a readiness summary.
4. On the home screen, tap **SHOW** next to the readiness summary (`READY` or
   `n FIXES NEEDED`) and get all six checks to a green square:
   - **Exact alarms** → toggle "Alarms & reminders" on.
   - **Full-screen alarm** → toggle on. Without this the alarm is a notification only.
   - **Notifications** → on.
   - **Camera** / **Step sensor** → Allow.
   - **Battery unrestricted** → Allow. Then also open
     **Settings → Apps → RiseUp → Battery usage → Unrestricted** (Nothing OS kills
     background work aggressively; this step is not optional on CMF/Nothing phones).
5. **Dismiss code → Scan to enroll** → point at a barcode on something that lives
   away from your bed (kitchen is the intended target).
6. Tap **WEEKLY SCHEDULE** to open the week. Each row has a day, a time field and an
   ON/OFF control: tap the time to change it, tap ON/OFF to make that day ring, and tap
   the row itself to pick the day the copy buttons read from. **COPY TO MON–FRI** and
   **COPY TO ALL 7** change times only — every day keeps its own on/off state.
   Go back and press **Test the whole chain**. Put the phone down and walk to your barcode:
   the alarm fires in 10 seconds, only the scan stops it, and 90 seconds later the
   steps screen should appear.
7. Leave the phone on the charger in the bathroom tonight.

## What is deliberately missing

- No snooze. No dismiss button. No settings reachable from the alarm screen.
- Back is swallowed on the alarm screen; the ringing lives in a foreground service, so
  Home-ing out of the screen does not silence it.
- The scan timestamp is single-use and expires in two minutes, so a stale dismissal
  cannot kill tomorrow's alarm.

## Programming your week

Every weekday is independent: its own wall-clock time and its own on/off switch.
Monday 06:15, Saturday 09:30, Sunday off is a normal week. If every day is off, the
pending alarm is cancelled and the app counts no trigger at all.

Existing installs are migrated on first read: the old single time and the old global
switch seed all seven days, because the old alarm rang every day. Nothing in the old
keys is deleted, so an interrupted upgrade cannot crash the app.

Two clock-change rules, both deterministic:

- **Skipped time (spring forward)** — a time that never happens (01:30 on a
  01:00 -> 02:00 jump) rings at the instant the clocks jump, i.e. 02:00. Never early.
- **Repeated time (fall back)** — the earliest occurrence still in the future: 01:30
  rings on the first pass through 01:30, and on the second pass only if the first
  pass is already behind you at scheduling time.

Timezone, clock and date changes re-arm the next alarm from the new local clock.

## Known limits

- Force-stopping the app from Settings, or revoking permissions, beats any alarm app.
  Nothing in userspace prevents that — only device-owner provisioning could.
- A photographed barcode can be scanned from bed. Put the object out of the bedroom and
  this stops mattering; if you want a code that cannot be photographed, use an NFC tag
  (the app currently ships the barcode path).
- On an unlocked, in-use phone the system shows a heads-up notification instead of
  taking the screen (Android FSI behaviour). The service still rings, so the alarm is
  not lost — tapping the notification opens the stop screen.

## Build

Requires JDK 17 and the Android SDK (platform 35, build-tools 35.0.0).

```
export JAVA_HOME=/path/to/jdk-17
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle assembleDebug       # requires Gradle 8.10.2 or compatible
gradle testDebugUnitTest    # scheduling logic tests
```

APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Layout

| File | Job |
|---|---|
| `Prefs.kt` | week model (7 x enabled/hour/minute), legacy migration, other settings |
| `AlarmScheduler.kt` | `setAlarmClock` scheduling, week-wide next-occurrence maths, guard timer |
| `AlarmReceiver.kt` | broadcast → foreground service + full-screen activity; boot re-arm |
| `TimeChangeReceiver.kt` | timezone / clock / date change → re-arm the next alarm |
| `AlarmService.kt` | ringing + vibration + volume ramp, category=alarm notification with FSI |
| `AlarmActivity.kt` | the alarm screen, single dismissal path, step guard and escalation |
| `ScanActivity.kt` | CameraX + ML Kit barcode scan, enrol or dismiss |
| `MainActivity.kt` | status: next alarm, code state, readiness summary, dry run |
| `WeekScheduleActivity.kt` | the week editor: per-day time, ring control, copy actions |

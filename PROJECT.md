# RiseUp — project brief

## Purpose

RiseUp is an Android alarm designed to make dismissal require getting out of bed. The phone is left charging outside the bedroom; the alarm stops only after scanning an enrolled barcode elsewhere. A follow-up step challenge is intended to make returning to bed harder.

## Current product

- **Package:** `com.mrjshzk.riseup`
- **Current branch baseline:** `main` at `b96deb3` (merged PR: `Fix footsteps guard and refine UI with 3-tab navigation`)
- **Version in Gradle:** `1.2` (`versionCode 3`)
- **Android:** min SDK 29, target/compile SDK 35
- **UI:** native Kotlin `Activity` and programmatic views; no Compose, no AppCompat, no view-binding library.
- **Public repository:** <https://github.com/mrjshzk/riseup>

## Main user flow

1. Install and configure exact alarms, full-screen intents, notifications, camera, activity recognition, and battery restrictions.
2. Enrol a barcode belonging to an object that stays outside the bedroom.
3. Set independent time and enabled state for each weekday.
4. Leave the phone outside the bedroom overnight.
5. At alarm time, scan the enrolled barcode to stop ringing.
6. Complete the follow-up step guard; ignoring it escalates back to the alarm.

The alarm intentionally has no snooze or ordinary dismiss control. Force-stop, uninstall, or revoking permissions remain possible escape routes; this is not a device-owner/parental-control app. The boot receiver is intended to re-arm alarms after reboot.

## Navigation

The current `MainActivity` is a three-tab container:

- **Alarm** — next-alarm summary and quick status.
- **Schedule** — weekday schedule and time controls.
- **Settings** — barcode enrolment, system readiness, and test alarm.

`WeekScheduleActivity` remains as a compatibility entry point that forwards to the Schedule tab. Preserve its manifest declaration unless all old explicit intents are removed and tested.

## Architecture map

| File | Responsibility |
|---|---|
| `MainActivity.kt` | Three-tab navigation; next-alarm summary; weekday editing; setup/readiness controls; test flow. |
| `WeekScheduleActivity.kt` | Compatibility route into the Schedule tab. |
| `AlarmScheduler.kt` | Finds the next strictly-future weekly occurrence; exact alarm and follow-up scheduling; DST overlap/gap rules. |
| `AlarmReceiver.kt` | Receives alarm/guard/boot broadcasts and starts the relevant alarm service/activity flow. |
| `AlarmService.kt` | Foreground ringing, volume/vibration, step counting, and escalation. |
| `AlarmActivity.kt` | Full-screen alarm and follow-up challenge UI. |
| `ScanActivity.kt` | Camera barcode scan for enrolment or alarm dismissal. |
| `Prefs.kt` | Weekday alarm data, legacy single-alarm migration, barcode and transient alarm state. |
| `TimeChangeReceiver.kt` | Recomputes the next alarm after clock, date, or timezone changes. |
| `Ui.kt` | Shared tokens, view helpers, insets, and window styling. |

## Behaviour contracts

Changes to UI or navigation must preserve these unless the user explicitly changes the product:

- Weekly schedule has seven independent day records: enabled state, hour, minute.
- Existing single-time installs migrate without losing the previous time or enabled state.
- The scheduler chooses the next future enabled occurrence, handles week boundaries and DST deterministically, and cancels when every day is off.
- Clock/timezone/date changes re-arm the next alarm.
- Only a matching enrolled barcode dismisses the ringing alarm; scanner results use the app's one-use state handshake.
- Keep foreground ringing independent of the visible activity; Home or Back must not silently stop it.
- The step challenge and escalation are service/scheduler state, not merely UI timers. Do not move critical timing into an Activity lifecycle.
- Keep system permission handling explicit. Never claim an alarm is reliable when exact-alarm, full-screen, notification, or battery settings are missing.

## Design direction

The design-system source of truth is `docs/design-system.md`; screen specifications are in `docs/ui-spec.md`; colour resources are in `app/src/main/res/values/colors.xml` and the reference tokens in `design/colors.xml`.

Keep the interface sparse and task-focused: high contrast, square geometry, flat fills, borders instead of elevation, a single restrained accent, and short action-led copy. Avoid gradients, glass effects, ornamental cards, faux metrics, emoji, motivational copy, unnecessary confirmation dialogs, and toasts. Keep touch targets generous, and make controls' purpose visible rather than relying on hidden gestures.

## Build and test

Requirements: JDK 17, Android SDK platform 35, Build Tools 35.0.0, Gradle 8.10.2 (or compatible). Local `local.properties` supplies the SDK path and is intentionally not committed.

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

Builds run on **Atlas** — the Debian 13 home machine that also hosts Hermes. The toolchain is a one-time install there (JDK 17, Android SDK platform 35 + Build Tools 35.0.0, Gradle 8.10.2); it is not preinstalled, so check `java -version` and `sdkmanager --list` before trusting a build. Install under `/opt/data/opt/` and export the paths:

```bash
export JAVA_HOME=/opt/data/opt/jdk-17
export ANDROID_HOME=/opt/data/opt/android-sdk
export PATH=/opt/data/opt/gradle-8.10.2/bin:$JAVA_HOME/bin:$PATH
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

Inside the Hermes container `/opt/data` is the host's `/home/miguel/.hermes`, so from a shell on Atlas itself the same directories read `/home/miguel/.hermes/opt/…`. Atlas has 8 vCPU and 15 GB RAM, so a build and an emulator can coexist, but `gradle.properties` caps workers at 2 on purpose: leave it there so a build does not starve the machine.

Nothing here depends on a specific host: `local.properties` and the two environment variables carry every path. Validation still belongs on the phone — an emulator does not exercise the camera, the step sensor, the notification or the lock screen, so UI and alarm-lifecycle changes are checked on the CMF Phone 2 Pro.

Current scheduler unit tests are in `app/src/test/java/com/mrjshzk/riseup/AlarmSchedulerTest.kt`. After any code change, build and run tests before producing an APK. For UI or alarm lifecycle changes, test on a device when possible; scheduler unit tests do not verify camera, sensor, notification, lock-screen, or manufacturer battery behaviour.

## Release notes / caveats

- The public `v1.2` release predates the merged `b96deb3` three-tab/footsteps changes, so the published APK does not contain them. Cut a new release once the branch has been verified on the phone.
- The current code has not been visually verified on a physical phone after the three-tab change. Verify touch targets, tab navigation, sensor permission denial/fallback, alarm dismissal, and the follow-up guard on the CMF Phone 2 Pro before treating this branch as a release.
- APKs in `dist/`, build outputs, emulator screenshots, and local Android SDK configuration are ignored from source control. Publish binaries as GitHub Release assets.

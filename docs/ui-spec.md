# RiseUp — UI specification

Implements `docs/design-system.md` with `design/colors.xml` and
`design/type-and-spacing.md`. Values below are exact; if something is not written here, it
does not exist on the screen.

Ship the tokens first: copy `design/colors.xml` to `app/src/main/res/values/colors.xml`
(dark set stays unsuffixed; the `_light` block is a future `values/` swap — see that file's
header). No screen may hard-code a hex.

**Reference frame** 432 × 960 dp (CMF Phone 2 Pro). Content width = 432 − 48 = **384 dp**
(360 dp devices: 312 dp). All vertical numbers are stack heights inside the root, after
insets.

**Token shorthand used in tables**

- Type: `D` = display 88/88 · `H` = hero 64/66 · `h1` = headline 34/38 · `A` = action 26/28 ·
  `B` = body 16/24 · `L` = label 13/18 (caps, +0.10 em)
- Colour: `bg` `surface` `line` `text` `dim` `faint` `accent` `ink` (=accent_ink)
  `fill` (=action_fill) `fillInk` (=action_ink) `fillPress` (=action_press)
- `G` = 24 dp gutter (left and right, on every screen, always)

**Global rules**

1. Every screen is a `FrameLayout` root with `bg`; gutters come from the inset listener in
   `type-and-spacing.md` §7. Screens never set their own horizontal padding again.
2. No `ScrollView` on `AlarmActivity` or the guard. A scrollable alarm implies more content;
   there is none.
3. No dialogs anywhere. Destructive actions use in-place two-step confirmation
   (`CLEAR` → `TAP AGAIN`). Transient confirmations are inline lines, never `Toast`, never
   `AlertDialog`.
4. Back is swallowed on `AlarmActivity` only. Elsewhere Back behaves normally.
5. Accent budget: ≤ 2 on each decision screen (alarm, guard, scan). On the main screen,
   accent marks status only (OFF label, failing square, `FIX`) plus one accent action.
   Type sizes per screen are listed and may not be exceeded.

---

## 1. MainActivity — status, three answers

Purpose: answer, in order, (1) when the next alarm is, (2) whether the dismiss code is
enrolled, (3) whether the device is set up. Scheduling is a **destination**, not a panel that
opens on the status screen.

Root: `FrameLayout` → `ScrollView` → `LinearLayout(vertical)`. The ScrollView exists for small
screens and for the expanded setup list only.

| # | Element | Height | Space above | Type | Colour | Notes |
|---|---|---|---|---|---|---|
| 1 | top spacer | 16 | — | — | — | after status-bar inset |
| 2 | wordmark row | 24 | 0 | `L` tracking **0.20** · right `L` 0.10 | `dim` · right `dim` when ON, `accent` when OFF | left `RISEUP`; right the week's aggregate state `ON` / `OFF` |
| 3 | spacer | 32 | — | — | — | separates identity from the answer |
| 4 | eyebrow | 18 | 0 | `L` | `dim` | `NEXT ALARM` |
| 5 | spacer | 8 | — | — | — | |
| 6 | hero time | 66 | 0 | `H` `-0.02` em, tabular | `text`; `dim` when no day is on; `--:--` when nothing is scheduled | **the next occurrence**, not "the" alarm time. Readout only — not clickable (§1.4 is the only way to edit) |
| 7 | spacer | 4 | — | — | — | |
| 8 | next line | 24 | 0 | `B` | `dim` | `Mon 9 Nov` / `No day is on` |
| 9 | spacer | 24 | — | — | — | |
| 10 | schedule row | 64 | 0 | `L` · `B` · `L` | `text` · `dim` · `dim` | 1 dp `line` border, radius 0; `WEEKLY SCHEDULE` over the summary, right `EDIT`; opens §1.4 |
| 11 | spacer | 32 | — | — | — | |
| 12 | section head | 18 | 0 | `L` | `dim` | `DISMISS CODE` |
| 13 | spacer | 8 | — | — | — | |
| 14 | code state | 24 or 48 | 0 | `B` | `dim` (enrolled) · `accent` (none) | enrolled: `Enrolled 3F9…A21`, the code span in `monospace`; none: 2-line `No code enrolled. The alarm cannot be stopped.` |
| 15 | spacer | 12 | — | — | — | |
| 16a | enrol button (state: none) | 56 | 0 | `L` | `accent` · border 1 dp `accent` | `SCAN TO ENROLL`, full width, centred |
| 16b | code actions (state: enrolled) | 56 + 4 + 48 | 0 | `L` | `text` · `dim` | `REPLACE CODE` as a 56 dp bordered action; `REMOVE CODE` as a 48 dp text action under it, left-aligned |
| 17 | spacer | 32 | — | — | — | |
| 18 | readiness row | 48 | 0 | `L` | `text` (ready) · `accent` (failing) | 8 dp square `success`/`accent`, 12 dp gap, `READY` or `1 FIX NEEDED` / `3 FIXES NEEDED`, right `SHOW`/`HIDE`; tap toggles §1.2 |
| 18a | detail rows (expanded only) | 8 + 6×48 + 5×1 | 0 | `B` · `L` | see §1.2 | mounted directly under #18 |
| 19 | spacer | 32 | — | — | — | |
| 20 | section head | 18 | 0 | `L` | `dim` | `DRY RUN` |
| 21 | spacer | 8 | — | — | — | |
| 22 | dry-run body | 24 | 0 | `B` | `dim` | `Fires in 10 seconds. Walk away from the phone.` |
| 23 | spacer | 8 | — | — | — | |
| 24 | dry-run action | 48 | 0 | `L` | `dim` | text action, left-aligned: `TEST THE WHOLE CHAIN` |
| 25 | inline notice (transient) | 0 → 18 (+8 pad) | — | `L` | `accent` | 4 s after #24: `ARMED — FIRES IN 10s`; occupies **0 dp including its 8 dp top pad** when hidden |
| 26 | bottom spacer | 48 | — | — | — | |

Types used: `H` 64, `B` 16, `L` 13 — **three sizes**.
Collapsed, code enrolled, no notice: **748 dp of stack**. Details expanded adds 301 dp and
scrolls; that is a summoned state, not the resting one.

**One target per job.** #6 is the answer, #10 is the only door to the week. The clock is not
tappable: tapping a number that opens a picker for "whichever day is next" was an invisible
rule, and it made the readout and a control share one rectangle.

### 1.1 Component states

| Component | Default | Pressed | Disabled | Error |
|---|---|---|---|---|
| Wordmark | `dim` | n/a | n/a | n/a |
| State label | `ON` → `dim` | n/a | n/a | `OFF` → `accent` |
| Hero time | `text` | not clickable | `dim` when every day is off | — |
| Schedule row | border `line`, label `text`, summary `dim` | fill `surface_press`, border `line` | — | `No days on` |
| Enrol button | border `accent`, label `accent` | fill `surface_press`, border `accent` | — | — |
| `REPLACE CODE` | border `line`, label `text` | fill `surface_press` | — | — |
| `REMOVE CODE` | `dim` | fill `surface_press`, label `text` | — | first tap → label `TAP AGAIN` in `accent` for 4 s |
| Readiness row (ready) | square `success`, `READY` in `text`, `SHOW` in `dim` | fill `surface_press` | — | — |
| Readiness row (failing) | square `accent`, `n FIXES NEEDED` in `accent`, `SHOW` in `dim` | fill `surface_press` | — | — |
| Settings row (passing) | square `success`, label `text` | not clickable | — | — |
| Settings row (failing) | square `accent`, label `text`, trailing `FIX` in `accent` | fill `surface_press` | — | — |
| Inline notice | gone (0 dp) | n/a | n/a | visible 4 s |

### 1.2 The six required rows

Hidden behind the readiness row (#18) by default; `SHOW` mounts them, `HIDE` removes them.
The summary word is the standing answer, so the checklist is a detail, not a second page.

48 dp tall; 8 × 8 dp square at x = 0, vertically centred; 12 dp gap; label at x = 20;
1 dp `line` divider under rows 1–5 only. Row is clickable **only when failing** and opens the
system screen named below.

| Row | Label | Failing tap target |
|---|---|---|
| 1 | `Exact alarms` | `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| 2 | `Full-screen alarm` | `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` (API 34+) |
| 3 | `Notifications` | `Settings.ACTION_APP_NOTIFICATION_SETTINGS` |
| 4 | `Camera` | runtime permission request |
| 5 | `Step sensor` | runtime permission request |
| 6 | `Battery unrestricted` | `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

Trailing `FIX`: `L`, `accent`, 48 dp touch, right-aligned, width 72 dp, text centred.
Passing rows have no trailing element — nothing to do, nothing to read.

### 1.3 What to delete from the current build

Brand paragraph under `RiseUp`, `Alarm is OFF` / `Not scheduled` sentence strings,
"Must be green" heading (→ `REQUIRED`), `●`/`○` glyph dots (→ 8 dp squares), `FIX` as a
`Button`, `toast()` via `AlertDialog`, all `Color.parseColor` constants, the 30 sp `RiseUp`
title and the 18 sp section headings (→ 13 sp `L`), the per-day on/off pill (`#4ADE80` fill),
and the `#FF5A3C` time on every week row.

Also deleted in this pass: the inline `EDIT WEEK` expansion, the tappable hero time, and the
two-column `REPLACE` / `CLEAR` row. The week is an activity now; the code actions are named
verbs.

### 1.4 WeekScheduleActivity — the week

`WEEKLY SCHEDULE` (#10) is the only entry. It is a separate activity, so Back and `DONE`
both return to the status screen, and a schedule edit can never leave the home screen in a
half-expanded state. Every change is written immediately and reschedules; there is no save
button.

Root: `FrameLayout` → `ScrollView` → column, same gutter and insets as §1.

| # | Element | Height | Space above | Type | Colour | Notes |
|---|---|---|---|---|---|---|
| 1 | top spacer | 16 | — | — | — | |
| 2 | top row | 48 | 0 | `L` 0.20 · `L` 0.10 | `dim` · `text` | `RISEUP` left; `DONE` text action right (72 dp) → `finish()` |
| 3 | spacer | 16 | — | — | — | |
| 4 | title | 38 (76 if it wraps at 312 dp) | 0 | `h1` 34/38 | `text` | `WEEKLY SCHEDULE` |
| 5 | spacer | 8 | — | — | — | |
| 6 | intro | 24–48 | 0 | `B` | `dim` | `Choose which days ring and at what time.` |
| 7 | spacer | 24 | — | — | — | |
| 8 | column heads | 18 | 0 | `L` | `dim` | `DAY` (56) · `TIME` (112, centred) · `RING` (96, centred) |
| 9 | spacer | 8 | — | — | — | |
| 10 | rows × 7 | 64 each + 6×1 dp dividers | 0 | `L` · `B` · `L` | see below | |
| 11 | spacer | 32 | — | — | — | |
| 12 | copy head | 18 | 0 | `L` | `dim` | `COPY A TIME` |
| 13 | spacer | 8 | — | — | — | |
| 14 | copy source | 48 | 0 | `B` | `dim` | `Source: MON 07:00. Tap a day to change the source. Times only; on/off stays.` |
| 15 | spacer | 12 | — | — | — | |
| 16 | copy weekdays | 56 | 0 | `L` | `text`, border `line` | `COPY TO MON–FRI` |
| 17 | spacer | 8 | — | — | — | |
| 18 | copy all | 56 | 0 | `L` | `text`, border `line` | `COPY TO ALL 7` |
| 19 | bottom spacer | 48 | — | — | — | |

Types used: `h1` 34, `B` 16, `L` 13 — **three sizes**. The screen scrolls at 960 dp; that is
expected for a seven-row editor, and the home screen carries the summary.

Row anatomy — 64 dp, full width, `1 dp line` divider **between** rows only:

| Part | Width | Type | Colour | Touch |
|---|---|---|---|---|
| selection mark | 2 × 24 dp at x = 0 | — | `accent` when this day is the copy source, transparent otherwise | — (decorative) |
| day | 56 dp total (2 + 8 + label) | `L` 13 caps | `text` for **today**, `dim` otherwise | row background (56 dp cell and empty space) → selects the copy source |
| time | 112 × 48 dp, 1 dp `line` border | `B` 16, tabular, centred | **always `text`** — a time is editable whether or not the day rings | field → time picker for that day |
| gap | weight 1 (≥ 8 dp) | — | — | row background → selects |
| ring | 96 × 48 dp, 1 dp `line` border | `L` 13, centred | `text` when ON, `dim` when OFF | box → toggles that day |

Rules the rows obey:

1. **The column heads do the explaining.** `TIME` over a bordered number reads as a field;
   `RING` over a bordered `ON`/`OFF` reads as the switch. No arrow glyphs, no chips, no
   settings-style switch, no `LONG PRESS` hint.
2. **Time is never dim, ring state is.** Dimming the time when a day is off made the field
   look disabled, which is the opposite of true.
3. **Today is a foreground difference, not a new colour.** Today's `MON` is `text`; the
   other six are `dim`. No background, no bar, no accent, no dot, no `TODAY` word.
4. **The copy source is a state, not a hidden memory.** Last touched day, default today, one
   2 × 24 dp `accent` mark and a sentence that names it. Tapping the row selects without
   editing; the time and ring targets stay separate, so a thumb can never change a time when
   it meant to toggle a day.
5. Toggling the last on day off leaves the whole week off: the home hero goes `--:--`, the
   row says `OFF` in `dim`, and `AlarmScheduler` cancels the trigger.
6. All seven rows ring independently; times are wall-clock and survive DST by the rules in
   the README (unchanged by this screen).

Copy rules:

- Both actions write **time only** and never touch an on/off flag — a schedule edit must not
  silently turn Sunday on. The sentence under `COPY A TIME` says exactly that.
- No dialog and no confirmation: the rows above update in place, which is the confirmation.
- The source label is regenerated from the selected day, so `COPY TO …` can never act on a
  day the user is not looking at.

## 2. AlarmActivity — ringing (`mode = "alarm"`)

Purpose: one sentence, one number, one button. Nothing else may render.

Frame: status bar **hidden** (`insetsController.hide(statusBars())`), nav bar visible with
`bg`; `FLAG_KEEP_SCREEN_ON`; `screenBrightness = 0.9f`; no scroll; no entrance animation.

| # | Element | Height | Space above | Type | Colour | Notes |
|---|---|---|---|---|---|---|
| 1 | top spacer | 48 | — | — | — | after cutout inset |
| 2 | eyebrow | 18 | 0 | `L` tracking 0.10 | `accent` | `WAKE UP` |
| 3 | spacer | 8 | — | — | — | |
| 4 | clock | 88 | 0 | `D` `-0.02` em, `setLineSpacing(0, 0.86)` | `text` | `06:00`, 24 h, `fontFeatureSettings = "tnum"` |
| 5 | spacer | 16 | — | — | — | |
| 6 | hairline | 1 | 0 | — | `line` | width **40 dp**, left-aligned |
| 7 | spacer | 24 | — | — | — | |
| 8 | hint | 36 (2 lines) | 0 | `L` | `dim`; `accent` in the no-code state | `SCAN THE CODE IN THE OTHER ROOM` |
| 9 | flexible spacer | weight 1 | — | — | — | ≥ 120 dp on any supported screen |
| 10 | primary action | **120** | 0 | `A` `+0.04` em | fill `fill`, label `fillInk` | `SCAN TO STOP`, full width, 0 radius |
| 11 | spacer | 16 | — | — | — | |
| 12 | footnote | 36 (2 lines) | 0 | `L` | `faint` | `NO SNOOZE. 40 STEPS 90s AFTER YOU SCAN.` |
| 13 | bottom spacer | 40 | — | — | — | above nav-bar inset |

Total fixed height 451 dp. Types used: `D` 88, `A` 26, `L` 13 — **three sizes**.

### 2.1 States

| Element | Default | Pressed | Disabled | Error / no-code |
|---|---|---|---|---|
| Clock | `text` | n/a | n/a | — |
| Hint | `dim` | n/a | n/a | `accent`, copy `No code enrolled. Scan anything to stop.` |
| Primary action | `fill` / `fillInk` | `fillPress` / `fillInk`, 0 ms | **never disabled**; hide instead | label `SCAN TO ENROLL` |
| Footnote | `faint` | n/a | n/a | — |

`SCAN TO ENROLL` reuses the existing path: `ScanActivity` in enroll mode sets the barcode and
returns `RESULT_OK`; `AlarmActivity.onActivityResult` already treats `RESULT_OK` as dismissal.
No new behaviour, no new escape hatch — the user still has to walk to an object.

### 2.2 Forbidden on this screen

Secondary text of any size beyond the two `L` lines · a snooze control · a dismiss
control · settings access · a back affordance · a scrollbar · the notification shade
(hence hidden status bar) · any animation, including the clock. The clock updates once per
minute at most (the current 5 s tick is wasted work and a needless redraw).

---

## 3. Guard — same Activity (`mode = "guard"`)

Purpose: after the scan, prove movement. The number is the whole message.

| # | Element | Height | Space above | Type | Colour | Notes |
|---|---|---|---|---|---|---|
| 1 | top spacer | 48 | — | — | — | after cutout inset |
| 2 | eyebrow | 18 | 0 | `L` | `accent` | `STILL UP?` |
| 3 | spacer | 24 | — | — | — | |
| 4 | count | 88 | 0 | `D`, `MONOSPACE` bold, `-0.02` em | `text` | `40` → counts down |
| 5 | spacer | 8 | — | — | — | |
| 6 | unit | 18 | 0 | `L` | `dim` | `STEPS TO GO` (singular `STEP TO GO` at 1) |
| 7 | spacer | 24 | — | — | — | |
| 8 | progress | 4 | 0 | — | track `line`, fill `accent` | width = `(40 − remaining)/40 × 384`; 140 ms linear on change; 0 radius |
| 9 | flexible spacer | weight 1 | — | — | — | |
| 10 | fallback action | 64 | 0 | `A` | fill `fill` / `fillInk` | only when the step sensor is missing: `SCAN AGAIN` |
| 11 | spacer | 24 | 0 | — | — | between #10 and footnote (0 if #10 absent) |
| 12 | footnote | 36 (2 lines) | 0 | `L` | `faint` | `STOP MOVING AND THIS COMES BACK LOUDER.` |
| 13 | bottom spacer | 40 | — | — | — | |

**Sensor available** (normal): types `D` 88, `L` 13 — two sizes. No button at all; the screen
cannot be dismissed, only satisfied.
**Sensor missing**: adds `A` 26 for `SCAN AGAIN` — three sizes. As shipped there is no count and
no progress bar in this state: with no step source there is no number to show, and a `40` that
can never move is a lie. The `L` note takes the place of #4–#8.

Copy in the sensor-missing state: `STEP SENSOR OFF. SCAN THE CODE AGAIN.` replaces #6.

| Element | Default | Pressed | Disabled | Error |
|---|---|---|---|---|
| Count | `text` | n/a | n/a | — |
| Progress fill | `accent` | n/a | — | — |
| `SCAN AGAIN` | `fill` / `fillInk` | `fillPress` | never disabled | — |

On 0 steps: finish immediately, no "well done" screen, no sound. The absence of the alarm is
the reward.

---

## 4. ScanActivity — camera gate

Purpose: point at a barcode in a dark room. Two modes share one layout; only the title, the
`CANCEL` action and the result branch differ.

Frame: `FrameLayout`, preview **full-bleed**, letterbox scrim top and bottom, no status-bar
change (visible, `bg`), `FLAG_KEEP_SCREEN_ON`, brightness unchanged (camera works in the dark).

### 4.1 Layout

| # | Element | Height | Notes |
|---|---|---|---|
| 1 | preview | weight 1 (≈ 351 dp) | `PreviewView`, `MATCH_PARENT`, `SCALE_TYPE_FILL_CENTER` |
| 2 | frame strip | 180 | `FrameLayout`, transparent; contains the scan frame |
| 2a | scan frame | 300 × 180, centred | background transparent, 2 dp `accent` stroke, radius 0 |
| 3 | bottom area | weight 1.15 (≈ 405 dp) | vertical `LinearLayout`, bottom-aligned content, gutter `G` |
| 4 | title | 38 | `h1` 34/38, `text`: `SCAN THE CODE` (dismiss) / `SCAN A CODE TO USE` (enroll) |
| 5 | spacer | 8 | |
| 6 | hint row | 48 | left: `B` 16 `dim` `Hold it inside the frame`; right: text action `LIGHT` (see 4.3) |
| 7 | spacer | 4 | |
| 8 | status line | 18 | `L`; hidden (height 0) unless a state is active |
| 9 | spacer | 24 | |
| 10 | cancel | 48 | text action `CANCEL`, `dim`, left-aligned; **enroll mode only** |
| 11 | bottom spacer | 40 | |

Scrim bands: #1 and #3 are wrapped so that the area outside the 180 dp strip is covered by
`riseup_scrim` (flat 70 % black). Implement as three vertical bands — scrim / strip / scrim —
inside one `LinearLayout`, with the text column as the bottom band's content. No blur, no
`RenderEffect`.

### 4.2 States

| State | Frame stroke | Status line | Behaviour |
|---|---|---|---|
| Searching | 2 dp `accent` | hidden | default |
| Wrong code | 2 dp `accent` | `WRONG CODE` in `accent`, 18 dp, shown 1.6 s then hidden | no sound, no vibration, camera keeps running |
| Accepted | 2 dp `success` | `CODE ACCEPTED` in `success` | 400 ms hold, then `RESULT_OK` + `finish()` (0 ms if animations are off) |
| Camera denied | — | `CAMERA BLOCKED. ENABLE IT IN SETTINGS.` | `finish()` after 1.2 s |
| Camera failed | — | `CAMERA UNAVAILABLE` | `finish()` after 1.2 s |

Do **not** use `Toast` for wrong-code (current build does). A toast is 3.5 s of a floating
grey rectangle that covers the preview and confirms nothing at a glance; the status line is
inside the layout, at the same place every time.

### 4.3 Torch (optional, recommended)

Text action `LIGHT` / `LIGHT ON`, `L`, 48 dp touch, right side of the hint row, `dim` →
`accent` when on. Wires to `camera.cameraControl.enableTorch(true/false)`; no new dependency
(CameraX core is already present). If it cannot be wired in this pass, delete the action
entirely — do not ship a disabled control.

**As shipped** the action is `GONE` until the bound camera reports `hasFlashUnit()`, and hides
itself again if enabling the torch throws, so no device ever shows a control that does nothing.
On the emulator (no flash unit) the action is therefore absent.

### 4.4 Why there is no shutter

The camera is the control. A shutter button implies a capture step that does not exist, and
it would be the largest element on a screen whose only job is framing. `CANCEL` is
deliberately 48 dp and low contrast: the exit exists, it is not advertised.

---

## 5. Copy — every string

`res/values/strings.xml` is owned by the implementation pass; these are exact values.
`%1$s` placeholders are as noted. Uppercase strings are stored uppercase (the layout sets
`isAllCaps = false` on buttons so the resource text is the truth).

### 5.1 MainActivity

| Key | Value |
|---|---|
| `home_wordmark` | `RISEUP` |
| `home_state_on` | `ON` |
| `home_state_off` | `OFF` |
| `next_label` | `NEXT ALARM` (eyebrow over the hero time) |
| `home_next_none` | `No day is on` (line under `--:--`) |
| `home_no_time` | `--:--` (hero, when no day is on) |
| `home_hero_none` | `No alarm scheduled` (hero contentDescription only) |
| `schedule_none` | `No days on` |
| `schedule_every_day` | `Every day · %1$s` → `Every day · 07:00` |
| `schedule_weekdays` | `Mon–Fri · %1$s` → `Mon–Fri · 07:00` (exactly Mon–Fri on) |
| `schedule_days` | `%1$s · %2$s` → `Mon, Wed, Sat · 07:00` |
| `schedule_mixed` | `%1$d days · from %2$s` → `4 days · from 06:15` (differing times; earliest) |
| `week_title` | `WEEKLY SCHEDULE` (also the schedule row's first line) |
| `week_open` | `EDIT` |
| `week_open_cd` | `Weekly schedule. %1$s. Edit.` |
| `code_section` | `DISMISS CODE` |
| `code_none` | `No code enrolled. The alarm cannot be stopped.` |
| `code_enrolled` | `Enrolled %1$s` → `Enrolled 3F9…A21` |
| `code_enrol` | `SCAN TO ENROLL` |
| `code_replace` | `REPLACE CODE` |
| `code_clear` | `REMOVE CODE` |
| `code_clear_again` | `TAP AGAIN` |
| `test_section` | `DRY RUN` |
| `test_body` | `Fires in 10 seconds. Walk away from the phone.` |
| `test_action` | `TEST THE WHOLE CHAIN` |
| `test_armed` | `ARMED — FIRES IN 10s` |
| `req_ready` | `READY` |
| `req_fix_one` | `1 FIX NEEDED` |
| `req_fix_many` | `%1$d FIXES NEEDED` |
| `req_show` | `SHOW` |
| `req_hide` | `HIDE` |
| `req_summary_cd` | `%1$s. %2$s details.` |
| `req_section` | `REQUIRED` (retained for the doc's original heading; no longer rendered) |
| `req_exact` | `Exact alarms` |
| `req_fsi` | `Full-screen alarm` |
| `req_notifications` | `Notifications` |
| `req_camera` | `Camera` |
| `req_steps` | `Step sensor` |
| `req_battery` | `Battery unrestricted` |
| `req_fix` | `FIX` |
| `os_note` | `NOTHING OS: SETTINGS / APPS / RISEUP / BATTERY / UNRESTRICTED.` |

### 5.2 AlarmActivity

| Key | Value |
|---|---|
| `alarm_eyebrow` | `WAKE UP` |
| `alarm_hint` | `SCAN THE CODE IN THE OTHER ROOM` |
| `alarm_hint_none` | `NO CODE ENROLLED. SCAN ANYTHING TO STOP.` (stored uppercase — it is an `L`) |
| `alarm_action` | `SCAN TO STOP` |
| `alarm_action_none` | `SCAN TO ENROLL` |
| `alarm_footnote` | `NO SNOOZE. 40 STEPS 90s AFTER YOU SCAN.` |

### 5.3 Guard

| Key | Value |
|---|---|
| `guard_eyebrow` | `STILL UP?` |
| `guard_unit` | `STEPS TO GO` |
| `guard_unit_one` | `STEP TO GO` |
| `guard_footnote` | `STOP MOVING AND THIS COMES BACK LOUDER.` |
| `guard_no_sensor` | `STEP SENSOR OFF. SCAN THE CODE AGAIN.` |
| `guard_scan_again` | `SCAN AGAIN` |

### 5.4 ScanActivity

| Key | Value |
|---|---|
| `scan_title` | `SCAN THE CODE` |
| `scan_title_enroll` | `SCAN A CODE TO USE` |
| `scan_hint` | `Hold it inside the frame` |
| `scan_status_wrong` | `WRONG CODE` |
| `scan_status_ok` | `CODE ACCEPTED` |
| `scan_status_camera_denied` | `CAMERA BLOCKED. ENABLE IT IN SETTINGS.` |
| `scan_status_camera_failed` | `CAMERA UNAVAILABLE` |
| `scan_cancel` | `CANCEL` |
| `scan_torch` | `LIGHT` |
| `scan_torch_on` | `LIGHT ON` |

### 5.5 Notifications (AlarmService, for consistency)

| Key | Value |
|---|---|
| `notif_title_alarm` | `WAKE UP` |
| `notif_title_guard` | `STILL UP?` |
| `notif_text_alarm` | `Scan your code to stop` |
| `notif_text_guard` | `40 steps or the alarm returns` |

### 5.6 WeekScheduleActivity (§1.4)

| Key | Value |
|---|---|
| `week_done` | `DONE` (top-right, closes the screen) |
| `week_intro` | `Choose which days ring and at what time.` |
| `week_col_day` | `DAY` |
| `week_col_time` | `TIME` |
| `week_col_ring` | `RING` |
| `week_day_on` | `ON` |
| `week_day_off` | `OFF` |
| `week_time_cd` | `%1$s time %2$s. Change.` → `Monday time 07:00. Change.` |
| `week_ring_on_cd` | `%1$s rings %2$s. Turn off.` |
| `week_ring_off_cd` | `%1$s is off. Turn on.` |
| `week_select_cd` | `%1$s. Pick as copy source.` |
| `week_copy_section` | `COPY A TIME` |
| `week_copy_source` | `Source: %1$s %2$s. Tap a day to change the source. Times only; on/off stays.` |
| `week_copy_weekdays` | `COPY TO MON–FRI` |
| `week_copy_all` | `COPY TO ALL 7` |

Copy rules that generated these: no sentence longer than 12 words · no `!` · no "please" ·
no first person · digits for numbers · consequence before action · one instruction per
control.

---

## 6. Implementation notes (no UI decisions left)

1. **Type counts are testable**: a reviewer counts distinct `setTextSize` values per screen.
   Alarm = 88/26/13. Guard = 88/13 (+26 in fallback). Scan = 34/16/13. Main = 64/16/13.
   Week = 34/16/13.
2. **Colour access** only through `ContextCompat.getColor(this, R.color.riseup_*)`. The
   build currently hard-codes five hex values; all five must disappear.
3. **Squares, not glyphs**: the 8 dp state mark is `View` + `block(success)` — delete the
   `●`/`○` `label(...)` calls.
4. **No `AlertDialog`**: the dry-run confirmation and the clear-code confirmation become the
   inline `test_armed` line and the `TAP AGAIN` label state respectively.
5. **The time picker** stays `TimePickerDialog(..., true)` (24 h). It is the only platform
   dialog in the app and is acceptable because it is a full-screen-ish modal the user
   summoned deliberately. Style it by leaving it alone.
6. **Masked code**: `take(4) + "…" + takeLast(3)` (current `take(3)…takeLast(3)` truncates
   short codes to a single character). Monospace, no letter-spacing change.
7. **Order of building** should follow the vertical tables; every `gap(n)` uses the scale.
   Replace `gap(px: Int)` with `spacer(dp)` returning a `View` of height `dp(n)` — do not
   pass raw dp into a px-sized layout param (the current `gap()` does, which works only
   because `dp()` is applied inside).
8. **`setPadding` on the root is now owned by the insets listener**; screens add spacers,
   not padding.
9. **AlarmActivity clock**: schedule the next update at the next minute boundary, not every
   5 s. Cancel callbacks in `onDestroy` (already done).
10. **Accessibility**: every 8 dp square is decorative (`importantForAccessibility = NO`);
    its row carries the state as text (`Exact alarms, allowed`) via `contentDescription`.
    Primary button `contentDescription` is its own label — no extra text.

---

## 7. Definition of done

- [ ] Screenshot at 0 % ambient light: `SCAN TO STOP` readable at 1 m.
- [ ] Every colour in Kotlin comes from `R.color.riseup_*`.
- [ ] No `Color.parseColor`, no `AlertDialog`, no `Toast`, no emoji, no `●` glyph.
- [ ] No `elevation` other than 0; no `cornerRadius` other than 0.
- [ ] Greyscale screenshot: state still legible (word + square).
- [ ] Distinct type sizes per screen match §6.1 exactly.
- [ ] No interactive element smaller than 48 × 48 dp; measured in `Accessibility Scanner`.
- [ ] Nothing animates on the ringing screen, from first frame to dismissal.

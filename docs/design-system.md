# RiseUp — design system

A single-purpose instrument for one person at 06:00: half-asleep, in the dark, no glasses,
holding a phone whose only job is to make him walk. Everything below is optimised for
**read at arm's length in a dark room** and **hit with one thumb by someone not fully awake**.
This is the opposite of a dashboard, and most of the document is about what is refused.

Reference device: CMF Phone 2 Pro (Nothing OS 3.2 / Android 15), 1080 × 2400, ~400 dpi,
≈ 432 dp wide, gesture nav. Spec numbers assume 393–432 dp width; gutters are fixed, the
column stretches.

---

## 1. Sources, and what is taken from each

| # | Source | Taken | Refused |
|---|---|---|---|
| 1 | Material 3 — **Color system** <https://m3.material.io/styles/color/system/overview> | Roles are *pairs*: a surface colour is algorithmically matched to a text colour that already passes contrast. We keep the pairing discipline (every token is defined by the pair it sits on) and drop the rest. | Dynamic colour from wallpaper. An alarm must look identical every morning; a wallpaper-derived palette is a variable in a moment that needs zero variables. |
| 2 | Material 3 — **Type scale tokens** (values read from `TypeScaleTokens.kt`) <https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/TypeScaleTokens.kt> | The actual numbers (Display L 57/64 sp, Headline S 24/32, Body M 14/20, Label S 11/16 …) and the ratio thinking behind them: big steps between roles, tight tracking as size grows, loose tracking on small caps. Our ramp is a subset with the same logic. | 15 baseline + 15 emphasized styles. This app has six sizes total. |
| 3 | Material 3 — **Shape tokens** (`ShapeTokens.kt`: 4 / 8 / 12 / 16 / 28 / 32 / 48 dp) | Proof that the familiar "12dp card, 8dp button" look is a *brand-expressive* scale, not a usability requirement. Naming that is how we justify radius 0. | All of it. |
| 4 | Material 2 — **Dark theme** <https://m2.material.io/design/color/dark-theme.html> | Two hard rules kept verbatim: *"use dark grey rather than black"* and *desaturate the primary so it passes AA at every elevation*; also the measured target "dark surfaces and 100% white body text … at least 15.8:1". | Elevation overlays (semi-transparent white at 1/2/3 dp) and shadows. We flatten instead: borders, no glow. |
| 5 | **WCAG 2.1 SC 1.4.6** Contrast (Enhanced) <https://www.w3.org/WAI/WCAG21/Understanding/contrast-enhanced.html> | 7:1 for normal text (AAA), 4.5:1 for large text. Every ratio in §3 is computed, not eyeballed. | — |
| 6 | Android accessibility — **Touch target size** <https://support.google.com/accessibility/android/answer/7101858> | 48 × 48 dp ≈ 9 mm; recommended human target 7–10 mm; 8 dp minimum between targets. The ringing button is 120 dp: the whole point is that it cannot be missed. | — |
| 7 | Purdue University, **dark mode power study** (MobiSys 2021) <https://www.purdue.edu/newsroom/archive/releases/2021/Q3/dark-mode-may-not-save-your-phones-battery-life-as-much-as-you-think,-but-there-are-a-few-silver-linings.html> | The number that settles an argument: at 30–50 % brightness dark mode saves **3–9 %**; at 100 % brightness it saves **39–47 %**. So a dark alarm screen is **not** a battery feature — it is an eye feature. We stop claiming battery and design for luminance at 3 a.m. | "Pure black saves the most battery" folklore. We use `#0A0A0B`, not `#000000`, because pure black erases the 1 dp borders that replace shadows. |
| 8 | Apple HIG — **Dark Mode** <https://developer.apple.com/design/human-interface-guidelines/dark-mode> | Permission to be dark-only: Apple sanctions a permanently dark appearance "in rare cases", and warns to test with Increase Contrast. We take the exception (for a low-light instrument, not for media) and pay for it by holding AAA in dark. | The system-wide appearance toggle. |
| 9 | Apple — **Set an alarm in Clock** <https://support.apple.com/guide/iphone/set-an-alarm-iph2909d3a74/ios> · <https://support.apple.com/en-us/118444> | The information order of the settings screen: **the time is the object, every option is a row under it**, and Snooze is an explicit per-alarm option rather than a default. That is the correct hierarchy and we copy it. | Snooze. Existence of a second way to make the noise stop. |
| 10 | Dieter Rams, **Ten Principles** (Design Museum) <https://designmuseum.org/discover-design/all-stories/what-is-good-design-a-quick-look-at-dieter-rams-ten-principles> | "Good design makes a product understandable" (04) — no explanation of *how* to use the screen, the screen is the instruction. "As little design as possible" (10) — one control on the ringing screen. "Honest" (06) — the clock shows the real time, the counter shows real steps. | The nostalgia. We are not making a 1961 radio. |
| 11 | **Teenage Engineering** — Braun lineage / industrial UI <https://onlyonceshop.com/blog/from-braun-to-teenage-engineering> · <https://artdesignideas.com/teenage-engineering-design/> | Three transferable moves: (a) **one colour = one function**, never decoration; (b) the enclosure is visible — grids, ticks and rules are left showing; (c) controls are sized for a hand, not for a layout. | RAL candy colours, exposed screws cosplay, novelty. |
| 12 | **Nothing brand reference** (community, derived from official docs) <https://nothing.wiki/nothing/brand_reference> | The actual values: foundation **Pure Black / Window Grey `#B1B3B3` / N-Grey `#DCD7D2`**, one primary accent **N-Red `#C8102E`**; five typefaces "each with a distinct, non-overlapping job"; NType82 Headline is 40 pt+, **80 % leading, −20 tracking**, one- or two-word statements only. That "oversized statement + tight leading" is the signature, and it is exactly what a 06:00 clock needs. | NDot55 dot-matrix as body or UI type. At 13 sp on a 400 dpi phone in the dark, a dot font is a legibility tax paid for a logo. Also refused: five families and any bundled `.ttf` (see §4.1). |

### The one unavoidable derivation

N-Red `#C8102E` measures **3.36:1** on our background — unusable for text, and unusable
for a small label. Lifted to 7:1 by mixing with white it becomes `#E28392`, a dusty rose
that reads as *decoration*, not alarm. So RiseUp keeps Nothing's structure (monochrome
foundation + exactly one red) and moves the hue **10° toward orange**: `#FF6E52`, which
holds 7.17:1 on the background and still reads as *a red thing*. Vermilion, not crimson.
This is the only place where the brand is deliberately not followed, and the reason is
measured rather than aesthetic.

---

## 2. Five rules that generate the rest

1. **One control per moment.** The ringing screen has exactly one tappable thing.
2. **Luminance is the hierarchy.** Size and brightness carry emphasis; colour does not.
   There is one accent and it appears at most twice per screen.
3. **Borders instead of shadows.** On a near-black surface at 3 a.m. a shadow is invisible
   noise; a 1 dp line is a fact.
4. **Nothing moves unless movement carries information.** The ringing screen is static the
   instant it appears.
5. **Every word is an instruction or a fact.** No encouragement, no apology, no branding voice.

---

## 3. Colour

### 3.1 Dark (shipping)

| Token | Hex | Role | Measured |
|---|---|---|---|
| `riseup_bg` | `#0A0A0B` | 0 dp plane, window background, status + nav bars | — |
| `riseup_surface` | `#131315` | raised plane: pressed rows, inline panels | 1.07:1 vs bg |
| `riseup_surface_press` | `#1C1C1F` | pressed fill for rows on bg | 1.16:1 vs bg |
| `riseup_line` | `#2A2A2E` | 1 dp border / divider | 1.38:1 vs bg |
| `riseup_text` | `#F4F3EF` | primary text, primary button fill | **17.82:1** |
| `riseup_text_dim` | `#9B9BA1` | secondary copy, inactive labels | **7.16:1** |
| `riseup_text_faint` | `#85858B` | footer / OS-note only | **5.40:1** |
| `riseup_accent` | `#FF6E52` | urgency mark, failure, the armed state | **7.17:1** |
| `riseup_accent_ink` | `#0A0A0B` | label on accent fill | **7.17:1** on accent |
| `riseup_accent_press` | `#E05F45` | pressed accent fill | ink 5.55:1 |
| `riseup_action_fill` | `#F4F3EF` | primary action fill | **17.82:1** with ink |
| `riseup_action_ink` | `#0A0A0B` | primary action label | — |
| `riseup_action_press` | `#D9D8D3` | primary action, pressed | 13.87:1 with ink |
| `riseup_success` | `#3ECF6B` | 8 dp state square, "OK" | **9.75:1** |
| `riseup_warning` | `#FFB020` | 8 dp state square, "CHECK" | **10.82:1** |
| `riseup_scrim` | `#B3000000` | camera gate, flat 70 % | — |

`error` is `riseup_accent`. There is no second red.

### 3.2 Light (defined, not shipped)

| Token | Hex | Measured |
|---|---|---|
| `riseup_bg_light` | `#F4F2ED` (ivory — between Nothing's N-Grey and paper) | — |
| `riseup_surface_light` | `#FFFFFF` | 1.12:1 vs bg |
| `riseup_surface_press_light` | `#EAE7E0` | — |
| `riseup_line_light` | `#D8D4CB` | 1.32:1 vs bg |
| `riseup_text_light` | `#141414` | **16.47:1** |
| `riseup_text_dim_light` | `#5A5A5F` | **6.13:1** |
| `riseup_text_faint_light` | `#6E6E73` | 4.53:1 |
| `riseup_accent_light` | `#A62C15` | **6.27:1** bg / 7.02:1 surface |
| `riseup_accent_ink_light` | `#FFFFFF` | 7.02:1 on accent |
| `riseup_accent_press_light` | `#8C2411` | 8.84:1 with white |
| `riseup_action_fill_light` | `#141414` | **16.47:1** with ink |
| `riseup_action_ink_light` | `#F4F3EF` | — |
| `riseup_action_press_light` | `#33332F` | — |
| `riseup_success_light` | `#146B31` | 5.91:1 |
| `riseup_warning_light` | `#7A4F00` | 6.37:1 |

Light theme is **the same drawing, inverted**: the primary action is always "the highest
contrast pair available", i.e. paper-on-ink in light, ink-on-paper in dark. The accent is
the same hue (≈ 9°), darkened instead of lightened.

### 3.3 Usage rules

- Accent budget: on the **decision screens** (ringing, guard, scan) **≤ 2 instances**, and each
  must be a *state* or a *name of the event* — never a surface, never a heading, never a
  decoration. Ringing screen: 1 (`WAKE UP`). Guard: 2 (eyebrow + progress fill).
  On the **main screen** accent is a *status* colour — the `OFF` label, the failing readiness
  square, its `n FIXES NEEDED` words, the failing checklist squares and their `FIX` — plus at
  most **one accent action** (the enrol border). It never becomes a fill or a heading there.
  On the **week screen** the only accent is the 2 × 24 dp copy-source mark: one selection
  state, one instance.
- Semantic colours appear only as an 8 dp filled square plus a word. **Colour is never the
  only signal** — that is what keeps the checklist readable for a protanope at 6 a.m.
- No fills ever combine: no text on `surface` in `accent` (6.72:1 — close but we do not
  ship "close"), no accent on success, no tinted backgrounds behind accent text.
- Never pure `#000000` and never pure `#FFFFFF` in dark. Both are harsh on OLED at night
  and kill the border system.

---

## 4. Type

### 4.1 Families — three, each with exactly one job

The app cannot add font files (no `res/font`, no new dependencies), so v1 uses platform
families through `Typeface.create(...)`. That constraint produces a *useful* rule: a family
is chosen by the kind of number it has to show.

| Family | Android name | Job | Why |
|---|---|---|---|
| Condensed grotesque | `sans-serif-condensed` | the big numerals (clock, time) | Narrow digits let `06:00` sit at 88 sp without touching the edges, and the compressed counters read as instrument, not interface. |
| Text grotesque | `sans-serif` / `sans-serif-medium` | everything read as a sentence | Roboto is metrically sane, hinting is good at 13 sp, and it is already on the device. |
| Monospace | `monospace` | the step counter and the masked code | Digits must not reflow while the user is walking. Tabular by construction. |

No NDot. Nothing's dot-matrix is a 40 pt+ display face for a brand; at 13 sp caps in a dark
bathroom it is a legibility tax. We borrow the **grid discipline** (square, aligned, no
softness), not the dots.

### 4.2 The ramp — six sizes in the entire app

| Token | sp | line height (dp) | weight | tracking (em) | family | Used for |
|---|---|---|---|---|---|---|
| `t-display` | 88 | 88 | 700 | −0.02 | condensed | ringing clock; guard step count |
| `t-hero` | 64 | 66 | 700 | −0.02 | condensed | the alarm time on the main screen |
| `t-headline` | 34 | 38 | 700 | −0.01 | sans-serif | scan screen title (one line, one job) |
| `t-action` | 26 | 28 | 700 | +0.04 | sans-serif | the one primary button label |
| `t-body` | 16 | 24 | 400 | 0 | sans-serif | one supporting sentence; settings row label |
| `t-label` | 13 | 18 | 700 | +0.10 | sans-serif, UPPERCASE | eyebrows, section heads, secondary buttons, footnotes |

Rules:

- **The ringing screen uses exactly three of these**: `t-display` (88), `t-action` (26),
  `t-label` (13). Nothing else may appear on it. The guard screen likewise.
- Uppercase is a *label* device only (`t-label`) and always tracked +0.10 em. Uppercase at
  body size is a shout; at 13 sp with tracking it is a marking.
- Negative tracking only above 32 sp. Below 16 sp, tracking is ≥ 0.
- Numerals use `fontFeatureSettings = "tnum"` where the family supports it; the monospace
  family is tabular by design so the counter never jitters.
- Line height ≤ 1.1 × size above 32 sp (NType82 runs 0.8; we stop at 0.9 because Android
  clips descenders at 0.8 with `includeFontPadding = false`).
- Minimum size anywhere: **13 sp**. There is no 11 sp, no 10 sp, no legal-text-at-6-am.

### 4.3 Sizes against the real problem

At 400 dpi, 88 sp is ≈ 14 mm of em box and ≈ 9.5 mm of cap height — legible from 2–3 m,
i.e. from the bed to the bathroom counter. 13 sp is ≈ 2 mm of em box; its 1.4 mm caps are
readable at ~50 cm, which is why the only 13 sp text on the ringing screen is a footnote: the
instruction is carried by a 120 dp control and a 26 sp label, not by a sentence.

---

## 5. Spacing

Base unit **4 dp**; every gap, pad and inset is a multiple of 4. The working set is eight
values — no others exist:

`4 · 8 · 12 · 16 · 24 · 32 · 48 · 64 · 96`

| Use | Value |
|---|---|
| Screen gutter (left/right) | **24** |
| Space between a section head and its content | 8 |
| Space between rows inside a group | 0 (1 dp divider) |
| Space between groups | **32** |
| Space under the last element | 48 |
| Icon/square to label | 12 |
| Minimum gap between two touch targets | 8 |
| Primary button inset from screen bottom | 40 (+ nav bar inset) |

Grid: single column. No 12-column grid, no cards side by side, no two-column settings. A
one-column list is the only layout this app needs, and it removes every alignment bug.

---

## 6. Shape

**Corner radius = 0 dp, everywhere.** Buttons, rows, dialogs, the scan frame, badges. No
exceptions in v1.

Reason: radius is not free — it encodes "soft, app-like, friendly". This app's visual
argument is the opposite: hard, mechanical, already-running. The familiar
12-on-cards / 8-on-buttons / pill-on-chips stack (Material 3's expressive corner scale) is
precisely the tell that a screen was assembled from a component gallery rather than drawn
for a purpose. Zero is also the only value that cannot drift: a developer who wants 2 dp has
to argue for it.

Borders do the work instead: 1 dp `riseup_line` for containers and dividers. A 1 dp border
plus 0 radius is legible at 3 a.m.; a shadow is not.

---

## 7. Elevation

**0 dp. No shadows, no outlines-as-depth, no overlays.**

- Depth is communicated by a flat step in value: `bg` → `surface` (1.07:1) — felt, not seen;
  the 1 dp border does the actual work of separating a region.
- Anything that would be a "card" is instead a **bordered region** (1 dp) or simply a
  section separated by 32 dp and a `t-label` heading.
- Buttons: `stateListAnimator = null`, `elevation = 0f`, background replaced by a
  `GradientDrawable`. Without this, `android.widget.Button` under `Theme.Material` animates
  its own elevation on press — the single most common way a flat design leaks shadows.
- **No dialogs at all**, including for destructive actions. Clearing the code is a two-step
  in-place confirmation (`CLEAR` → `TAP AGAIN`, 4 s window); the dry-run confirmation is an
  inline `ARMED — FIRES IN 10s` line. A modal at 6 a.m. asks for a decision the user cannot
  make, and every dialog is one more surface to style.

---

## 8. Iconography

- Grid **24 × 24 dp**, live area 20 × 20, **1.5 dp stroke**, butt caps, miter joins, no
  fills except where a state is literally filled.
- One weight. No filled/outlined pairs of the same icon, no rounded icon set mixed with
  square type.
- Colour: `riseup_text_dim` at rest, `riseup_text` when active, `riseup_accent` when the
  icon *is* the failure. Never two-tone, never gradient-filled.
- **No emoji, anywhere, ever.** They are font-dependent, break the baseline grid, carry a
  tone (🙂) that contradicts the message, and render differently on every ROM.
- **No sparkles / "AI" glyphs.** The app contains no intelligence and must not imply any.
- State marks are **8 dp filled squares** drawn as `View`s with a flat colour — not the
  `●` character (the current build's version). A glyph dot depends on font metrics, sits on
  a text baseline, and shifts when the label wraps.
- The existing `ic_alarm.xml` stays, but only as the **notification small icon**: 24 dp
  canvas, single-path, tinted `#FFFFFF` at runtime. It must not appear in-app at 48 dp with
  a baked `#FF5A3C` fill.
- New icons required by this system: **none**. If one is ever needed, it is drawn as a
  1.5 dp stroke path on the 24 dp grid, never downloaded.

---

## 9. Motion

| Token | Duration | Easing | Where |
|---|---|---|---|
| `m-instant` | 0 ms | — | press fills, state squares, the clock, the step count |
| `m-quick` | 140 ms | `cubic-bezier(0.2, 0, 0, 1)` | scan result line colour change; the guard progress bar |
| `m-screen` | 220 ms | `cubic-bezier(0.2, 0, 0, 1)` | leaving the scan screen after a hit |

Only three durations. `cubic-bezier(0.2, 0, 0, 1)` is Material's standard easing, chosen
because it decelerates into rest without overshoot — no bounce, ever.

**Motion is banned where the user is not yet awake:**

- No entrance animation on `AlarmActivity`. The screen must be final on frame 1. A 300 ms
  fade-in is 300 ms of a person trying to read a moving target.
- No pulsing, breathing, glowing or shaking the primary button. A button that moves is a
  button that is harder to hit.
- No numeral roll / odometer on the clock or the step counter. Numbers change by swapping.
- No animated gradient, aurora, parallax, confetti, particle, Lottie or video background.
- No scale-on-press. Press feedback is a **fill change at 0 ms**; the finger already knows.
- No skeleton/shimmer. Nothing loads.
- Honour reduced motion: if `ValueAnimator.areAnimatorsEnabled()` is false, all durations
  collapse to 0 and the scan-confirm delay drops from 400 ms to 0.

---

## 10. Copy

| Rule | Bad | Good |
|---|---|---|
| Imperative, ≤ 3 words on a control | "Tap here to dismiss your alarm" | `SCAN TO STOP` |
| State the consequence, not the action taken | "Alarm has been dismissed" | `NEXT TUE 06:00` |
| Digits, not words | "forty steps" | `40 STEPS` |
| No encouragement | "You've got this!" | — (delete) |
| No apology, no "please", no "sorry" | "Please scan your code" | `SCAN THE CODE` |
| No first person plural | "We'll wake you at 06:00" | `NEXT TUE 06:00` |
| Uppercase for labels and buttons, sentence case for sentences | `TURN OFF` (button) · `No code enrolled.` (body) | |
| One sentence of support copy per screen, max 2 lines at 345 dp | | |

The ringing screen's entire vocabulary: `WAKE UP` · the time · `SCAN THE CODE IN THE OTHER
ROOM` · `SCAN TO STOP` · `NO SNOOZE. 40 STEPS 90s AFTER YOU SCAN.`

---

## 11. Banned patterns (checklist for review)

| Banned | Why, in one line |
|---|---|
| Purple/indigo gradients | A gradient with no light source is decoration, and a second hue destroys the single-accent signal. |
| Glassmorphism / blur / translucency | Costs GPU frames, is invisible on near-black, and drops text contrast unpredictably. |
| "AI" sparkle iconography | Claims intelligence the app does not have; the algorithm is a barcode comparison. |
| Emoji in UI | Font-dependent, off-grid, and they carry a tone that contradicts an alarm. |
| Drop shadows on flat cards | At 3 a.m. they are invisible; by day they fake depth that does not exist. |
| 12+ accent colours | When everything is highlighted, the primary control stops being findable. |
| Rounded-corner-everything (12/8/pill stack) | Uniform radius flattens hierarchy and is the signature of an assembled template. |
| Fake stats or metrics ("97% of users wake up") | A lie in a product whose only currency is trust at 6 a.m. |
| Motivational copy ("You've got this!") | The user is asleep and needs an instruction, not a coach. |
| Hero images / illustrations / 3D blobs | Nothing to depict; the content is a time and a number. |
| Onboarding carousel | Delays the one action; the checklist on the main screen is the onboarding. |
| Pill-shaped chips | Consume horizontal space, add no affordance a rectangle lacks. |
| Bottom navigation / tabs | Four screens, three of them transient. |
| Skeleton loaders / shimmer | There is no network and nothing loads. |
| Animated/aurora backgrounds | Motion plus light at 3 a.m., for zero information. |
| Dark-pattern toggles, "maybe later" | The app's ethics are the point; a dismissible guard defeats the guard. |
| Custom display fonts from the internet | Licensing risk, APK weight, and an unproven glyph set at 13 sp. |
| Colour as the only state signal | 8 % of men cannot read it; always pair the square with a word. |
| Text below 13 sp | Unreadable without glasses at arm's length. |

---

## 12. Night-use acceptance test

Run this at 06:00, in the actual bathroom, before shipping a change:

1. Phone flat on the counter, 2 m away, room dark: can you read the time without lifting the
   phone? (88 sp, 17.82:1 — yes.)
2. Without glasses, at arm's length: is `SCAN TO STOP` readable? (26 sp, 17.82:1 — yes.)
3. Thumb only, no second hand, no looking: does the thumb land inside the button?
   (120 dp tall, full width, 40 dp above the nav bar — yes.)
4. Is anything moving? (It must not be.)
5. Squint: is there exactly one bright object? (The button. If the clock and the button
   compete, the button is not bright enough.)
6. Greyscale screenshot: can you still tell OK from broken? (Word + square, not colour.)

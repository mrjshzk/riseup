# RiseUp — type, spacing and the Kotlin numbers

Everything here is a literal you can paste. Values match `docs/design-system.md` §4–§7 and
`design/colors.xml`.

---

## 1. Units

- **Layout in `dp`.** Screen, padding, gaps, heights, strokes.
- **Text in `sp`.** Always `TypedValue.COMPLEX_UNIT_SP`, never `COMPLEX_UNIT_PX` and never
  `setTextSize(Float)` without a unit (that one defaults to SP, but it is ambiguous to
  read — be explicit).
- **Never touch px directly.** No `density` arithmetic outside the two helpers in §2.

---

## 2. The helpers

The codebase already has this, in `MainActivity` and `AlarmActivity`:

```kotlin
private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
```

Keep it. Three additions, in that same style:

```kotlin
/** dp -> px. Truncates, which is correct for padding/borders: never up. */
private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** sp -> px for anything that must be set in px (rare: span widths, custom draw). */
private fun sp(v: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics).toInt()

/** Text sizes: always pass through this so the unit can never be forgotten. */
private fun TextView.size(value: Float) = apply {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
}
```

Notes that matter:

- `density` on the reference device (400 dpi) is **2.5**. `dp(24)` = 60 px, `dp(1)` = 2 px
  (wait — 2.5 → 2 after truncation). A 1 dp hairline therefore renders 2 px, which is
  correct and intentionally crisp; do not "round up" to 3.
- Do **not** use `Math.round` in `dp()`. Half-pixel paddings blur 1 dp borders.
- `resources.displayMetrics.density` is stable for an Activity; do not re-read it per view.

---

## 3. The spacing scale, as px

| token | dp | px @2.0 | px @2.5 (CMF) | px @3.0 |
|---|---|---|---|---|
| `space-1` | 1 | 2 | 2 | 3 |
| `space-2` | 2 | 4 | 5 | 6 |
| `space-4` | 4 | 8 | 10 | 12 |
| `space-8` | 8 | 16 | 20 | 24 |
| `space-12` | 12 | 24 | 30 | 36 |
| `space-16` | 16 | 32 | 40 | 48 |
| `space-24` | 24 | 48 | 60 | 72 |
| `space-32` | 32 | 64 | 80 | 96 |
| `space-40` | 40 | 80 | 100 | 120 |
| `space-48` | 48 | 96 | 120 | 144 |
| `space-56` | 56 | 112 | 140 | 168 |
| `space-64` | 64 | 128 | 160 | 192 |
| `space-66` | 66 | 132 | 165 | 198 |
| `space-88` | 88 | 176 | 220 | 264 |
| `space-96` | 96 | 192 | 240 | 288 |
| `space-120` | 120 | 240 | 300 | 360 |

There is no `space-20`, no `space-28`, no `space-36`. If a design needs one, the design is
wrong. `66` and `88` exist only as *line heights* (see §4), never as gaps.

**Screen gutter: 24 dp.** At 432 dp width that leaves 384 dp of column; at 360 dp, 312 dp.
Both are inside the 45–75 character measure at 16 sp.

---

## 4. The type ramp, as Kotlin

Six styles. The `Typeface` objects are built once per Activity, not per `TextView`.

```kotlin
// families: three, one job each
private val condensed by lazy { Typeface.create("sans-serif-condensed", Typeface.BOLD) }
private val textBold  by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
private val textReg   by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }
private val mono      by lazy { Typeface.MONOSPACE }
```

| token | sp | `letterSpacing` | line height | typeface | `setLineSpacing` |
|---|---|---|---|---|---|
| `t-display` | **88f** | `-0.02f` | 88 dp | `condensed` | `setLineSpacing(0f, 0.86f)` |
| `t-hero` | **64f** | `-0.02f` | 66 dp | `condensed` | `setLineSpacing(0f, 0.86f)` |
| `t-headline` | **34f** | `-0.01f` | 38 dp | `textBold` | `setLineSpacing(0f, 1.0f)` |
| `t-action` | **26f** | `+0.04f` | 28 dp | `textBold` | `setLineSpacing(0f, 1.0f)` |
| `t-body` | **16f** | `0f` | 24 dp | `textReg` | `setLineSpacing(0f, 1.15f)` |
| `t-label` | **13f** | `+0.10f` | 18 dp | `textBold` + `isAllCaps = true` | `setLineSpacing(0f, 1.1f)` |

Why these numbers, stated once:

- `letterSpacing` is in **em**, not sp — `TextView.letterSpacing = 0.10f` means 0.10 em, i.e.
  ≈ 1.3 px of tracking at 13 sp. Do not convert it by hand.
- Negative tracking above 32 sp only. At 88 sp, `-0.02f` pulls `06:00` in by ≈ 9 dp, which
  is the difference between a centred block and a crowded one on a 360 dp screen.
- `setLineSpacing(0f, 0.86f)` tightens the default line box by ~14 % so the 88 sp numeral
  does not push the hint below it off the bottom of a 640 dp screen. If a localised string
  clips descenders, raise the multiplier to 0.92 — never raise the font size.
- `isAllCaps = true` on `t-label` only. Elsewhere, `isAllCaps = false` **explicitly**:
  `android.widget.Button` under `Theme.Material` defaults to all-caps via the theme, so
  forgetting this silently uppercases body copy.

A label factory to copy:

```kotlin
private fun label(
    text: CharSequence,
    size: Float,
    color: Int,
    typeface: Typeface,
    tracking: Float = 0f,
    caps: Boolean = false,
    lineHeightMult: Float = 1.0f
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    setTextColor(color)
    this.typeface = typeface
    letterSpacing = tracking
    isAllCaps = caps
    includeFontPadding = false
    setLineSpacing(0f, lineHeightMult)
    setPadding(0, 0, 0, 0)
    breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
    hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
}
```

`breakStrategy = SIMPLE` and no hyphenation: at these sizes a hyphenated word looks like a
bug, and the copy is short enough that it never needs it.

---

## 5. Component dimensions

| Component | Height | Padding L/R | Text | Radius | Border |
|---|---|---|---|---|---|
| Primary action (alarm / guard) | **120 dp** | 24 | `t-action` 26 | 0 | none |
| Primary action (guard fallback) | **64 dp** | 24 | `t-action` 26 | 0 | none |
| Secondary action (main screen) | **56 dp** | 20 | `t-label` 13 | 0 | 1 dp `riseup_line` |
| Text action (`FIX`, `CLEAR`, `CANCEL`) | **48 dp** (touch) | 12 | `t-label` 13 | 0 | none |
| Settings row | **48 dp** min | 0 | `t-body` 16 | 0 | 1 dp divider below |
| Section head | 18 dp | 0 | `t-label` 13 | — | none |
| State square | **8 × 8 dp** | — | — | 0 | none |
| Divider | 1 dp | — | — | — | `riseup_line` |
| Alarm clock line | 88 dp | 0 | `t-display` | — | — |
| Scan frame | 300 × 180 dp | — | — | 0 | 2 dp `riseup_accent` |

All heights are multiples of 8 except the 18 dp section head (a text line, not a box).

---

## 6. Backgrounds: the only two shapes in the app

There is no card component, no shadow, no ripple drawable. Two builders cover every surface:

```kotlin
/** Flat fill, optional 1 dp border, always radius 0. */
private fun block(fill: Int, stroke: Int? = null, strokeDp: Int = 1) = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = 0f
    setColor(fill)
    if (stroke != null) setStroke(dp(strokeDp), stroke)
}

/** Pressed / default pair for anything tappable without a platform ripple. */
private fun pressable(v: View, normal: Int, pressed: Int, stroke: Int? = null) {
    v.background = block(normal, stroke)
    v.isClickable = true
    v.setOnTouchListener { view, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.background = block(pressed, stroke)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                view.background = block(normal, stroke)
        }
        false // let the click listener still fire
    }
}
```

If a platform `Button` is used anywhere (the scan fallback), neutralise it:

```kotlin
Button(this).apply {
    stateListAnimator = null     // kills the Material elevation animator
    elevation = 0f
    isAllCaps = false
    background = block(fill, stroke, 1)
    setPadding(dp(24), 0, dp(24), 0)
}
```

Press uses a **fill change at 0 ms**. No `RippleDrawable`, no scale, no alpha. The ripple is
a circular reveal that is invisible on a 120 dp rectangle in the dark and adds a frame of
animation to the one moment that must be instant.

---

## 7. Window setup (per Activity)

```kotlin
window.setBackgroundDrawable(ColorDrawable(getColor(R.color.riseup_bg)))
window.statusBarColor = getColor(R.color.riseup_bg)
window.navigationBarColor = getColor(R.color.riseup_bg)
if (Build.VERSION.SDK_INT >= 30) {
    window.insetsController?.setSystemBarsAppearance(
        0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
           WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
    )
}
```

`AlarmActivity` additionally:

```kotlin
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
window.attributes = window.attributes.apply { screenBrightness = 0.9f } // readable at 1 m
```

`screenBrightness = 0.9f` overrides auto-brightness for this window only, and is the single
most effective legibility change for a phone lying flat on a bathroom counter. Reset it by
finishing the Activity (it is per-window, so nothing leaks).

Inset handling — no `fitsSystemWindows`, one listener:

```kotlin
root.setOnApplyWindowInsetsListener { v, insets ->
    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
    v.setPadding(dp(24) + bars.left, dp(24) + bars.top, dp(24) + bars.right, dp(24) + bars.bottom)
    insets
}
```

(The 24 top/bottom above is the screen's own gutter; each screen then adds its internal
40/48/96 dp as spacers, never as padding on the root — see `docs/ui-spec.md`.)

---

## 8. Colours in Kotlin

```kotlin
private fun c(@ColorRes id: Int) = ContextCompat.getColor(this, id)

label("WAKE UP", 13f, c(R.color.riseup_accent), textBold, tracking = 0.10f, caps = true)
```

Rules:

- No `Color.parseColor("#...")` in Kotlin. The current screens hard-code
  `#0B0B0C / #F5F5F5 / #9AA0A6 / #FF5A3C / #5F6368` — every one of those is replaced by a
  token, and the hex must not reappear.
- `accent_ink` is used only on an accent fill. `action_ink` only on `action_fill`.
- For a tinted compound drawable: `DrawableCompat.setTint(drawable, c(R.color.riseup_text_dim))`.

---

## 9. Quick reference: the six numbers that define the ringing screen

```
gutter      24 dp
eyebrow     13 sp / 0.10 em / accent
clock       88 sp / -0.02 em / text / 88 dp line
button      120 dp tall, full width, action_fill, 26 sp action_ink
button base 40 dp above nav bar
footnote    13 sp / 0.10 em / text_faint
```

Everything else on that screen is empty space, deliberately.

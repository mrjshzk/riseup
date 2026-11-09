package com.mrjshzk.riseup

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Layout
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

/**
 * The design system, in Kotlin. One place for the six type sizes, the eight spacing
 * values, the flat block, the 0 ms press and the three window calls, so no screen can
 * invent a seventh size or a twelfth hex.
 *
 * Sources: docs/design-system.md §3–§9, design/type-and-spacing.md §2–§8.
 */

/** dp -> px. Truncates: a 1 dp hairline must never round up into a 3 px blur. */
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** Colour by token. No screen may call Color.parseColor. */
fun Context.token(id: Int): Int = ContextCompat.getColor(this, id)

class Ui(private val ctx: Context) {

    // ---------------------------------------------------------------- families

    /** Condensed grotesque: the big numerals, and nothing else. */
    val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    /** Text grotesque, medium: headlines, labels, button copy. */
    val textBold: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /** Text grotesque, regular: sentences. */
    val textReg: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    /** Monospace: counters and masked codes; tabular by construction. */
    val mono: Typeface = Typeface.create("monospace", Typeface.NORMAL)
    val monoBold: Typeface = Typeface.create("monospace", Typeface.BOLD)

    fun dp(v: Int): Int = ctx.dp(v)

    fun c(@ColorRes id: Int): Int = ctx.token(id)

    // ---------------------------------------------------------------- type ramp
    // Six sizes for the whole app: 88 / 64 / 34 / 26 / 16 / 13. There is no seventh.

    fun text(
        value: CharSequence,
        sizeSp: Float,
        color: Int,
        face: Typeface,
        tracking: Float = 0f,
        caps: Boolean = false,
        lineMult: Float = 1f,
        tabular: Boolean = false
    ): TextView = TextView(ctx).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = face
        letterSpacing = tracking
        isAllCaps = caps
        includeFontPadding = false
        setLineSpacing(0f, lineMult)
        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        if (tabular) fontFeatureSettings = "tnum"
        setPadding(0, 0, 0, 0)
    }

    /** 88 / 88, -0.02 em, condensed. Ringing clock, guard count. */
    fun display(value: CharSequence, color: Int, face: Typeface = condensed): TextView =
        text(value, 88f, color, face, tracking = -0.02f, lineMult = 0.86f, tabular = true)

    /** 64 / 66, -0.02 em, condensed. The alarm time on the main screen. */
    fun hero(value: CharSequence, color: Int): TextView =
        text(value, 64f, color, condensed, tracking = -0.02f, lineMult = 0.86f, tabular = true)

    /** 34 / 38, -0.01 em. Scan title. */
    fun headline(value: CharSequence, color: Int): TextView =
        text(value, 34f, color, textBold, tracking = -0.01f)

    /** 26 / 28, +0.04 em. The one primary button label. */
    fun action(value: CharSequence, color: Int): TextView =
        text(value, 26f, color, textBold, tracking = 0.04f)

    /** 16 / 24. One supporting sentence, settings row. */
    fun body(value: CharSequence, color: Int, face: Typeface = textReg): TextView =
        text(value, 16f, color, face, lineMult = 1.15f)

    /** 13 / 18, +0.10 em, caps. Eyebrows, section heads, secondary actions, footnotes. */
    fun label(value: CharSequence, color: Int, tracking: Float = 0.10f): TextView =
        text(value, 13f, color, textBold, tracking = tracking, caps = true, lineMult = 1.1f)

    // ---------------------------------------------------------------- spacing

    /** A spacer from the eight-value scale. Never a height that is not a token. */
    fun spacer(h: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(h))
    }

    /** The flexible gap that absorbs whatever is left. */
    fun flex(): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    /** 1 dp `line`, full width. Divider or rule. */
    fun divider(): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        setBackgroundColor(c(R.color.riseup_line))
    }

    /** 1 dp rule of a fixed width, left-aligned (the ringing screen's 40 dp mark). */
    fun rule(widthDp: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(1))
        setBackgroundColor(c(R.color.riseup_line))
    }

    /** An 8 dp state square. Flat fill, no radius, never the `●` glyph. */
    fun square(color: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
        background = block(color)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    // ---------------------------------------------------------------- surfaces

    /** Flat fill, optional 1 dp border, always radius 0. */
    fun block(fill: Int, stroke: Int? = null, strokeDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 0f
            setColor(fill)
            if (stroke != null) setStroke(dp(strokeDp), stroke)
        }

    /**
     * Press feedback: a fill change at 0 ms, no ripple, no scale, no elevation.
     * Every tappable surface in the app goes through here, which is why no
     * android.widget.Button (and its stateListAnimator) exists in the build.
     */
    fun pressable(v: View, normal: Int, pressed: Int, stroke: Int? = null) {
        v.background = block(normal, stroke)
        v.isClickable = true
        v.isFocusable = true
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.background = block(pressed, stroke)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.background = block(normal, stroke)
            }
            false
        }
    }

    // ---------------------------------------------------------------- components

    /** Primary action: 120 dp (alarm) or 64 dp (guard fallback). label / labelInk pair. */
    fun primary(heightDp: Int, value: CharSequence): TextView = text(
        value, 26f, c(R.color.riseup_action_ink), textBold, tracking = 0.04f
    ).apply {
        gravity = android.view.Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)
        )
        pressable(this, c(R.color.riseup_action_fill), c(R.color.riseup_action_press))
        contentDescription = value
    }

    /** Secondary action: 56 dp, 1 dp `line` border, 13 sp label. */
    fun secondary(value: CharSequence, accent: Boolean = false): TextView = text(
        value, 13f, c(if (accent) R.color.riseup_accent else R.color.riseup_text), textBold,
        tracking = 0.10f, caps = true, lineMult = 1.1f
    ).apply {
        gravity = android.view.Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        )
        pressable(
            this,
            c(R.color.riseup_bg),
            c(R.color.riseup_surface_press),
            c(if (accent) R.color.riseup_accent else R.color.riseup_line)
        )
        contentDescription = value
    }

    /**
     * Text action: 13 sp label, 48 dp touch, `dim` at rest, `text` on press.
     * [flat] leaves the resting fill transparent, for actions that sit on the scrim or on
     * a surface that is not `bg` — a `bg` rectangle over black reads as a button.
     */
    fun textAction(
        value: CharSequence,
        widthDp: Int? = null,
        color: Int? = null,
        flat: Boolean = false
    ): TextView = text(
        value, 13f, color ?: c(R.color.riseup_text_dim), textBold,
        tracking = 0.10f, caps = true, lineMult = 1.1f
    ).apply {
        gravity = android.view.Gravity.CENTER
        if (widthDp != null) {
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(48))
        } else {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)
            )
        }
        setPadding(dp(12), 0, dp(12), 0)
        pressable(
            this,
            if (flat) Color.TRANSPARENT else c(R.color.riseup_bg),
            c(R.color.riseup_surface_press)
        )
    }

    /** Section head: 18 dp line of 13 sp caps, `dim`, 8 dp above its content. */
    fun sectionHead(value: CharSequence): TextView = text(
        value, 13f, c(R.color.riseup_text_dim), textBold,
        tracking = 0.10f, caps = true, lineMult = 1.1f
    ).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(18)
        )
    }
}

// ---------------------------------------------------------------- window

/**
 * Edge-to-edge on `riseup_bg`, bars dark, no light scrim. Called by every screen.
 * The status bar can be hidden for the ringing screen only.
 */
fun Activity.paintWindow(hideStatusBar: Boolean = false) {
    window.setBackgroundDrawable(ColorDrawable(token(R.color.riseup_bg)))
    window.statusBarColor = token(R.color.riseup_bg)
    window.navigationBarColor = token(R.color.riseup_bg)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= 29) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
    if (Build.VERSION.SDK_INT >= 30) {
        // Only valid once a decor view exists: call this after setContentView().
        try {
            val controller = window.insetsController
            if (controller != null) {
                controller.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
                if (hideStatusBar) controller.hide(WindowInsets.Type.statusBars())
            }
        } catch (e: Exception) {
            // A window without a decor view: the theme already painted it.
        }
    }
}

/**
 * The FrameLayout every screen starts from: `bg`, and the 24 dp gutter plus the
 * system-bar insets applied once. Screens then add spacers, never padding.
 */
fun Activity.insetRoot(gutterDp: Int = 24): FrameLayout {
    val root = FrameLayout(this)
    root.setBackgroundColor(token(R.color.riseup_bg))
    root.setOnApplyWindowInsetsListener { v, insets ->
        val left: Int
        val top: Int
        val right: Int
        val bottom: Int
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            left = bars.left
            top = bars.top
            right = bars.right
            bottom = bars.bottom
        } else {
            @Suppress("DEPRECATION")
            left = insets.systemWindowInsetLeft
            @Suppress("DEPRECATION")
            top = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            right = insets.systemWindowInsetRight
            @Suppress("DEPRECATION")
            bottom = insets.systemWindowInsetBottom
        }
        v.setPadding(dp(gutterDp) + left, dp(gutterDp) + top, dp(gutterDp) + right,
            dp(gutterDp) + bottom)
        insets
    }
    return root
}

/** Bottom nav-bar inset only, for screens that paint full-bleed behind the bars. */
fun Activity.barInsets(onInsets: (left: Int, top: Int, right: Int, bottom: Int) -> Unit) {
    window.decorView.setOnApplyWindowInsetsListener { _, insets ->
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            onInsets(bars.left, bars.top, bars.right, bars.bottom)
        } else {
            @Suppress("DEPRECATION")
            onInsets(
                insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom
            )
        }
        insets
    }
}

package com.mrjshzk.riseup

import android.app.Activity
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The ringing screen and the guard. One sentence, one number, one control.
 * Spec: docs/ui-spec.md §2 and §3. Behaviour (barcode gate, 40 steps, 90 s guard,
 * no snooze, no dismiss, Back swallowed) is unchanged by this pass.
 *
 * In guard mode this screen is only a readout: [AlarmService] counts the steps and
 * [AlarmScheduler] owns the escalation, so neither depends on this Activity being
 * on screen.
 */
class AlarmActivity : Activity() {

    companion object {
        const val EXTRA_MODE = "mode"
        private const val REQ_SCAN = 11
        private const val STEPS_TARGET = AlarmService.STEPS_TARGET

        fun launch(ctx: Context, mode: String) {
            val i = Intent(ctx, AlarmActivity::class.java)
                .putExtra(EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            try {
                ctx.startActivity(i)
            } catch (e: Exception) {
                // The full-screen-intent notification is the fallback path.
            }
        }
    }

    private lateinit var ui: Ui
    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var mode = AlarmService.MODE_ALARM
    private var clockView: TextView? = null
    private var stepsView: TextView? = null
    private var unitView: TextView? = null
    private var progressTrack: FrameLayout? = null
    private var progressFill: View? = null
    private var progressFraction = 0f

    private var guardDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // A phone lying flat on a bathroom counter: auto-brightness is the enemy.
        window.attributes = window.attributes.apply { screenBrightness = 0.9f }
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        mode = intent.getStringExtra(EXTRA_MODE) ?: AlarmService.MODE_ALARM
        render()
    }

    override fun onResume() {
        super.onResume()
        val scannedAt = Prefs.scanOkAt(this)
        if (scannedAt > 0L && System.currentTimeMillis() - scannedAt < 120_000L) {
            Prefs.setScanOkAt(this, 0L)
            if (mode == AlarmService.MODE_ALARM) onDismissed() else finishGuard()
            return
        }
        if (mode == AlarmService.MODE_GUARD) {
            // The guard ended while this screen was not showing — either the steps were
            // walked, or it escalated and the alarm owns the notification now. Leave
            // without touching the service in both cases.
            if (!Prefs.guardPending(this)) finish() else ensureCounting()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra(EXTRA_MODE)?.let { mode = it }
        render()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        detachSteps()
        super.onDestroy()
    }

    @Deprecated("The alarm must not be dismissable with Back")
    override fun onBackPressed() {
        // Swallowed on purpose.
    }

    // ---------------------------------------------------------------- rendering

    private fun render() {
        detachSteps()
        // A re-render (onNewIntent) must not leave the previous mode's callbacks queued.
        handler.removeCallbacksAndMessages(null)
        val root = insetRoot()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)
        paintWindow(hideStatusBar = true)
        if (mode == AlarmService.MODE_ALARM) buildAlarm(column) else buildGuard(column)
    }

    private fun buildAlarm(column: LinearLayout) {
        column.removeAllViews()
        column.addView(ui.spacer(24))
        column.addView(ui.label(getString(R.string.alarm_eyebrow), ui.c(R.color.riseup_accent)))
        column.addView(ui.spacer(8))

        val clock = ui.display("", ui.c(R.color.riseup_text))
        clockView = clock
        column.addView(clock)
        column.addView(ui.spacer(16))
        column.addView(ui.rule(40))
        column.addView(ui.spacer(24))

        val enrolled = Prefs.barcode(this)
        val hint = if (enrolled == null) {
            ui.label(getString(R.string.alarm_hint_none), ui.c(R.color.riseup_accent))
        } else {
            ui.label(getString(R.string.alarm_hint), ui.c(R.color.riseup_text_dim))
        }
        hint.maxWidth = ui.dp(345)
        column.addView(hint)

        column.addView(ui.flex())

        val action = ui.primary(
            120,
            getString(if (enrolled == null) R.string.alarm_action_none else R.string.alarm_action)
        )
        action.setOnClickListener {
            startActivityForResult(Intent(this@AlarmActivity, ScanActivity::class.java), REQ_SCAN)
        }
        column.addView(action)

        column.addView(ui.spacer(16))
        val footnote = ui.label(getString(R.string.alarm_footnote), ui.c(R.color.riseup_text_faint))
        footnote.maxWidth = ui.dp(345)
        column.addView(footnote)
        column.addView(ui.spacer(16))

        tickClock()
    }

    /** The clock changes once a minute, at the minute. Nothing else moves. */
    private fun tickClock() {
        handler.removeCallbacksAndMessages(null)
        val run = object : Runnable {
            override fun run() {
                clockView?.text = clockFormat.format(Date())
                if (mode != AlarmService.MODE_ALARM) return
                val now = System.currentTimeMillis()
                val nextMinute = now - now % 60_000L + 60_000L + 250L
                handler.postDelayed(this, nextMinute - now)
            }
        }
        handler.post(run)
    }

    private fun buildGuard(column: LinearLayout) {
        column.removeAllViews()
        column.addView(ui.spacer(24))
        column.addView(ui.label(getString(R.string.guard_eyebrow), ui.c(R.color.riseup_accent)))
        column.addView(ui.spacer(24))

        val canCount = AlarmService.canCountSteps(this)
        val remaining = AlarmService.remainingSteps

        if (canCount) {
            val count = ui.display("$remaining", ui.c(R.color.riseup_text), ui.monoBold)
            stepsView = count
            column.addView(count)
            column.addView(ui.spacer(8))
            val unit = ui.label(unitFor(remaining), ui.c(R.color.riseup_text_dim))
            unitView = unit
            column.addView(unit)
            column.addView(ui.spacer(24))
            column.addView(progressBar(remaining))
        } else {
            stepsView = null
            unitView = null
            val note = ui.label(
                getString(R.string.guard_no_sensor), ui.c(R.color.riseup_text_dim)
            )
            note.maxWidth = ui.dp(345)
            column.addView(note)
        }

        column.addView(ui.flex())

        if (!canCount) {
            val again = ui.primary(64, getString(R.string.guard_scan_again))
            again.setOnClickListener {
                startActivityForResult(Intent(this@AlarmActivity, ScanActivity::class.java), REQ_SCAN)
            }
            column.addView(again)
            column.addView(ui.spacer(24))
        }

        // The consequence as a fact: a fixed clock time, not a countdown that moves.
        val footnote = ui.label(guardFootnote(), ui.c(R.color.riseup_text_faint))
        footnote.maxWidth = ui.dp(345)
        column.addView(footnote)
        column.addView(ui.spacer(16))

        // onResume follows every render and makes sure the service is counting.
        if (canCount) AlarmService.stepListener = { left -> onSteps(left) }
    }

    private fun guardFootnote(): String {
        val deadline = Prefs.guardDeadline(this)
        if (deadline <= 0L) return getString(R.string.guard_footnote_plain)
        return getString(R.string.guard_footnote, clockFormat.format(Date(deadline)))
    }

    private fun unitFor(remaining: Int): String =
        getString(if (remaining == 1) R.string.guard_unit_one else R.string.guard_unit)

    /** 4 dp track, `line`; fill `accent`; width = steps done / 40. */
    private fun progressBar(remaining: Int): FrameLayout {
        val track = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(4)
            )
            setBackgroundColor(ui.c(R.color.riseup_line))
        }
        val fill = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(ui.c(R.color.riseup_accent))
        }
        track.addView(fill)
        progressTrack = track
        progressFill = fill
        progressFraction = (STEPS_TARGET - remaining).toFloat() / STEPS_TARGET
        track.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or2, _ ->
            if (r - l != or2 - ol) applyProgress(animate = false)
        }
        return track
    }

    private fun applyProgress(animate: Boolean) {
        val track = progressTrack ?: return
        val fill = progressFill ?: return
        val width = track.width
        if (width <= 0) return
        val target = (width * progressFraction).toInt().coerceIn(0, width)
        val lp = fill.layoutParams as FrameLayout.LayoutParams
        if (lp.width == target) return
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            lp.width = target
            fill.layoutParams = lp
            return
        }
        ValueAnimator.ofInt(lp.width, target).apply {
            duration = 140
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val p = fill.layoutParams as FrameLayout.LayoutParams
                p.width = anim.animatedValue as Int
                fill.layoutParams = p
            }
            start()
        }
    }

    // ------------------------------------------------------------------ steps

    private fun onSteps(remaining: Int) {
        if (mode != AlarmService.MODE_GUARD) return
        stepsView?.text = "$remaining"
        unitView?.text = unitFor(remaining)
        progressFraction = (STEPS_TARGET - remaining).toFloat() / STEPS_TARGET
        applyProgress(animate = true)
        if (remaining == 0) finishGuard()
    }

    /**
     * The guard screen can be reached from the notification after the service died or
     * never started; asking for guard mode again is idempotent while it is counting.
     */
    private fun ensureCounting() {
        if (guardDone || !Prefs.guardPending(this)) return
        if (!AlarmService.canCountSteps(this)) return
        try {
            ContextCompat.startForegroundService(this, AlarmService.intent(this, AlarmService.MODE_GUARD))
        } catch (e: Exception) {
            // The receiver already started it; nothing else to try from here.
        }
    }

    private fun detachSteps() {
        AlarmService.stepListener = null
    }

    // ------------------------------------------------------------------ outcomes

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SCAN && resultCode == RESULT_OK) {
            if (mode == AlarmService.MODE_ALARM) onDismissed() else finishGuard()
        }
    }

    private fun onDismissed() {
        Prefs.setScanOkAt(this, 0L)
        detachSteps()
        handler.removeCallbacksAndMessages(null)
        AlarmService.stop(this)
        AlarmScheduler.scheduleNextAlarm(this)
        AlarmScheduler.scheduleGuard(this)
        finish()
    }

    private fun finishGuard() {
        if (guardDone) return
        guardDone = true
        Prefs.setScanOkAt(this, 0L)
        detachSteps()
        handler.removeCallbacksAndMessages(null)
        AlarmScheduler.cancelGuard(this)
        AlarmService.stop(this)
        finish()
    }
}

package com.mrjshzk.riseup

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * The main screen answers three questions in order: when the next alarm is, whether
 * the dismiss code is enrolled, and whether the device is set up. The week itself
 * lives on its own screen ([WeekScheduleActivity]), reached through one named row.
 * Spec: docs/ui-spec.md §1.
 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMS = 31
        private const val REQ_ENROLL = 41
        private const val KEY_DETAILS = "setup_details"
        private const val CLEAR_WINDOW_MS = 4_000L
        private const val ARMED_WINDOW_MS = 4_000L
    }

    private lateinit var ui: Ui
    private val uiHandler = Handler(Looper.getMainLooper())

    /** The six system checks are a detail of the READY summary, folded by default. */
    private var detailsExpanded = false
    private var clearArmed = false
    private var clearView: TextView? = null
    private var armedNotice: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        detailsExpanded = savedInstanceState?.getBoolean(KEY_DETAILS) ?: false
        requestMissingPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Recomputes the whole week; cancels when every day is off.
        AlarmScheduler.scheduleNextAlarm(this)
        build()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_DETAILS, detailsExpanded)
        super.onSaveInstanceState(outState)
    }

    // ---------------------------------------------------------------- permissions

    private fun requestMissingPermissions() {
        val wanted = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.ACTIVITY_RECOGNITION
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) build()
    }

    // ---------------------------------------------------------------- UI

    private fun build() {
        val week = Prefs.week(this)
        val next = AlarmScheduler.nextTrigger(week, ZonedDateTime.now())
        val anyEnabled = week.any { it.enabled }
        val code = Prefs.barcode(this)
        val checks = requiredChecks()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = false
            addView(column)
        }
        val root = insetRoot()
        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)
        paintWindow()

        // --- 1–4: identity, then the one fact the screen exists for
        column.addView(ui.spacer(16))
        column.addView(wordmarkRow(anyEnabled))
        column.addView(ui.spacer(32))
        column.addView(ui.label(getString(R.string.next_label), ui.c(R.color.riseup_text_dim)))
        column.addView(ui.spacer(8))
        column.addView(heroTime(next, anyEnabled))
        column.addView(ui.spacer(4))
        column.addView(nextLine(next))

        // --- 5: the week is a destination, not a panel on this screen
        column.addView(ui.spacer(24))
        column.addView(scheduleRow(week))

        // --- 6: dismiss code
        column.addView(ui.spacer(32))
        column.addView(ui.sectionHead(getString(R.string.code_section)))
        column.addView(ui.spacer(8))
        column.addView(codeState(code))
        column.addView(ui.spacer(12))
        if (code == null) {
            val enrol = ui.secondary(getString(R.string.code_enrol), accent = true)
            enrol.setOnClickListener { enroll() }
            column.addView(enrol)
        } else {
            column.addView(codeActions())
        }

        // --- 7: readiness summary, details on demand
        column.addView(ui.spacer(32))
        column.addView(readinessRow(checks))
        if (detailsExpanded) {
            column.addView(ui.spacer(8))
            checks.forEachIndexed { index, check ->
                column.addView(checkRow(check))
                if (index < checks.lastIndex) column.addView(ui.divider())
            }
        }

        // --- 8: dry run stays last and quiet
        column.addView(ui.spacer(32))
        column.addView(ui.sectionHead(getString(R.string.test_section)))
        column.addView(ui.spacer(8))
        column.addView(ui.body(getString(R.string.test_body), ui.c(R.color.riseup_text_dim)))
        column.addView(ui.spacer(8))
        val test = ui.textAction(getString(R.string.test_action))
        test.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        test.setPadding(0, 0, 0, 0)
        test.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        )
        test.setOnClickListener { dryRun() }
        column.addView(test)
        val notice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
            visibility = View.GONE
        }
        notice.addView(ui.label(getString(R.string.test_armed), ui.c(R.color.riseup_accent)))
        armedNotice = notice
        column.addView(notice)

        column.addView(ui.spacer(48))
        applyClearLabel()
    }

    private fun wordmarkRow(anyEnabled: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)
            )
        }
        row.addView(
            ui.label(getString(R.string.home_wordmark), ui.c(R.color.riseup_text_dim), tracking = 0.20f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(
            ui.label(
                getString(if (anyEnabled) R.string.home_state_on else R.string.home_state_off),
                ui.c(if (anyEnabled) R.color.riseup_text_dim else R.color.riseup_accent)
            )
        )
        return row
    }

    /** Pure readout: the schedule is edited on its own screen, not by tapping the clock. */
    private fun heroTime(next: ZonedDateTime?, anyEnabled: Boolean): TextView {
        val value = if (next == null) getString(R.string.home_no_time) else fmt(next.hour * 60 + next.minute)
        val view = ui.hero(
            value,
            ui.c(if (anyEnabled) R.color.riseup_text else R.color.riseup_text_dim)
        )
        view.gravity = Gravity.CENTER_VERTICAL
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(66)
        )
        view.contentDescription = if (next == null) {
            getString(R.string.home_hero_none)
        } else {
            getString(R.string.next_label) + ": " + value
        }
        return view
    }

    private fun nextLine(next: ZonedDateTime?): TextView {
        val caption = if (next == null) getString(R.string.home_next_none) else fmtDate(next)
        return ui.body(caption, ui.c(R.color.riseup_text_dim))
    }

    /** 64 dp bordered row: what the week is, what it currently says, and the door to it. */
    private fun scheduleRow(week: List<DaySchedule>): View {
        val summary = scheduleSummary(week)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)
            )
        }
        val words = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        words.addView(ui.label(getString(R.string.week_title), ui.c(R.color.riseup_text)))
        words.addView(ui.spacer(4))
        words.addView(ui.body(summary, ui.c(R.color.riseup_text_dim)))
        row.addView(words)
        row.addView(
            ui.label(getString(R.string.week_open), ui.c(R.color.riseup_text_dim)),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)).also { lp ->
                lp.leftMargin = dp(12)
                lp.gravity = Gravity.CENTER_VERTICAL
            }
        )
        ui.pressable(row, ui.c(R.color.riseup_bg), ui.c(R.color.riseup_surface_press), ui.c(R.color.riseup_line))
        row.contentDescription = getString(R.string.week_open_cd, summary)
        row.setOnClickListener {
            startActivity(Intent(this, WeekScheduleActivity::class.java))
        }
        return row
    }

    // ---------------------------------------------------------------- code

    private fun codeState(code: String?): TextView {
        if (code == null) {
            val view = ui.body(getString(R.string.code_none), ui.c(R.color.riseup_accent))
            view.maxWidth = dp(345)
            return view
        }
        val masked = mask(code)
        val span = SpannableStringBuilder(getString(R.string.code_enrolled, masked))
        val start = span.length - masked.length
        span.setSpan(
            TypefaceSpan(ui.mono), start, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return ui.body(span, ui.c(R.color.riseup_text_dim))
    }

    /** One clear action; removal stays a quieter second step. */
    private fun codeActions(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val replace = ui.secondary(getString(R.string.code_replace))
        replace.setOnClickListener { enroll() }
        column.addView(replace)
        column.addView(ui.spacer(4))

        val clear = ui.textAction(getString(R.string.code_clear))
        clear.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        clear.setPadding(0, 0, 0, 0)
        clear.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        )
        clearView = clear
        clear.setOnClickListener { clearCode() }
        column.addView(clear)
        return column
    }

    private fun clearCode() {
        if (!clearArmed) {
            clearArmed = true
            applyClearLabel()
            uiHandler.removeCallbacks(clearReset)
            uiHandler.postDelayed(clearReset, CLEAR_WINDOW_MS)
        } else {
            clearArmed = false
            uiHandler.removeCallbacks(clearReset)
            Prefs.clearBarcode(this)
            rescheduleAndRebuild()
        }
    }

    private val clearReset = Runnable {
        clearArmed = false
        applyClearLabel()
    }

    private fun applyClearLabel() {
        val view = clearView ?: return
        view.text = getString(if (clearArmed) R.string.code_clear_again else R.string.code_clear)
        view.setTextColor(ui.c(if (clearArmed) R.color.riseup_accent else R.color.riseup_text_dim))
    }

    // ---------------------------------------------------------------- readiness

    /** One 48 dp row that answers "is this phone set up?", with the six checks behind SHOW. */
    private fun readinessRow(checks: List<Check>): View {
        val failing = checks.count { !it.ok }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            )
        }
        row.addView(
            ui.square(ui.c(if (failing == 0) R.color.riseup_success else R.color.riseup_accent))
        )
        row.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(1))
        })
        val state = ui.label(
            when (failing) {
                0 -> getString(R.string.req_ready)
                1 -> getString(R.string.req_fix_one)
                else -> getString(R.string.req_fix_many, failing)
            },
            ui.c(if (failing == 0) R.color.riseup_text else R.color.riseup_accent)
        )
        state.gravity = Gravity.CENTER_VERTICAL
        row.addView(
            state,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        )
        val toggle = ui.label(
            getString(if (detailsExpanded) R.string.req_hide else R.string.req_show),
            ui.c(R.color.riseup_text_dim)
        )
        toggle.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        row.addView(toggle, LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.MATCH_PARENT))

        ui.pressable(row, ui.c(R.color.riseup_bg), ui.c(R.color.riseup_surface_press))
        row.contentDescription = getString(
            R.string.req_summary_cd,
            state.text,
            getString(if (detailsExpanded) R.string.req_hide else R.string.req_show).lowercase(Locale.getDefault())
        )
        row.setOnClickListener {
            detailsExpanded = !detailsExpanded
            build()
        }
        return row
    }

    // ---------------------------------------------------------------- dry run

    private fun dryRun() {
        AlarmScheduler.scheduleTest(this, 10_000L)
        val notice = armedNotice ?: return
        notice.visibility = View.VISIBLE
        uiHandler.removeCallbacks(armedHide)
        uiHandler.postDelayed(armedHide, ARMED_WINDOW_MS)
    }

    private val armedHide = Runnable { armedNotice?.visibility = View.GONE }

    // ---------------------------------------------------------------- the six checks

    private inner class Check(val label: String, val ok: Boolean, val open: () -> Unit)

    private fun requiredChecks(): List<Check> = listOf(
        Check(getString(R.string.req_exact), AlarmScheduler.canScheduleExact(this)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            )
        },
        Check(getString(R.string.req_fsi), canUseFullScreen(), ::openFullScreenSettings),
        Check(getString(R.string.req_notifications), notificationsEnabled(), ::openNotificationSettings),
        Check(getString(R.string.req_camera), granted(Manifest.permission.CAMERA)) {
            requestMissingPermissions()
        },
        Check(getString(R.string.req_steps), granted(Manifest.permission.ACTIVITY_RECOGNITION)) {
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQ_PERMS)
        },
        Check(getString(R.string.req_battery), batteryUnrestricted(), ::openBatterySettings)
    )

    /** 48 dp: 8 dp square, 12 dp gap, label, trailing FIX only when failing. */
    private fun checkRow(check: Check): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            )
        }
        row.addView(
            ui.square(ui.c(if (check.ok) R.color.riseup_success else R.color.riseup_accent))
        )
        row.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(1))
            }
        )
        row.addView(
            ui.body(check.label, ui.c(R.color.riseup_text)),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { lp ->
                lp.gravity = Gravity.CENTER_VERTICAL
            }
        )
        if (!check.ok) {
            val fix = ui.label(getString(R.string.req_fix), ui.c(R.color.riseup_accent))
            fix.gravity = Gravity.CENTER
            fix.layoutParams = LinearLayout.LayoutParams(dp(72), dp(48))
            row.addView(fix)
        }
        row.contentDescription = getString(
            if (check.ok) R.string.req_ok else R.string.req_missing, check.label
        )
        if (!check.ok) {
            ui.pressable(row, ui.c(R.color.riseup_bg), ui.c(R.color.riseup_surface_press))
            row.setOnClickListener { check.open() }
        }
        return row
    }

    // ---------------------------------------------------------------- actions

    private fun rescheduleAndRebuild() {
        AlarmScheduler.scheduleNextAlarm(this)
        build()
    }

    private fun enroll() {
        startActivityForResult(
            Intent(this, ScanActivity::class.java).putExtra(ScanActivity.EXTRA_ENROLL, true),
            REQ_ENROLL
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ENROLL && resultCode == RESULT_OK) build()
    }

    // ---------------------------------------------------------------- checks

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun notificationsEnabled(): Boolean {
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return false
        return nm.areNotificationsEnabled()
    }

    private fun canUseFullScreen(): Boolean {
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName"))
                )
                return
            } catch (e: Exception) {
                // fall through
            }
        }
        openNotificationSettings()
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun batteryUnrestricted(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBatterySettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ---------------------------------------------------------------- formatting

    private fun dp(v: Int): Int = ui.dp(v)

    private fun fmt(minutes: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)

    private fun fmtDate(at: ZonedDateTime): String =
        at.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

    private fun abbr(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun mask(value: String): String =
        if (value.length <= 8) value else value.take(4) + "…" + value.takeLast(3)

    /**
     * One honest line for the week: the days it rings and the time it rings.
     * Mixed times fall back to the earliest one, labelled "from".
     */
    private fun scheduleSummary(week: List<DaySchedule>): String {
        val on = week.filter { it.enabled }
        if (on.isEmpty()) return getString(R.string.schedule_none)
        val times = on.map { it.minutesOfDay }.distinct()
        if (times.size == 1) {
            val time = fmt(times.first())
            return when {
                on.size == 7 -> getString(R.string.schedule_every_day, time)
                on.size == 5 && on.all { it.dayOfWeek.value <= DayOfWeek.FRIDAY.value } ->
                    getString(R.string.schedule_weekdays, time)
                else -> getString(
                    R.string.schedule_days,
                    on.joinToString(", ") { abbr(it.dayOfWeek) },
                    time
                )
            }
        }
        return if (on.size == 1) {
            getString(R.string.schedule_days, abbr(on.first().dayOfWeek), fmt(times.first()))
        } else {
            getString(R.string.schedule_mixed, on.size, fmt(times.min()))
        }
    }
}

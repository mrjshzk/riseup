package com.mrjshzk/riseup

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * The main container for RiseUp. Organised into three bottom tabs:
 *  - Left [TAB_MAIN]: Next alarm readout, sleep distance ("in 7 h 12 min"), quick status.
 *  - Middle [TAB_SCHEDULE]: Full week schedule with in-place physical tactile steppers.
 *  - Right [TAB_SETTINGS]: QR / Barcode enrollment, system readiness checks, dry run test.
 */
class MainActivity : Activity() {

    companion object {
        const val EXTRA_TAB = "extra_tab"

        const val TAB_MAIN = 0
        const val TAB_SCHEDULE = 1
        const val TAB_SETTINGS = 2

        private const val KEY_TAB = "current_tab"
        private const val KEY_DETAILS = "setup_details"
        private const val KEY_SOURCE = "copy_source"
        private const val KEY_EDITING = "editing_day"

        private const val REQ_PERMS = 31
        private const val REQ_ENROLL = 41
        private const val CLEAR_WINDOW_MS = 4_000L
        private const val ARMED_WINDOW_MS = 4_000L
        private const val HOLD_MS = 400L
        private const val REPEAT_MS = 90L
        private const val RESCHEDULE_MS = 500L
    }

    private lateinit var ui: Ui
    private val uiHandler = Handler(Looper.getMainLooper())

    private var currentTab = TAB_MAIN

    // Tab 1 state
    private var nextLineView: TextView? = null

    // Tab 2 (Schedule) state
    private var sourceDay: DayOfWeek = LocalDate.now().dayOfWeek
    private var editingDay: DayOfWeek? = null
    private var editHour = 0
    private var editMinute = 0
    private var editTimeView: TextView? = null
    private var hourView: TextView? = null
    private var minuteView: TextView? = null
    private var sourceNoteView: TextView? = null
    private val reschedule = Runnable { AlarmScheduler.scheduleNextAlarm(this) }

    // Tab 3 (Settings) state
    private var detailsToggled: Boolean? = null
    private var detailsExpanded = false
    private var clearArmed = false
    private var clearView: TextView? = null
    private var armedNotice: View? = null

    // Window insets
    private var topInset = 0
    private var bottomInset = 0
    private var leftInset = 0
    private var rightInset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt(KEY_TAB, TAB_MAIN)
            if (savedInstanceState.containsKey(KEY_DETAILS)) {
                detailsToggled = savedInstanceState.getBoolean(KEY_DETAILS)
            }
            savedInstanceState.getString(KEY_SOURCE)?.let { name ->
                runCatching { DayOfWeek.valueOf(name) }.getOrNull()?.let { sourceDay = it }
            }
            savedInstanceState.getString(KEY_EDITING)?.let { name ->
                editingDay = runCatching { DayOfWeek.valueOf(name) }.getOrNull()
            }
        }
        intent?.getIntExtra(EXTRA_TAB, -1)?.let { if (it in 0..2) currentTab = it }
        requestMissingPermissions()
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.scheduleNextAlarm(this)
        build()
        tickNextLine()
    }

    override fun onPause() {
        uiHandler.removeCallbacks(nextLineTick)
        if (uiHandler.hasCallbacks(reschedule)) {
            uiHandler.removeCallbacks(reschedule)
            reschedule.run()
        }
        uiHandler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_TAB, currentTab)
        detailsToggled?.let { outState.putBoolean(KEY_DETAILS, it) }
        outState.putString(KEY_SOURCE, sourceDay.name)
        editingDay?.let { outState.putString(KEY_EDITING, it.name) }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getIntExtra(EXTRA_TAB, -1)?.let { if (it in 0..2) { currentTab = it; build() } }
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

    // ---------------------------------------------------------------- UI Builder

    private fun build() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(ui.c(R.color.riseup_bg))
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Content Area (Scrollable with left/right/top gutter)
        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val scrollColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = false
            addView(scrollColumn)
        }

        contentFrame.addView(
            scroll,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        mainLayout.addView(contentFrame)

        // 2. Tab Bar (Anchored at the bottom)
        val tabBar = buildTabBar()
        mainLayout.addView(tabBar)

        root.addView(mainLayout)
        setContentView(root)
        paintWindow()

        // Handle insets: top/sides on scroll content, bottom on tab bar
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                leftInset = bars.left
                topInset = bars.top
                rightInset = bars.right
                bottomInset = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                leftInset = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                rightInset = insets.systemWindowInsetRight
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }
            scroll.setPadding(dp(24) + leftInset, dp(16) + topInset, dp(24) + rightInset, dp(24))
            tabBar.setPadding(leftInset, 0, rightInset, bottomInset)
            insets
        }

        // Populate selected tab content
        when (currentTab) {
            TAB_MAIN -> buildMainTab(scrollColumn)
            TAB_SCHEDULE -> buildScheduleTab(scrollColumn)
            TAB_SETTINGS -> buildSettingsTab(scrollColumn)
        }
    }

    // ---------------------------------------------------------------- Bottom Navigation Bar

    private fun buildTabBar(): View {
        val barContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.c(R.color.riseup_bg))
        }

        // 1 dp top border
        barContainer.addView(ui.divider())

        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            )
        }

        tabsRow.addView(tabButton(getString(R.string.tab_alarm), TAB_MAIN))
        tabsRow.addView(tabButton(getString(R.string.tab_schedule), TAB_SCHEDULE))
        tabsRow.addView(tabButton(getString(R.string.tab_settings), TAB_SETTINGS))

        barContainer.addView(tabsRow)
        return barContainer
    }

    private fun tabButton(label: String, tabIndex: Int): View {
        val isActive = currentTab == tabIndex
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundColor(ui.c(if (isActive) R.color.riseup_surface else R.color.riseup_bg))
        }

        // Top 2 dp indicator
        val indicator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2))
            setBackgroundColor(ui.c(if (isActive) R.color.riseup_accent else Color.TRANSPARENT))
        }
        container.addView(indicator)

        // Label
        val textView = ui.label(
            label,
            ui.c(if (isActive) R.color.riseup_text else R.color.riseup_text_dim),
            tracking = 0.12f
        ).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        container.addView(textView)

        ui.pressable(
            container,
            ui.c(if (isActive) R.color.riseup_surface else R.color.riseup_bg),
            ui.c(R.color.riseup_surface_press)
        )
        container.contentDescription = "$label tab"
        container.setOnClickListener {
            if (currentTab != tabIndex) {
                currentTab = tabIndex
                build()
            }
        }
        return container
    }

    // ---------------------------------------------------------------- TAB 1: ALARM / MAIN

    private fun buildMainTab(column: LinearLayout) {
        val week = Prefs.week(this)
        val next = AlarmScheduler.nextTrigger(week, ZonedDateTime.now())
        val anyEnabled = week.any { it.enabled }
        val code = Prefs.barcode(this)
        val checks = requiredChecks()
        val failing = checks.count { !it.ok }

        // Top brand row
        column.addView(wordmarkRow(anyEnabled))
        column.addView(ui.spacer(32))

        // Hero readout
        column.addView(ui.label(getString(R.string.next_label), ui.c(R.color.riseup_text_dim)))
        column.addView(ui.spacer(8))
        column.addView(heroTime(next, anyEnabled))
        column.addView(ui.spacer(4))
        column.addView(nextLine(next))

        // Destination Card 1: Weekly Schedule (tap opens Tab 2)
        column.addView(ui.spacer(24))
        column.addView(scheduleRow(week))

        // Destination Card 2: Dismiss Code Status (tap opens Tab 3)
        column.addView(ui.spacer(16))
        column.addView(codeQuickRow(code))

        // Destination Card 3: Readiness Status (tap opens Tab 3)
        column.addView(ui.spacer(16))
        column.addView(readinessQuickRow(failing))

        column.addView(ui.spacer(32))
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
        return view
    }

    private fun nextLine(next: ZonedDateTime?): TextView {
        val view = ui.body(nextCaption(next), ui.c(R.color.riseup_text_dim))
        nextLineView = view
        return view
    }

    private fun nextCaption(next: ZonedDateTime?): String {
        if (next == null) return getString(R.string.home_next_none)
        val seconds = java.time.Duration.between(ZonedDateTime.now(), next).seconds
        val minutes = (seconds + 59) / 60
        return getString(R.string.home_next_in, fmtDate(next), fmtDistance(minutes))
    }

    private fun fmtDistance(totalMinutes: Long): String {
        if (totalMinutes < 1) return getString(R.string.dur_under_minute)
        val days = (totalMinutes / (24 * 60)).toInt()
        val hours = ((totalMinutes / 60) % 24).toInt()
        val minutes = (totalMinutes % 60).toInt()
        return when {
            days > 0 -> getString(R.string.dur_d_h, days, hours)
            hours > 0 && minutes == 0 -> getString(R.string.dur_h, hours)
            hours > 0 -> getString(R.string.dur_h_min, hours, minutes)
            else -> getString(R.string.dur_min, minutes)
        }
    }

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
        row.setOnClickListener {
            currentTab = TAB_SCHEDULE
            build()
        }
        return row
    }

    private fun codeQuickRow(code: String?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)
            )
        }
        val words = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        words.addView(ui.label(getString(R.string.code_section), ui.c(R.color.riseup_text)))
        words.addView(ui.spacer(4))
        val statusText = if (code == null) {
            getString(R.string.code_none)
        } else {
            getString(R.string.code_enrolled, mask(code))
        }
        words.addView(ui.body(statusText, ui.c(if (code == null) R.color.riseup_accent else R.color.riseup_text_dim)))
        row.addView(words)
        row.addView(
            ui.label(getString(if (code == null) R.string.code_enrol else R.string.week_open), ui.c(R.color.riseup_text_dim)),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)).also { lp ->
                lp.leftMargin = dp(12)
                lp.gravity = Gravity.CENTER_VERTICAL
            }
        )
        ui.pressable(row, ui.c(R.color.riseup_bg), ui.c(R.color.riseup_surface_press), ui.c(R.color.riseup_line))
        row.setOnClickListener {
            currentTab = TAB_SETTINGS
            build()
        }
        return row
    }

    private fun readinessQuickRow(failing: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            )
        }
        row.addView(ui.square(ui.c(if (failing == 0) R.color.riseup_success else R.color.riseup_accent)))
        row.addView(View(this), LinearLayout.LayoutParams(dp(12), dp(1)))
        val words = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        words.addView(
            ui.label(
                if (failing == 0) getString(R.string.req_ready) else getString(R.string.req_fix_many, failing),
                ui.c(if (failing == 0) R.color.riseup_text else R.color.riseup_accent)
            )
        )
        row.addView(words)
        row.addView(
            ui.label(getString(R.string.week_open), ui.c(R.color.riseup_text_dim)),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)).also { lp ->
                lp.leftMargin = dp(12)
                lp.gravity = Gravity.CENTER_VERTICAL
            }
        )
        ui.pressable(row, ui.c(R.color.riseup_bg), ui.c(R.color.riseup_surface_press), ui.c(R.color.riseup_line))
        row.setOnClickListener {
            currentTab = TAB_SETTINGS
            build()
        }
        return row
    }

    // ---------------------------------------------------------------- TAB 2: SCHEDULE

    private fun buildScheduleTab(column: LinearLayout) {
        val week = Prefs.week(this)
        val today = LocalDate.now().dayOfWeek

        editTimeView = null
        hourView = null
        minuteView = null

        column.addView(ui.headline(getString(R.string.week_title), ui.c(R.color.riseup_text)))
        column.addView(ui.spacer(8))
        column.addView(ui.body(getString(R.string.week_intro), ui.c(R.color.riseup_text_dim)))
        column.addView(ui.spacer(24))

        column.addView(columnHeads())
        column.addView(ui.spacer(8))

        week.forEachIndexed { index, schedule ->
            if (index > 0) column.addView(ui.divider())
            column.addView(dayRow(schedule, today))
            if (schedule.dayOfWeek == editingDay) column.addView(editor(schedule))
        }

        column.addView(ui.spacer(32))
        column.addView(copyBlock(week))
        column.addView(ui.spacer(32))
    }

    private fun columnHeads(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(18)
            )
        }
        row.addView(
            ui.label(getString(R.string.week_col_day), ui.c(R.color.riseup_text_dim)),
            LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.MATCH_PARENT)
        )
        row.addView(
            ui.label(getString(R.string.week_col_time), ui.c(R.color.riseup_text_dim)).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.MATCH_PARENT).also { lp ->
                lp.leftMargin = dp(8)
            }
        )
        row.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        row.addView(
            ui.label(getString(R.string.week_col_ring), ui.c(R.color.riseup_text_dim)).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.MATCH_PARENT)
        )
        return row
    }

    private fun dayRow(schedule: DaySchedule, today: DayOfWeek): View {
        val isToday = schedule.dayOfWeek == today
        val selected = schedule.dayOfWeek == sourceDay
        val editing = schedule.dayOfWeek == editingDay
        val dayName = fullDay(schedule.dayOfWeek)
        val timeText = fmt(schedule.minutesOfDay)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)
            )
        }
        ui.pressable(row, ui.c(R.color.riseup_bg), ui.c(R.color.riseup_surface_press))
        row.setOnClickListener { selectDay(schedule.dayOfWeek) }

        val dayCell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val mark = View(this).apply {
            setBackgroundColor(ui.c(if (selected) R.color.riseup_accent else Color.TRANSPARENT))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        dayCell.addView(mark, LinearLayout.LayoutParams(dp(2), dp(24)).also { lp ->
            lp.gravity = Gravity.CENTER_VERTICAL
        })
        dayCell.addView(View(this), LinearLayout.LayoutParams(dp(8), dp(1)))
        dayCell.addView(
            ui.label(
                abbr(schedule.dayOfWeek),
                ui.c(if (isToday) R.color.riseup_text else R.color.riseup_text_dim)
            )
        )
        row.addView(dayCell)

        val time = ui.body(timeText, ui.c(R.color.riseup_text))
        time.fontFeatureSettings = "tnum"
        time.gravity = Gravity.CENTER
        ui.pressable(
            time, ui.c(R.color.riseup_bg), ui.c(R.color.riseup_surface_press),
            ui.c(if (editing) R.color.riseup_text else R.color.riseup_line)
        )
        time.setOnClickListener { toggleEditor(schedule.dayOfWeek) }
        if (editing) editTimeView = time
        row.addView(time, LinearLayout.LayoutParams(dp(112), dp(48)).also { lp ->
            lp.leftMargin = dp(8)
            lp.gravity = Gravity.CENTER_VERTICAL
        })

        row.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))

        val on = schedule.enabled
        val ring = ui.label(
            getString(if (on) R.string.week_day_on else R.string.week_day_off),
            ui.c(if (on) R.color.riseup_text else R.color.riseup_text_dim)
        )
        ring.gravity = Gravity.CENTER
        ui.pressable(ring, ui.c(R.color.riseup_bg), ui.c(R.color.riseup_surface_press), ui.c(R.color.riseup_line))
        ring.setOnClickListener {
            Prefs.setDayEnabled(this, schedule.dayOfWeek, !on)
            sourceDay = schedule.dayOfWeek
            AlarmScheduler.scheduleNextAlarm(this)
            build()
        }
        row.addView(ring, LinearLayout.LayoutParams(dp(96), dp(48)).also { lp ->
            lp.gravity = Gravity.CENTER_VERTICAL
        })

        return row
    }

    private fun selectDay(day: DayOfWeek) {
        if (sourceDay == day) return
        sourceDay = day
        build()
    }

    private fun toggleEditor(day: DayOfWeek) {
        editingDay = if (editingDay == day) null else day
        sourceDay = day
        build()
    }

    private fun editor(schedule: DaySchedule): View {
        editHour = schedule.hour
        editMinute = schedule.minute
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = ui.block(ui.c(R.color.riseup_bg), ui.c(R.color.riseup_line))
        }
        val hour = stepperRow(
            getString(R.string.week_edit_hour), two(editHour),
            getString(R.string.week_edit_hour_down_cd), getString(R.string.week_edit_hour_up_cd)
        ) { delta -> editHour = Math.floorMod(editHour + delta, 24); applyEdit() }
        hourView = hour.second
        box.addView(hour.first)
        box.addView(ui.spacer(8))
        val minute = stepperRow(
            getString(R.string.week_edit_minute), two(editMinute),
            getString(R.string.week_edit_minute_down_cd), getString(R.string.week_edit_minute_up_cd)
        ) { delta -> editMinute = Math.floorMod(editMinute + delta, 60); applyEdit() }
        minuteView = minute.second
        box.addView(minute.first)
        box.addView(ui.spacer(12))
        box.addView(ui.label(getString(R.string.week_edit_hint), ui.c(R.color.riseup_text_faint)))
        outer.addView(box)
        return outer
    }

    private fun stepperRow(
        label: String,
        value: String,
        downCd: String,
        upCd: String,
        step: (Int) -> Unit
    ): Pair<View, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            )
        }
        row.addView(
            ui.label(label, ui.c(R.color.riseup_text_dim)),
            LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        row.addView(stepButton(getString(R.string.week_edit_minus), downCd) { step(-1) })
        val number = ui.text(value, 34f, ui.c(R.color.riseup_text), ui.condensed, tracking = -0.01f, tabular = true)
        number.gravity = Gravity.CENTER
        number.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.addView(number, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(stepButton(getString(R.string.week_edit_plus), upCd) { step(1) })
        return row to number
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun stepButton(glyph: String, cd: String, step: () -> Unit): TextView {
        val normal = ui.c(R.color.riseup_bg)
        val pressed = ui.c(R.color.riseup_surface_press)
        val stroke = ui.c(R.color.riseup_line)
        val view = ui.text(glyph, 26f, ui.c(R.color.riseup_text), ui.textBold)
        view.gravity = Gravity.CENTER
        view.layoutParams = LinearLayout.LayoutParams(dp(56), dp(48))
        view.background = ui.block(normal, stroke)
        view.isClickable = true
        view.isFocusable = true
        view.contentDescription = cd
        view.setOnClickListener { step() }
        val repeat = object : Runnable {
            override fun run() {
                step()
                uiHandler.postDelayed(this, REPEAT_MS)
            }
        }
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.background = ui.block(pressed, stroke)
                    step()
                    uiHandler.postDelayed(repeat, HOLD_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.background = ui.block(normal, stroke)
                    uiHandler.removeCallbacks(repeat)
                }
            }
            true
        }
        return view
    }

    private fun applyEdit() {
        val day = editingDay ?: return
        Prefs.setDayTime(this, day, editHour, editMinute)
        hourView?.text = two(editHour)
        minuteView?.text = two(editMinute)
        editTimeView?.text = fmt(editHour * 60 + editMinute)
        if (sourceDay == day) {
            sourceNoteView?.text = sourceNote(Prefs.day(this, day))
        }
        uiHandler.removeCallbacks(reschedule)
        uiHandler.postDelayed(reschedule, RESCHEDULE_MS)
    }

    private fun sourceNote(source: DaySchedule): String =
        getString(R.string.week_copy_source, abbr(source.dayOfWeek), fmt(source.minutesOfDay))

    private fun copyBlock(week: List<DaySchedule>): View {
        val source = week.first { it.dayOfWeek == sourceDay }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(ui.sectionHead(getString(R.string.week_copy_section)))
        column.addView(ui.spacer(8))
        val note = ui.body(sourceNote(source), ui.c(R.color.riseup_text_dim))
        sourceNoteView = note
        column.addView(note)
        column.addView(ui.spacer(12))

        val weekdays = ui.secondary(getString(R.string.week_copy_weekdays))
        weekdays.setOnClickListener {
            Prefs.copyTimeToWeekdays(this, Prefs.day(this, sourceDay))
            AlarmScheduler.scheduleNextAlarm(this)
            build()
        }
        column.addView(weekdays)
        column.addView(ui.spacer(8))

        val all = ui.secondary(getString(R.string.week_copy_all))
        all.setOnClickListener {
            Prefs.copyTimeToAllDays(this, Prefs.day(this, sourceDay))
            AlarmScheduler.scheduleNextAlarm(this)
            build()
        }
        column.addView(all)
        return column
    }

    // ---------------------------------------------------------------- TAB 3: CODE & SETTINGS

    private fun buildSettingsTab(column: LinearLayout) {
        val code = Prefs.barcode(this)
        val checks = requiredChecks()
        detailsExpanded = detailsToggled ?: checks.any { !it.ok }

        // Section 1: Dismiss QR / Barcode
        column.addView(ui.headline(getString(R.string.code_section), ui.c(R.color.riseup_text)))
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

        // Section 2: System Readiness
        column.addView(ui.spacer(32))
        column.addView(readinessRow(checks))
        if (detailsExpanded) {
            column.addView(ui.spacer(8))
            checks.forEachIndexed { index, check ->
                column.addView(checkRow(check))
                if (index < checks.lastIndex) column.addView(ui.divider())
            }
        }

        // Section 3: Dry Run
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

        column.addView(ui.spacer(32))
        applyClearLabel()
    }

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

    private fun codeActions(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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
            build()
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
        row.setOnClickListener {
            detailsToggled = !detailsExpanded
            build()
        }
        return row
    }

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
        row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(12), dp(1)) })
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
        if (!check.ok) {
            ui.pressable(row, ui.c(R.color.riseup_bg), ui.c(R.color.riseup_surface_press))
            row.setOnClickListener { check.open() }
        }
        return row
    }

    private fun dryRun() {
        AlarmScheduler.scheduleTest(this, 10_000L)
        val notice = armedNotice ?: return
        notice.visibility = View.VISIBLE
        uiHandler.removeCallbacks(armedHide)
        uiHandler.postDelayed(armedHide, ARMED_WINDOW_MS)
    }

    private val armedHide = Runnable { armedNotice?.visibility = View.GONE }

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

    // ---------------------------------------------------------------- helpers

    private val nextLineTick = object : Runnable {
        override fun run() {
            val next = AlarmScheduler.nextTrigger(Prefs.week(this@MainActivity), ZonedDateTime.now())
            nextLineView?.text = nextCaption(next)
            tickNextLine()
        }
    }

    private fun tickNextLine() {
        uiHandler.removeCallbacks(nextLineTick)
        val now = System.currentTimeMillis()
        uiHandler.postDelayed(nextLineTick, 60_000L - now % 60_000L + 250L)
    }

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
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
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
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun dp(v: Int): Int = ui.dp(v)

    private fun two(v: Int): String = String.format(Locale.getDefault(), "%02d", v)

    private fun fmt(minutes: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)

    private fun fmtDate(at: ZonedDateTime): String =
        at.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

    private fun abbr(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun fullDay(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.FULL, Locale.getDefault())

    private fun mask(value: String): String =
        if (value.length <= 8) value else value.take(4) + "…" + value.takeLast(3)

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

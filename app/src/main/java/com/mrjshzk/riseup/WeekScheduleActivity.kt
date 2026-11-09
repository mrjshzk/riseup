package com.mrjshzk.riseup

import android.app.Activity
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The week's own screen. Seven independent days, each with a visible time field and
 * an explicit RING control; the copy actions name their source and say what they
 * change. Reached only through the WEEKLY SCHEDULE row on MainActivity.
 * Spec: docs/ui-spec.md §1.4.
 */
class WeekScheduleActivity : Activity() {

    companion object {
        private const val KEY_SOURCE = "copy_source"
    }

    private lateinit var ui: Ui

    /** The day whose time the copy actions read. Defaults to today, like an untouched week. */
    private var sourceDay: DayOfWeek = LocalDate.now().dayOfWeek

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        savedInstanceState?.getString(KEY_SOURCE)?.let { name ->
            runCatching { DayOfWeek.valueOf(name) }.getOrNull()?.let { sourceDay = it }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SOURCE, sourceDay.name)
        super.onSaveInstanceState(outState)
    }

    // ---------------------------------------------------------------- UI

    private fun render() {
        val week = Prefs.week(this)
        val today = LocalDate.now().dayOfWeek

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

        column.addView(ui.spacer(16))
        column.addView(topRow())
        column.addView(ui.spacer(16))
        column.addView(ui.headline(getString(R.string.week_title), ui.c(R.color.riseup_text)))
        column.addView(ui.spacer(8))
        column.addView(ui.body(getString(R.string.week_intro), ui.c(R.color.riseup_text_dim)))
        column.addView(ui.spacer(24))
        column.addView(columnHeads())
        column.addView(ui.spacer(8))
        week.forEachIndexed { index, schedule ->
            if (index > 0) column.addView(ui.divider())
            column.addView(dayRow(schedule, today))
        }
        column.addView(ui.spacer(32))
        column.addView(copyBlock(week))
        column.addView(ui.spacer(48))
    }

    private fun topRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            )
        }
        row.addView(
            ui.label(getString(R.string.home_wordmark), ui.c(R.color.riseup_text_dim), tracking = 0.20f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val done = ui.textAction(getString(R.string.week_done), widthDp = 72)
        done.setPadding(dp(12), 0, dp(4), 0)
        done.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        done.setOnClickListener { finish() }
        row.addView(done)
        return row
    }

    /** Column names, so TIME reads as a field and RING as the thing it controls. */
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

    /** 64 dp: day (selects the copy source), a 112 × 48 time field, a 96 × 48 RING control. */
    private fun dayRow(schedule: DaySchedule, today: DayOfWeek): View {
        val isToday = schedule.dayOfWeek == today
        val selected = schedule.dayOfWeek == sourceDay
        val dayName = fullDay(schedule.dayOfWeek)
        val timeText = fmt(schedule.minutesOfDay)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)
            )
        }
        // Tapping the row's empty space picks it as the copy source; the two fields
        // inside remain their own targets.
        ui.pressable(row, ui.c(R.color.riseup_bg), ui.c(R.color.riseup_surface_press))
        row.contentDescription = getString(R.string.week_select_cd, dayName)
        row.setOnClickListener { select(schedule.dayOfWeek) }

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
        ui.pressable(time, ui.c(R.color.riseup_bg), ui.c(R.color.riseup_surface_press), ui.c(R.color.riseup_line))
        time.contentDescription = getString(R.string.week_time_cd, dayName, timeText)
        time.setOnClickListener { pickTime(schedule.dayOfWeek) }
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
        ring.contentDescription = if (on) {
            getString(R.string.week_ring_on_cd, dayName, timeText)
        } else {
            getString(R.string.week_ring_off_cd, dayName)
        }
        ring.setOnClickListener {
            Prefs.setDayEnabled(this, schedule.dayOfWeek, !on)
            sourceDay = schedule.dayOfWeek
            rescheduleAndRender()
        }
        row.addView(ring, LinearLayout.LayoutParams(dp(96), dp(48)).also { lp ->
            lp.gravity = Gravity.CENTER_VERTICAL
        })

        return row
    }

    private fun copyBlock(week: List<DaySchedule>): View {
        val source = week.first { it.dayOfWeek == sourceDay }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(ui.sectionHead(getString(R.string.week_copy_section)))
        column.addView(ui.spacer(8))
        val note = ui.body(
            getString(
                R.string.week_copy_source,
                abbr(source.dayOfWeek),
                fmt(source.minutesOfDay)
            ),
            ui.c(R.color.riseup_text_dim)
        )
        column.addView(note)
        column.addView(ui.spacer(12))

        val weekdays = ui.secondary(getString(R.string.week_copy_weekdays))
        weekdays.setOnClickListener {
            Prefs.copyTimeToWeekdays(this, source)
            rescheduleAndRender()
        }
        column.addView(weekdays)
        column.addView(ui.spacer(8))

        val all = ui.secondary(getString(R.string.week_copy_all))
        all.setOnClickListener {
            Prefs.copyTimeToAllDays(this, source)
            rescheduleAndRender()
        }
        column.addView(all)
        return column
    }

    // ---------------------------------------------------------------- actions

    private fun select(day: DayOfWeek) {
        if (sourceDay == day) return
        sourceDay = day
        render()
    }

    private fun pickTime(day: DayOfWeek) {
        val schedule = Prefs.day(this, day)
        TimePickerDialog(
            this,
            { _, hour, minute ->
                Prefs.setDayTime(this, day, hour, minute)
                sourceDay = day
                rescheduleAndRender()
            },
            schedule.hour,
            schedule.minute,
            true
        ).show()
    }

    private fun rescheduleAndRender() {
        AlarmScheduler.scheduleNextAlarm(this)
        render()
    }

    // ---------------------------------------------------------------- formatting

    private fun dp(v: Int): Int = ui.dp(v)

    private fun fmt(minutes: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)

    private fun abbr(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun fullDay(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

package com.mrjshzk.riseup

import android.content.Context
import android.content.SharedPreferences
import java.time.DayOfWeek
import java.util.Locale

/**
 * One weekday of the alarm programme.
 *
 * The week is stored as seven independent records: a day can be enabled or off
 * without touching the others, and every day keeps its own wall-clock time.
 * Times are wall-clock on purpose — 06:15 stays 06:15 after a DST change.
 */
data class DaySchedule(
    val dayOfWeek: DayOfWeek,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int
) {
    val minutesOfDay: Int get() = hour * 60 + minute

    companion object {
        const val DEFAULT_MINUTES = 7 * 60
    }
}

object Prefs {
    private const val FILE = "riseup"

    /** Bumped key marking that the legacy single-time pair has been expanded into a week. */
    private const val KEY_WEEK_MIGRATED = "week_migrated"

    /** Pre-v2 keys: one hour+minute for every day, plus a global on/off switch. */
    private const val KEY_LEGACY_MINUTES = "alarm_minutes"
    private const val KEY_LEGACY_ENABLED = "enabled"

    val DAYS: List<DayOfWeek> = DayOfWeek.values().toList()

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun key(day: DayOfWeek, field: String) =
        "day_" + day.name.lowercase(Locale.US) + "_" + field

    // ---------------------------------------------------------------- week model

    /** The whole week, Monday first. Cheap: seven preference reads. */
    fun week(c: Context): List<DaySchedule> {
        val prefs = p(c)
        if (!prefs.getBoolean(KEY_WEEK_MIGRATED, false)) migrate(prefs)
        return DAYS.map { day ->
            // Coerce: a hand-edited or corrupted value must never crash the app.
            val minutes = prefs.getInt(key(day, "minutes"), DaySchedule.DEFAULT_MINUTES)
                .coerceIn(0, MINUTES_PER_DAY - 1)
            DaySchedule(
                dayOfWeek = day,
                enabled = prefs.getBoolean(key(day, "enabled"), false),
                hour = minutes / 60,
                minute = minutes % 60
            )
        }
    }

    fun day(c: Context, day: DayOfWeek): DaySchedule =
        week(c).first { it.dayOfWeek == day }

    fun enabledDays(c: Context): List<DaySchedule> = week(c).filter { it.enabled }

    fun setDay(c: Context, schedule: DaySchedule) {
        val minutes = (schedule.hour * 60 + schedule.minute).coerceIn(0, MINUTES_PER_DAY - 1)
        p(c).edit()
            .putBoolean(key(schedule.dayOfWeek, "enabled"), schedule.enabled)
            .putInt(key(schedule.dayOfWeek, "minutes"), minutes)
            .apply()
    }

    fun setDayEnabled(c: Context, day: DayOfWeek, enabled: Boolean) {
        p(c).edit().putBoolean(key(day, "enabled"), enabled).apply()
    }

    fun setDayTime(c: Context, day: DayOfWeek, hour: Int, minute: Int) {
        val minutes = (hour * 60 + minute).coerceIn(0, MINUTES_PER_DAY - 1)
        p(c).edit().putInt(key(day, "minutes"), minutes).apply()
    }

    /** Copy [from]'s time onto Monday..Friday, leaving every day's on/off flag alone. */
    fun copyTimeToWeekdays(c: Context, from: DaySchedule) {
        val minutes = from.minutesOfDay
        val e = p(c).edit()
        for (day in DAYS) {
            if (day.value <= DayOfWeek.FRIDAY.value) e.putInt(key(day, "minutes"), minutes)
        }
        e.apply()
    }

    /** Copy [from]'s time onto all seven days, leaving every day's on/off flag alone. */
    fun copyTimeToAllDays(c: Context, from: DaySchedule) {
        val minutes = from.minutesOfDay
        val e = p(c).edit()
        for (day in DAYS) e.putInt(key(day, "minutes"), minutes)
        e.apply()
    }

    /**
     * v1 stored a single daily time plus one global switch. The old alarm rang
     * every day at that time, so the only migration that preserves what the user
     * had is to seed all seven days with the same time and the same flag.
     * Nothing is deleted: the legacy keys stay readable (and harmless) forever.
     */
    private fun migrate(prefs: SharedPreferences) {
        val minutes = prefs.getInt(KEY_LEGACY_MINUTES, DaySchedule.DEFAULT_MINUTES)
            .coerceIn(0, MINUTES_PER_DAY - 1)
        val enabled = prefs.getBoolean(KEY_LEGACY_ENABLED, true)
        val e = prefs.edit()
        for (day in DAYS) {
            e.putBoolean(key(day, "enabled"), enabled)
            e.putInt(key(day, "minutes"), minutes)
        }
        e.putBoolean(KEY_WEEK_MIGRATED, true)
        e.apply()
    }

    // ---------------------------------------------------------------- other settings

    fun barcode(c: Context): String? = p(c).getString("barcode", null)
    fun setBarcode(c: Context, v: String) = p(c).edit().putString("barcode", v).apply()
    fun clearBarcode(c: Context) = p(c).edit().remove("barcode").apply()

    fun nextTrigger(c: Context): Long = p(c).getLong("next_trigger", 0L)
    fun setNextTrigger(c: Context, v: Long) = p(c).edit().putLong("next_trigger", v).apply()

    fun guardPending(c: Context): Boolean = p(c).getBoolean("guard_pending", false)
    fun setGuardPending(c: Context, v: Boolean) = p(c).edit().putBoolean("guard_pending", v).apply()

    /** Wall-clock instant the guard escalates back to the full alarm; 0 when none is armed. */
    fun guardDeadline(c: Context): Long = p(c).getLong("guard_deadline", 0L)
    fun setGuardDeadline(c: Context, v: Long) = p(c).edit().putLong("guard_deadline", v).apply()

    /** Timestamp of a successful dismiss-code scan. Used instead of an activity
     *  result, which is unreliable when the alarm activity runs singleInstance. */
    fun scanOkAt(c: Context): Long = p(c).getLong("scan_ok_at", 0L)
    fun setScanOkAt(c: Context, v: Long) = p(c).edit().putLong("scan_ok_at", v).apply()

    private const val MINUTES_PER_DAY = 24 * 60
}

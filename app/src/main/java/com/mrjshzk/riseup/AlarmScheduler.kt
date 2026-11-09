package com.mrjshzk.riseup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmScheduler {
    const val ACTION_ALARM = "com.mrjshzk.riseup.ACTION_ALARM"
    const val ACTION_GUARD = "com.mrjshzk.riseup.ACTION_GUARD"

    private const val REQ_ALARM = 1001
    private const val REQ_GUARD = 1002
    private const val REQ_TEST = 1003

    const val GUARD_DELAY_MS = 90_000L

    /** Today plus one full lap of the week, so "only this weekday is on" still resolves. */
    private const val LOOKAHEAD_DAYS = 7L

    private fun pi(ctx: Context, req: Int, action: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            ctx, req, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canScheduleExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
    }

    // ---------------------------------------------------------------- next trigger

    /**
     * The strictly-next future occurrence of any enabled day, in [now]'s zone.
     * Null when every day is off.
     *
     * Days are matched by their wall-clock day-of-week, so the scan runs from
     * today to today+7 inclusive: a day whose time has already passed is simply
     * skipped today and found again next week. The winner is the earliest
     * candidate, which folds the Sunday->Monday wrap into the same loop.
     *
     * DST, deterministic and documented:
     *  - skipped (spring-forward) wall time -> fire at the instant the clocks
     *    jump forward, i.e. the first real instant at or after the requested
     *    time. An 01:30 alarm on a 01:00->02:00 jump rings at 02:00, never early.
     *  - repeated (fall-back) wall time -> the earliest occurrence that is still
     *    in the future; if the first pass has already gone by, the second pass is
     *    used rather than dropping the day.
     */
    fun nextTrigger(week: List<DaySchedule>, now: ZonedDateTime): ZonedDateTime? {
        val zone = now.zone
        var best: ZonedDateTime? = null
        for (offset in 0..LOOKAHEAD_DAYS) {
            val date = now.toLocalDate().plusDays(offset)
            val schedule = week.firstOrNull { it.enabled && it.dayOfWeek == date.dayOfWeek }
                ?: continue
            for (candidate in occurrences(date, schedule, zone)) {
                if (!candidate.isAfter(now)) continue
                if (best == null || candidate.isBefore(best)) best = candidate
            }
        }
        return best
    }

    fun nextTriggerMillis(week: List<DaySchedule>, now: ZonedDateTime): Long? =
        nextTrigger(week, now)?.toInstant()?.toEpochMilli()

    /**
     * Every real instant that shows [date] at the schedule's wall-clock time.
     * One for a normal day, two for a fall-back overlap, and the transition
     * instant for a spring-forward gap (where the wall time never exists).
     */
    private fun occurrences(
        date: LocalDate,
        schedule: DaySchedule,
        zone: ZoneId
    ): List<ZonedDateTime> {
        val local = LocalDateTime.of(date, LocalTime.of(schedule.hour, schedule.minute))
        val offsets = zone.rules.getValidOffsets(local)
        return when {
            offsets.size == 1 -> listOf(ZonedDateTime.ofLocal(local, zone, offsets[0]))
            offsets.isEmpty() -> {
                val jump = zone.rules.getTransition(local) ?: return emptyList()
                listOf(jump.instant.atZone(zone))
            }
            // Earliest instant first: the larger offset is the earlier one.
            else -> offsets.sortedByDescending { it.totalSeconds }
                .map { ZonedDateTime.ofLocal(local, zone, it) }
        }
    }

    // ---------------------------------------------------------------- scheduling

    fun scheduleNextAlarm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val next = nextTrigger(Prefs.week(ctx), ZonedDateTime.now())
        if (next == null) {
            // No day is enabled: leave nothing armed for a stale time.
            cancelAlarm(ctx)
            return
        }
        val at = next.toInstant().toEpochMilli()
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, AlarmActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val info = AlarmManager.AlarmClockInfo(at, show)
        try {
            am.setAlarmClock(info, pi(ctx, REQ_ALARM, ACTION_ALARM))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, REQ_ALARM, ACTION_ALARM))
        }
        Prefs.setNextTrigger(ctx, at)
    }

    fun cancelAlarm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pi(ctx, REQ_ALARM, ACTION_ALARM))
        Prefs.setNextTrigger(ctx, 0L)
    }

    fun scheduleGuard(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + GUARD_DELAY_MS
        Prefs.setGuardPending(ctx, true)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, REQ_GUARD, ACTION_GUARD))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, REQ_GUARD, ACTION_GUARD))
        }
    }

    fun cancelGuard(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pi(ctx, REQ_GUARD, ACTION_GUARD))
        Prefs.setGuardPending(ctx, false)
    }

    /** Fire a full alarm in [delayMs] so the whole chain can be proven without waiting for 07:00. */
    fun scheduleTest(ctx: Context, delayMs: Long = 10_000L) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + delayMs
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, REQ_TEST, ACTION_ALARM))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, REQ_TEST, ACTION_ALARM))
        }
    }
}

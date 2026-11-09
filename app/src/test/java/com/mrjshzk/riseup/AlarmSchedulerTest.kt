package com.mrjshzk.riseup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * All cases are pure: a fixed week plus a fixed "now". The zone is passed in,
 * so nothing here depends on the test machine's timezone.
 *
 * Europe/Lisbon is used for the DST cases:
 *   2026-03-29 01:00 WET -> 02:00 WEST  (spring forward, 01:00-02:00 does not exist)
 *   2026-10-25 02:00 WEST -> 01:00 WET  (fall back, 01:00-02:00 happens twice)
 */
class AlarmSchedulerTest {

    private val lisbon = ZoneId.of("Europe/Lisbon")

    private fun at(
        year: Int, month: Int, day: Int, hour: Int, minute: Int,
        zone: ZoneId = lisbon
    ): ZonedDateTime = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

    private fun schedule(day: DayOfWeek, hour: Int, minute: Int, enabled: Boolean = true) =
        DaySchedule(day, enabled, hour, minute)

    /** A full 7-day week; days not mentioned are off at 07:00, like an untouched install. */
    private fun week(vararg days: DaySchedule): List<DaySchedule> =
        DayOfWeek.values().map { d ->
            days.firstOrNull { it.dayOfWeek == d } ?: DaySchedule(d, false, 7, 0)
        }

    private fun assertInstant(expected: String, actual: ZonedDateTime?) {
        assertNotNull("expected a trigger at $expected", actual)
        assertEquals(Instant.parse(expected), actual!!.toInstant())
        assertEquals(lisbon, actual.zone)
    }

    // ------------------------------------------------------------ plain week logic

    @Test
    fun alarmLaterTodayStaysToday() {
        val now = at(2026, 11, 9, 5, 0) // Monday
        val next = AlarmScheduler.nextTrigger(week(schedule(MONDAY, 6, 15)), now)
        assertInstant("2026-11-09T06:15:00Z", next)
    }

    @Test
    fun alarmAlreadyPassedTodayMovesToTheNextEnabledDay() {
        val now = at(2026, 11, 9, 7, 0) // Monday, 06:15 already gone
        val next = AlarmScheduler.nextTrigger(
            week(schedule(MONDAY, 6, 15), schedule(TUESDAY, 7, 30)),
            now
        )
        assertInstant("2026-11-10T07:30:00Z", next)
    }

    @Test
    fun weekBoundaryWrapsSundayToMonday() {
        val now = at(2026, 11, 15, 22, 0) // Sunday night
        val next = AlarmScheduler.nextTrigger(
            week(schedule(MONDAY, 6, 15), schedule(SUNDAY, 9, 30, enabled = false)),
            now
        )
        assertInstant("2026-11-16T06:15:00Z", next)
    }

    @Test
    fun onlyEnabledDayAlreadyPassedIsNextWeek() {
        val now = at(2026, 11, 9, 7, 0) // Monday
        val next = AlarmScheduler.nextTrigger(week(schedule(MONDAY, 6, 15)), now)
        assertInstant("2026-11-16T06:15:00Z", next)
    }

    @Test
    fun allDaysDisabledYieldsNoTrigger() {
        val now = at(2026, 11, 9, 5, 0)
        assertNull(AlarmScheduler.nextTrigger(week(), now))
        assertNull(AlarmScheduler.nextTriggerMillis(week(), now))
    }

    @Test
    fun exactNowIsNotAnOccurrenceTheTriggerMustBeStrictlyFuture() {
        val now = at(2026, 11, 9, 6, 15)
        val next = AlarmScheduler.nextTrigger(week(schedule(MONDAY, 6, 15)), now)
        assertInstant("2026-11-16T06:15:00Z", next)
    }

    @Test
    fun midnightAlarmRollsOverAtEndOfDay() {
        val now = at(2026, 11, 9, 23, 59) // Monday
        val next = AlarmScheduler.nextTrigger(
            week(schedule(MONDAY, 0, 0), schedule(TUESDAY, 0, 0)),
            now
        )
        assertInstant("2026-11-10T00:00:00Z", next)
    }

    @Test
    fun disabledTodayIsSkippedEvenWhenItIsSoonest() {
        val now = at(2026, 11, 9, 5, 0)
        val next = AlarmScheduler.nextTrigger(
            week(
                schedule(MONDAY, 6, 15, enabled = false),
                schedule(TUESDAY, 7, 30)
            ),
            now
        )
        assertInstant("2026-11-10T07:30:00Z", next)
    }

    @Test
    fun earliestOfSeveralEnabledDaysWins() {
        val now = at(2026, 11, 11, 10, 0) // Wednesday
        val next = AlarmScheduler.nextTrigger(
            week(
                schedule(TUESDAY, 6, 15),
                schedule(WEDNESDAY, 20, 0),
                schedule(THURSDAY, 6, 0),
                schedule(SATURDAY, 9, 30)
            ),
            now
        )
        assertInstant("2026-11-11T20:00:00Z", next)
    }

    @Test
    fun fridayThenWeekendOffWrapsToMonday() {
        val now = at(2026, 11, 13, 7, 0) // Friday, after 06:15
        val next = AlarmScheduler.nextTrigger(
            week(
                schedule(MONDAY, 6, 15),
                schedule(FRIDAY, 6, 15),
                schedule(SATURDAY, 9, 30, enabled = false),
                schedule(SUNDAY, 9, 30, enabled = false)
            ),
            now
        )
        assertInstant("2026-11-16T06:15:00Z", next)
    }

    @Test
    fun computationUsesTheGivenZoneNotTheSystemDefault() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = at(2026, 11, 9, 7, 0, tokyo)
        val next = AlarmScheduler.nextTrigger(week(schedule(MONDAY, 6, 15)), now)
        assertNotNull(next)
        assertEquals(tokyo, next!!.zone)
        assertEquals(Instant.parse("2026-11-15T21:15:00Z"), next.toInstant()) // Mon 06:15 JST
    }

    // ------------------------------------------------------------ DST

    @Test
    fun springForwardGapFiresAtTheMomentTheClocksJump() {
        // 01:30 does not exist on 2026-03-29 in Lisbon; the clock goes 01:00 -> 02:00.
        // Rule: fire at the jump (02:00 WEST), i.e. 01:00Z, never before the request.
        val now = at(2026, 3, 29, 0, 0)
        val next = AlarmScheduler.nextTrigger(week(schedule(SUNDAY, 1, 30)), now)
        assertInstant("2026-03-29T01:00:00Z", next)
        assertEquals(2, next!!.hour)
        assertEquals(0, next.minute)
    }

    @Test
    fun springForwardGapAlreadyBehindUsSkipsToNextWeek() {
        val now = at(2026, 3, 29, 3, 0) // after the jump
        val next = AlarmScheduler.nextTrigger(week(schedule(SUNDAY, 1, 30)), now)
        assertInstant("2026-04-05T00:30:00Z", next) // 01:30 WEST the following Sunday
    }

    @Test
    fun fallBackUsesTheFirstOccurrenceOfTheRepeatedHour() {
        // 01:30 happens twice on 2026-10-25: 01:30 WEST (00:30Z) then 01:30 WET (01:30Z).
        val now = ZonedDateTime.ofInstant(Instant.parse("2026-10-25T00:00:00Z"), lisbon) // 01:00 WEST
        val next = AlarmScheduler.nextTrigger(week(schedule(SUNDAY, 1, 30)), now)
        assertInstant("2026-10-25T00:30:00Z", next)
        assertEquals(3600, next!!.offset.totalSeconds)
    }

    @Test
    fun fallBackSecondPassIsUsedWhenTheFirstHasPassed() {
        // now is 01:00:10 WET, the second pass through 01:00.
        val now = ZonedDateTime.ofInstant(Instant.parse("2026-10-25T01:00:10Z"), lisbon)
        val next = AlarmScheduler.nextTrigger(week(schedule(SUNDAY, 1, 30)), now)
        assertInstant("2026-10-25T01:30:00Z", next)
        assertEquals(0, next!!.offset.totalSeconds)
    }
}

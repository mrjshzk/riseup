package com.mrjshzk.riseup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Clock and timezone changes invalidate any armed alarm: the trigger was
 * computed against the old wall clock. Recompute and re-arm. Cheap on purpose —
 * no service, no wake lock, one preference read plus one setAlarmClock.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED -> AlarmScheduler.scheduleNextAlarm(context)
        }
    }
}

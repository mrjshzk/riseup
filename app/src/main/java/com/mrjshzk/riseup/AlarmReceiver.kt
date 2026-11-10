package com.mrjshzk.riseup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_ALARM -> {
                // A real alarm supersedes any guard still in flight.
                AlarmScheduler.cancelGuard(context)
                fire(context, AlarmService.MODE_ALARM)
                AlarmActivity.launch(context, AlarmService.MODE_ALARM)
            }

            AlarmScheduler.ACTION_GUARD -> {
                if (!Prefs.guardPending(context)) return
                AlarmScheduler.scheduleEscalation(context)
                fire(context, AlarmService.MODE_GUARD)
                AlarmActivity.launch(context, AlarmService.MODE_GUARD)
            }

            AlarmScheduler.ACTION_ESCALATE -> {
                // Steps done or code rescanned already cleared the flag.
                if (!Prefs.guardPending(context)) return
                AlarmScheduler.cancelGuard(context)
                fire(context, AlarmService.MODE_ALARM)
                AlarmActivity.launch(context, AlarmService.MODE_ALARM)
            }
        }
    }

    private fun fire(context: Context, mode: String) {
        try {
            ContextCompat.startForegroundService(context, AlarmService.intent(context, mode))
        } catch (e: Exception) {
            // If the OS refuses the FGS start, still bring the screen up: the
            // activity plays the alarm itself as a fallback.
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.setGuardPending(context, false)
        Prefs.setGuardDeadline(context, 0L)
        AlarmScheduler.scheduleNextAlarm(context)
    }
}

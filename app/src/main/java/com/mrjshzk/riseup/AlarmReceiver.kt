package com.mrjshzk.riseup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_ALARM -> {
                Prefs.setGuardPending(context, false)
                fire(context, AlarmService.MODE_ALARM)
                AlarmActivity.launch(context, AlarmService.MODE_ALARM)
            }

            AlarmScheduler.ACTION_GUARD -> {
                if (!Prefs.guardPending(context)) return
                fire(context, AlarmService.MODE_GUARD)
                AlarmActivity.launch(context, AlarmService.MODE_GUARD)
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
        AlarmScheduler.scheduleNextAlarm(context)
    }
}

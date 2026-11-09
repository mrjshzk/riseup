package com.mrjshzk.riseup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AlarmService : Service() {

    companion object {
        const val MODE_ALARM = "alarm"
        const val MODE_GUARD = "guard"

        private const val CHANNEL_ID = "riseup_alarm"
        private const val NOTIF_ID = 1001
        private const val ACTION_START = "com.mrjshzk.riseup.START"
        private const val EXTRA_MODE = "mode"

        fun intent(ctx: Context, mode: String): Intent =
            Intent(ctx, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode)

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AlarmService::class.java))
        }
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var mode = MODE_ALARM
    private var gain = 0.2f

    private val ramp = object : Runnable {
        override fun run() {
            gain = (gain + 0.08f).coerceAtMost(1f)
            player?.setVolume(gain, gain)
            handler.postDelayed(this, 5_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "riseup:alarm")
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_ALARM
        startForeground(NOTIF_ID, buildNotification())
        try {
            wakeLock?.acquire(20 * 60 * 1000L)
        } catch (e: Exception) {
            // ignore
        }
        if (mode == MODE_ALARM) startRinging() else stopSound()
        return START_STICKY
    }

    override fun onDestroy() {
        stopSound()
        handler.removeCallbacksAndMessages(null)
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            // ignore
        }
        super.onDestroy()
    }

    private fun startRinging() {
        if (player != null) {
            startVibrating()
            return
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            p.setDataSource(this, uri)
            p.isLooping = true
            gain = 0.2f
            p.setVolume(gain, gain)
            p.prepare()
            p.start()
            player = p
            handler.removeCallbacks(ramp)
            handler.postDelayed(ramp, 3_000L)
        } catch (e: Exception) {
            p.release()
        }
        startVibrating()
    }

    private fun startVibrating() {
        val v = vibrator ?: return
        val pattern = longArrayOf(0L, 700L, 500L)
        try {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun stopSound() {
        handler.removeCallbacks(ramp)
        try {
            player?.stop()
        } catch (e: Exception) {
            // ignore
        }
        player?.release()
        player = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(CHANNEL_ID, "Wake alarm", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null)
        ch.enableVibration(false)
        ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val full = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AlarmActivity::class.java)
                .putExtra(AlarmActivity.EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(
                getString(
                    if (mode == MODE_ALARM) R.string.notif_title_alarm else R.string.notif_title_guard
                )
            )
            .setContentText(
                getString(
                    if (mode == MODE_ALARM) R.string.notif_text_alarm else R.string.notif_text_guard
                )
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(full)
        if (mode == MODE_ALARM) {
            builder.setFullScreenIntent(full, true)
        }
        return builder.build()
    }
}

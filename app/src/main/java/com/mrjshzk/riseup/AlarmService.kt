package com.mrjshzk.riseup

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns everything that must keep working while no screen is showing: the ringing in
 * alarm mode, and the step count in guard mode.
 *
 * The guard used to count steps inside AlarmActivity. That only worked if the activity
 * actually reached the screen, which on Android 10+ it usually does not: a broadcast
 * receiver in the background is not allowed to start an activity, and the guard
 * notification carried no full-screen intent. Counting here — with the wake lock this
 * service already holds — means the 40 steps register even with the screen off.
 */
class AlarmService : Service() {

    companion object {
        const val MODE_ALARM = "alarm"
        const val MODE_GUARD = "guard"
        const val STEPS_TARGET = 40

        private const val CHANNEL_ID = "riseup_alarm"
        private const val NOTIF_ID = 1001
        private const val ACTION_START = "com.mrjshzk.riseup.START"
        private const val EXTRA_MODE = "mode"

        /** Steps still owed in the current guard. Main thread only. */
        var remainingSteps = STEPS_TARGET
            private set

        /** The guard screen, while it is showing. Called on the main thread. */
        var stepListener: ((Int) -> Unit)? = null

        fun intent(ctx: Context, mode: String): Intent =
            Intent(ctx, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode)

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AlarmService::class.java))
        }

        /** True when the guard can be satisfied by walking rather than by rescanning. */
        fun canCountSteps(ctx: Context): Boolean {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) !=
                PackageManager.PERMISSION_GRANTED
            ) return false
            val sm = ctx.getSystemService(SensorManager::class.java) ?: return false
            return sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null ||
                sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
        }
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var mode = MODE_ALARM
    private var gain = 0.2f

    // Two sources, one count. STEP_COUNTER is accurate but batched (some phones only
    // report after ~10 steps, and the first reading may already include steps taken
    // since registration); STEP_DETECTOR reports each step promptly but can drop events.
    // Taking the larger of the two covers both failure modes.
    private var counting = false
    private var counterBase = -1f
    private var counterSteps = 0
    private var detectorSteps = 0

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
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "riseup:alarm")?.apply {
            // Escalation re-issues start commands to the same instance; one release must
            // be enough to let go.
            setReferenceCounted(false)
        }
        sensorManager = getSystemService(SensorManager::class.java)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A sticky restart arrives with a null intent: resume what was actually running
        // rather than assuming a full alarm.
        mode = intent?.getStringExtra(EXTRA_MODE)
            ?: if (Prefs.guardPending(this)) MODE_GUARD else MODE_ALARM
        startForeground(NOTIF_ID, buildNotification(fullScreen = true))
        try {
            wakeLock?.acquire(20 * 60 * 1000L)
        } catch (e: Exception) {
            // ignore
        }
        if (mode == MODE_ALARM) {
            stopCounting()
            startRinging()
        } else {
            stopSound()
            startCounting()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopSound()
        stopCounting()
        remainingSteps = STEPS_TARGET
        handler.removeCallbacksAndMessages(null)
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            // ignore
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- ringing

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

    // ---------------------------------------------------------------- steps

    private val stepSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    val value = event.values.getOrNull(0) ?: return
                    if (counterBase < 0f) counterBase = value
                    counterSteps = (value - counterBase).toInt().coerceAtLeast(0)
                }
                Sensor.TYPE_STEP_DETECTOR -> detectorSteps += 1
                else -> return
            }
            val taken = maxOf(counterSteps, detectorSteps)
            val remaining = (STEPS_TARGET - taken).coerceAtLeast(0)
            if (remaining == remainingSteps) return
            remainingSteps = remaining
            stepListener?.invoke(remaining)
            if (remaining == 0) completeGuard() else refreshNotification()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // unused
        }
    }

    private fun startCounting() {
        if (counting) return
        remainingSteps = STEPS_TARGET
        counterBase = -1f
        counterSteps = 0
        detectorSteps = 0
        stepListener?.invoke(remainingSteps)
        if (!canCountSteps(this)) return
        val sm = sensorManager ?: return
        var registered = false
        sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            registered = sm.registerListener(
                stepSensorListener, it, SensorManager.SENSOR_DELAY_NORMAL, handler
            ) || registered
        }
        sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            registered = sm.registerListener(
                stepSensorListener, it, SensorManager.SENSOR_DELAY_NORMAL, handler
            ) || registered
        }
        counting = registered
    }

    private fun stopCounting() {
        if (!counting) return
        counting = false
        try {
            sensorManager?.unregisterListener(stepSensorListener)
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 40 steps reached, screen or no screen: disarm the escalation and go quiet. */
    private fun completeGuard() {
        stopCounting()
        AlarmScheduler.cancelGuard(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------------------------------------------------------- notification

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(CHANNEL_ID, "Wake alarm", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null)
        ch.enableVibration(false)
        ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ch)
    }

    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIF_ID, buildNotification(fullScreen = false))
    }

    private fun guardText(): String {
        val deadline = Prefs.guardDeadline(this)
        val steps = resources.getQuantityString(R.plurals.notif_steps, remainingSteps, remainingSteps)
        if (deadline <= 0L) return steps
        val at = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(deadline))
        return getString(R.string.notif_text_guard, steps, at)
    }

    private fun buildNotification(fullScreen: Boolean): Notification {
        val full = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AlarmActivity::class.java)
                .putExtra(AlarmActivity.EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val guard = mode == MODE_GUARD
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(
                getString(if (guard) R.string.notif_title_guard else R.string.notif_title_alarm)
            )
            .setContentText(
                if (guard) guardText() else getString(R.string.notif_text_alarm)
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(full)
        // Both modes need it: without a full-screen intent the guard screen cannot reach
        // a locked phone, because a background receiver may not start activities itself.
        if (fullScreen) builder.setFullScreenIntent(full, true)
        return builder.build()
    }
}

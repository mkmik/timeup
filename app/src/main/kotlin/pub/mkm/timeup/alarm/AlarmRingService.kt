package pub.mkm.timeup.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pub.mkm.timeup.graph

/**
 * Foreground service that exists only while the alarm rings. Plays the ringtone on the
 * alarm stream (independent of ring volume, so silent mode does not mute it), vibrates,
 * and shows the full-screen notification. Stops on its own after the configured time.
 */
class AlarmRingService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var priorityId: String? = null
    private val autoStop = Runnable { finish(postQuiet = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> startRinging(intent.getStringExtra(EXTRA_PRIORITY_ID) ?: return stopNow())
            ACTION_KEEP_GOING -> finish(postQuiet = false)
            ACTION_STOP_SESSION -> {
                graph.actions.stop()
                finish(postQuiet = false)
            }
            else -> return stopNow()
        }
        return START_NOT_STICKY
    }

    private fun stopNow(): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    private fun startRinging(id: String) {
        val g = graph
        val p = g.repo.state.value.priorities[id] ?: return stopSelf()
        priorityId = id
        _ringing.value = true
        startForeground(Notifier.ID_ALARM, g.notifier.ringingNotification(p), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "timeup:alarm")
            .also { it.acquire(MAX_RING_MS) }

        playSound(g.settings.alarmSound?.let(Uri::parse))
        if (g.settings.vibrate) {
            getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 700, 500, 700, 1200), 0),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        }
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, g.settings.autoStopMinutes.coerceIn(1, 10) * 60_000L)
    }

    private fun playSound(preferred: Uri?) {
        val candidates = listOfNotNull(
            preferred,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (e: Exception) {
                mp.release()
            }
        }
    }

    private fun finish(postQuiet: Boolean) {
        handler.removeCallbacks(autoStop)
        player?.runCatching { stop() }
        player?.release()
        player = null
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        if (postQuiet) priorityId?.let { id -> graph.repo.state.value.priorities[id]?.let(graph.notifier::postBudgetSpentQuiet) }
        _ringing.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        player?.release()
        player = null
        wakeLock?.takeIf { it.isHeld }?.release()
        _ringing.value = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "pub.mkm.timeup.alarm.RING"
        const val ACTION_KEEP_GOING = "pub.mkm.timeup.alarm.KEEP_GOING"
        const val ACTION_STOP_SESSION = "pub.mkm.timeup.alarm.STOP_SESSION"
        const val EXTRA_PRIORITY_ID = "priority_id"
        private const val MAX_RING_MS = 11 * 60_000L

        private val _ringing = MutableStateFlow(false)
        /** True while the alarm is sounding; [AlarmActivity] closes itself when it goes false. */
        val ringing: StateFlow<Boolean> = _ringing

        fun intent(context: Context, action: String, priorityId: String): Intent =
            Intent(context, AlarmRingService::class.java).setAction(action).putExtra(EXTRA_PRIORITY_ID, priorityId)
    }
}

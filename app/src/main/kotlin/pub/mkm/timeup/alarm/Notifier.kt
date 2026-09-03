package pub.mkm.timeup.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import pub.mkm.timeup.R
import pub.mkm.timeup.domain.Priority
import pub.mkm.timeup.domain.PriorityStatus
import pub.mkm.timeup.domain.formatHm
import pub.mkm.timeup.ui.MainActivity
import kotlin.math.max

/** All notification channels and builders in one place. */
class Notifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "Budget alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings when a priority's daily budget is spent. The sound is played by the app on the alarm stream."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WRAPUP, "Wrap-up", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A few minutes before a budget runs out."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SESSION, "Running priority", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Live countdown while a priority is running."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    val notificationsEnabled: Boolean get() = nm.areNotificationsEnabled()
    val canUseFullScreenIntent: Boolean get() = nm.canUseFullScreenIntent()
    val canPostPromoted: Boolean get() = nm.canPostPromotedNotifications()

    private fun openAppIntent(code: Int): PendingIntent = PendingIntent.getActivity(
        context, code, Intent(context, MainActivity::class.java), FLAGS,
    )

    private fun icon() = Icon.createWithResource(context, R.drawable.ic_stat)

    /** The Live Update: an ongoing, promoted notification with a system-rendered countdown. */
    fun postLiveUpdate(status: PriorityStatus, endsAt: Long) {
        val p = status.priority
        val stop = PendingIntent.getBroadcast(
            context, 1, Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_STOP), FLAGS,
        )
        val progress = ((status.spentMs * 100) / max(1L, p.budgetMs)).coerceIn(0, 100).toInt()
        val style = NotificationCompat.ProgressStyle()
            .setProgress(progress)
            .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(100)))
            .setStyledByProgress(false)
        val n = NotificationCompat.Builder(context, CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(p.name)
            .setContentText(if (status.overBudget) "Over budget" else "Budget ends")
            .setColor(p.color)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(0))
            .setWhen(endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(formatHm(status.remainingMs))
            .setStyle(style)
            .addAction(NotificationCompat.Action.Builder(IconCompat.createWithResource(context, R.drawable.ic_stat), "Stop", stop).build())
            .addAction(NotificationCompat.Action.Builder(IconCompat.createWithResource(context, R.drawable.ic_stat), "Switch…", openAppIntent(0)).build())
            .build()
        nm.notify(ID_LIVE, n)
    }

    fun cancelLiveUpdate() = nm.cancel(ID_LIVE)

    /** Shown by the ringing foreground service; the full-screen intent opens [AlarmActivity] over the lock screen. */
    fun ringingNotification(p: Priority): Notification {
        val full = PendingIntent.getActivity(context, 2, AlarmActivity.intent(context, p.id), FLAGS)
        val stopSession = PendingIntent.getService(context, 3, AlarmRingService.intent(context, AlarmRingService.ACTION_STOP_SESSION, p.id), FLAGS)
        val keepGoing = PendingIntent.getService(context, 4, AlarmRingService.intent(context, AlarmRingService.ACTION_KEEP_GOING, p.id), FLAGS)
        return Notification.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${p.name}: budget spent")
            .setContentText("Stop, or keep going into overtime")
            .setColor(p.color)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(Notification.Action.Builder(icon(), "Stop ${p.name}", stopSession).build())
            .addAction(Notification.Action.Builder(icon(), "Keep going", keepGoing).build())
            .build()
    }

    fun postWrapUp(p: Priority, remainingMs: Long) {
        val n = Notification.Builder(context, CH_WRAPUP)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${p.name}: ${formatHm(remainingMs)} left")
            .setContentText("Time to wrap up")
            .setColor(p.color)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(0))
            .build()
        nm.notify(ID_WRAPUP, n)
    }

    /** Left behind when the alarm auto-stops without an answer. */
    fun postBudgetSpentQuiet(p: Priority) {
        val stop = PendingIntent.getBroadcast(
            context, 1, Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_STOP), FLAGS,
        )
        val n = Notification.Builder(context, CH_WRAPUP)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${p.name}: budget spent")
            .setContentText("Still running in overtime")
            .setColor(p.color)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(0))
            .addAction(Notification.Action.Builder(icon(), "Stop", stop).build())
            .build()
        nm.notify(ID_SPENT, n)
    }

    fun cancelAlarmLeftovers() {
        nm.cancel(ID_SPENT)
        nm.cancel(ID_WRAPUP)
    }

    companion object {
        const val CH_ALARM = "alarm"
        const val CH_WRAPUP = "wrapup"
        const val CH_SESSION = "session"
        const val ID_LIVE = 1
        const val ID_ALARM = 2
        const val ID_WRAPUP = 3
        const val ID_SPENT = 4
        const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}

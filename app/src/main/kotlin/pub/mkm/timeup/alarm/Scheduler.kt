package pub.mkm.timeup.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pub.mkm.timeup.control.Shortcuts
import pub.mkm.timeup.control.ToggleTileService
import pub.mkm.timeup.data.Repository
import pub.mkm.timeup.domain.DayView
import pub.mkm.timeup.domain.Days
import pub.mkm.timeup.ui.MainActivity
import pub.mkm.timeup.widget.TimeUpWidget
import java.time.ZoneId

/**
 * Derives every scheduled thing (budget alarm, wrap-up, midnight tick, Live Update, widgets,
 * tile, shortcuts) from the current state. Idempotent: call it after any change and on every
 * system event; it cancels and re-plans from scratch. No background service is involved.
 */
class Scheduler(
    private val context: Context,
    private val repo: Repository,
    private val notifier: Notifier,
    private val gate: AlarmGate,
    private val scope: CoroutineScope,
) {
    private val am = context.getSystemService(AlarmManager::class.java)

    val exactAlarmsAllowed: Boolean get() = am.canScheduleExactAlarms()

    private fun receiverIntent(action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code, Intent(context, AlarmReceiver::class.java).setAction(action), Notifier.FLAGS,
    )

    private val budgetPi get() = receiverIntent(AlarmReceiver.ACTION_BUDGET, 10)
    private val wrapUpPi get() = receiverIntent(AlarmReceiver.ACTION_WRAPUP, 11)
    private val midnightPi get() = receiverIntent(AlarmReceiver.ACTION_MIDNIGHT, 12)

    @Synchronized
    fun resync() {
        val now = System.currentTimeMillis()
        val view = DayView(repo.state.value, Days.window(now, ZoneId.systemDefault()), now)
        gate.prune(view.window.start)

        am.cancel(budgetPi)
        am.cancel(wrapUpPi)

        val running = view.runningStatus
        if (running != null) {
            val endsAt = view.budgetEndsAt!!
            if (!gate.hasRung(running.priority.id, view.window.start)) {
                // A trigger in the past fires immediately: "the moment the budget is spent".
                if (exactAlarmsAllowed) {
                    val show = PendingIntent.getActivity(context, 13, Intent(context, MainActivity::class.java), Notifier.FLAGS)
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(endsAt, show), budgetPi)
                } else {
                    // Without the exact-alarm permission this may be minutes late; the UI says so.
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, budgetPi)
                }
            }
            val wrapAt = view.wrapUpAt
            if (wrapAt != null && wrapAt > now) {
                if (exactAlarmsAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wrapAt, wrapUpPi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wrapAt, wrapUpPi)
            }
            notifier.postLiveUpdate(running, endsAt)
        } else {
            notifier.cancelLiveUpdate()
            notifier.cancelAlarmLeftovers()
        }

        // Midnight tick: recompute for the new day (accounting rolls over, the session keeps running).
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, view.window.end + 1_000, midnightPi)

        refreshSurfaces()
    }

    fun refreshSurfaces() {
        Shortcuts.update(context, repo.state.value)
        TileService.requestListeningState(context, ComponentName(context, ToggleTileService::class.java))
        scope.launch { runCatching { TimeUpWidget().updateAll(context) } }
    }
}

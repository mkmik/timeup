package pub.mkm.timeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pub.mkm.timeup.graph

/** Target of every AlarmManager PendingIntent. Verifies against current state before acting. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val g = context.graph
        when (intent.action) {
            ACTION_BUDGET -> {
                val view = g.dayView()
                val running = view.runningStatus
                if (running != null && running.remainingMs <= TOLERANCE_MS && !g.alarmGate.hasRung(running.priority.id, view.window.start)) {
                    g.alarmGate.markRung(running.priority.id, view.window.start)
                    context.startForegroundService(AlarmRingService.intent(context, AlarmRingService.ACTION_RING, running.priority.id))
                }
                g.scheduler.resync()
            }

            ACTION_WRAPUP -> {
                val running = g.dayView().runningStatus
                val minutes = running?.priority?.wrapUpMinutes
                if (running != null && minutes != null && running.remainingMs <= minutes * 60_000L + TOLERANCE_MS) {
                    g.notifier.postWrapUp(running.priority, running.remainingMs)
                }
            }

            ACTION_MIDNIGHT -> g.scheduler.resync()
        }
    }

    companion object {
        const val ACTION_BUDGET = "pub.mkm.timeup.alarm.BUDGET"
        const val ACTION_WRAPUP = "pub.mkm.timeup.alarm.WRAPUP"
        const val ACTION_MIDNIGHT = "pub.mkm.timeup.alarm.MIDNIGHT"
        private const val TOLERANCE_MS = 2_000L
    }
}

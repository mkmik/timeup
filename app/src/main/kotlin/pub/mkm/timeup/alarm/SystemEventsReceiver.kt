package pub.mkm.timeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pub.mkm.timeup.graph

/** Reboot, time zone or wall clock change, app update, exact-alarm permission change: re-plan from DB state. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.graph.scheduler.resync()
    }
}

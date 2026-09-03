package pub.mkm.timeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pub.mkm.timeup.graph

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> context.graph.actions.stop()
        }
    }

    companion object {
        const val ACTION_STOP = "pub.mkm.timeup.notification.STOP"
    }
}

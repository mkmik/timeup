package pub.mkm.timeup.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Exported, guarded by the signature permission `pub.mkm.timeup.permission.CONTROL`. */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = Commands.execute(context, intent.action, intent.extras)
        Log.i("TimeUp", "control ${intent.action}: $result")
    }
}

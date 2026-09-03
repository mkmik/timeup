package pub.mkm.timeup.control

import android.content.Context
import android.os.Bundle
import pub.mkm.timeup.domain.Priority
import pub.mkm.timeup.domain.lastUsedPriority
import pub.mkm.timeup.graph

/**
 * The automation surface: the same handlers serve the exported receiver, the headless
 * activity (shortcuts, Tasker), and the Quick Settings tile.
 *
 * Extras: `priority` (name, fuzzy) or `priority_id`; `minutes` (int or string) for LOG.
 */
object Commands {
    const val ACTION_START = "pub.mkm.timeup.action.START"
    const val ACTION_STOP = "pub.mkm.timeup.action.STOP"
    const val ACTION_TOGGLE = "pub.mkm.timeup.action.TOGGLE"
    const val ACTION_LOG = "pub.mkm.timeup.action.LOG"
    const val EXTRA_PRIORITY = "priority"
    const val EXTRA_PRIORITY_ID = "priority_id"
    const val EXTRA_MINUTES = "minutes"

    /** Runs the command and returns a one-line human-readable result. */
    fun execute(context: Context, action: String?, extras: Bundle?): String {
        val g = context.graph
        val a = g.actions
        return when (action) {
            ACTION_START -> {
                val p = resolve(context, extras) ?: return "No such priority"
                if (a.start(p.id)) "Started ${p.name}" else "Could not start ${p.name}"
            }
            ACTION_STOP -> if (a.stop()) "Stopped" else "Nothing running"
            ACTION_TOGGLE -> a.toggle()
            ACTION_LOG -> {
                val minutes = extras?.let { b ->
                    b.getString(EXTRA_MINUTES)?.trim()?.toIntOrNull() ?: b.getInt(EXTRA_MINUTES, 0).takeIf { it > 0 }
                } ?: return "Missing minutes"
                val p = resolve(context, extras) ?: return "No such priority"
                if (a.log(p.id, minutes)) "Logged ${minutes}m on ${p.name}" else "Could not log"
            }
            else -> "Unknown action"
        }
    }

    private fun resolve(context: Context, extras: Bundle?): Priority? {
        val g = context.graph
        val state = g.repo.state.value
        extras?.getString(EXTRA_PRIORITY_ID)?.let { id -> return state.priorities[id] }
        extras?.getString(EXTRA_PRIORITY)?.let { name -> return g.actions.findPriority(name) }
        return state.lastUsedPriority()
    }
}

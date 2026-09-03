package pub.mkm.timeup.alarm

import android.content.Context
import androidx.core.content.edit

/** Remembers which (priority, day) pairs already rang, so the alarm fires at most once per priority per day. */
class AlarmGate(context: Context) {
    private val prefs = context.getSharedPreferences("alarm_gate", Context.MODE_PRIVATE)

    private fun key(priorityId: String, dayStart: Long) = "$priorityId:$dayStart"

    fun hasRung(priorityId: String, dayStart: Long): Boolean = prefs.getBoolean(key(priorityId, dayStart), false)

    fun markRung(priorityId: String, dayStart: Long) = prefs.edit { putBoolean(key(priorityId, dayStart), true) }

    /** Drops entries that are not for [dayStart]. */
    fun prune(dayStart: Long) {
        val stale = prefs.all.keys.filter { !it.endsWith(":$dayStart") }
        if (stale.isNotEmpty()) prefs.edit { stale.forEach { remove(it) } }
    }
}

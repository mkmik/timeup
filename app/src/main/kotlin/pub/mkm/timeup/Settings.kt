package pub.mkm.timeup

import android.content.Context
import androidx.core.content.edit

/** Small, non-synced preferences. Everything that matters lives in the event log. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Ringtone URI for the budget alarm; null = the system default alarm sound. */
    var alarmSound: String?
        get() = prefs.getString(KEY_ALARM_SOUND, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_ALARM_SOUND) else putString(KEY_ALARM_SOUND, value) }

    var vibrate: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit { putBoolean(KEY_VIBRATE, value) }

    /** How long the alarm rings before giving up and leaving a normal notification. */
    var autoStopMinutes: Int
        get() = prefs.getInt(KEY_AUTO_STOP, 2)
        set(value) = prefs.edit { putInt(KEY_AUTO_STOP, value) }

    companion object {
        private const val KEY_ALARM_SOUND = "alarm_sound"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_AUTO_STOP = "auto_stop_minutes"
    }
}

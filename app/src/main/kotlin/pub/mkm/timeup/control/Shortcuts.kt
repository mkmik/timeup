package pub.mkm.timeup.control

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import pub.mkm.timeup.R
import pub.mkm.timeup.domain.State

/** Dynamic "Start X" launcher shortcuts, one per priority (pinnable to the home screen). */
object Shortcuts {
    fun update(context: Context, state: State) {
        val max = (ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) - 1).coerceIn(0, 4)
        val list = state.activePriorities.take(max).map { p ->
            ShortcutInfoCompat.Builder(context, "start:${p.id}")
                .setShortLabel("Start ${p.name}")
                .setLongLabel("Start ${p.name}")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_stat))
                .setIntent(
                    Intent(Commands.ACTION_START)
                        .setClass(context, ActionActivity::class.java)
                        .putExtra(Commands.EXTRA_PRIORITY_ID, p.id),
                )
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }
}

package pub.mkm.timeup.control

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import pub.mkm.timeup.R
import pub.mkm.timeup.domain.formatHm
import pub.mkm.timeup.domain.lastUsedPriority
import pub.mkm.timeup.graph

/** Quick Settings tile with the Action Button semantics: stop if running, else start the last priority. */
class ToggleTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        graph.actions.toggle()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val view = graph.dayView()
        val running = view.runningStatus
        if (running != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = running.priority.name
            tile.subtitle = formatHm(running.remainingMs)
        } else {
            tile.state = if (view.state.activePriorities.isEmpty()) Tile.STATE_UNAVAILABLE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_label)
            tile.subtitle = view.state.lastUsedPriority()?.let { "Start ${it.name}" } ?: "No priorities"
        }
        tile.updateTile()
    }
}

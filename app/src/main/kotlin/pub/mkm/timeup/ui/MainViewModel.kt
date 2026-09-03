package pub.mkm.timeup.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pub.mkm.timeup.AppGraph
import pub.mkm.timeup.domain.DayView
import pub.mkm.timeup.domain.PriorityStatus
import pub.mkm.timeup.domain.State
import pub.mkm.timeup.export.Exporter

data class OverviewUi(
    val statuses: List<PriorityStatus>,
    val hoursStillYoursMs: Long,
    val running: PriorityStatus?,
) {
    companion object {
        fun from(v: DayView) = OverviewUi(v.statuses, v.hoursStillYoursMs, v.runningStatus)
    }
}

class MainViewModel(private val graph: AppGraph) : ViewModel() {
    private val tick = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val state: StateFlow<State> get() = graph.repo.state

    /** Recomputed every second while something is on screen; nothing is ever counted. */
    val overview: StateFlow<OverviewUi> = combine(graph.repo.state, tick) { s, now -> OverviewUi.from(graph.dayView(s, now)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUi.from(graph.dayView()))

    fun start(priorityId: String) = io { graph.actions.start(priorityId) }
    fun stop() = io { graph.actions.stop() }
    fun toggle(priorityId: String) = io {
        if (graph.repo.state.value.openSession?.priorityId == priorityId) graph.actions.stop() else graph.actions.start(priorityId)
    }

    fun log(priorityId: String, minutes: Int, endAt: Long) = io { graph.actions.log(priorityId, minutes, endAt) }

    fun createPriority(name: String, color: Int, budgetSeconds: Long, wrapUp: Int?) =
        io { graph.actions.createPriority(name, color, budgetSeconds, wrapUp) }

    fun updatePriority(id: String, name: String, color: Int, budgetSeconds: Long, wrapUp: Int?) =
        io { graph.actions.updatePriority(id, name, color, budgetSeconds, wrapUp) }

    fun archive(id: String) = io { graph.actions.archive(id) }

    fun deleteEverything() = io {
        graph.repo.deleteEverything()
        graph.scheduler.resync()
    }

    fun export(onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val intent = withContext(Dispatchers.IO) { Exporter.export(graph.context) }
            onReady(intent)
        }
    }

    private fun io(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }
}

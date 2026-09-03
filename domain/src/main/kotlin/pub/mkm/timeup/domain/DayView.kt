package pub.mkm.timeup.domain

import kotlin.math.max

data class PriorityStatus(
    val priority: Priority,
    val spentMs: Long,
    /** Negative when over budget (invariant 5). */
    val remainingMs: Long,
    val running: Boolean,
) {
    val overBudget: Boolean get() = remainingMs < 0
}

/**
 * Everything the UI, the alarm scheduler and the widgets need for one day, derived
 * from [state] and the clock. Nothing is counted; it is all recomputed from timestamps.
 */
class DayView(val state: State, val window: DayWindow, val now: Long) {
    val openSession: Session? = state.openSession

    /** The running priority, even if it has since been archived. */
    val runningPriority: Priority? = openSession?.let { state.priorities[it.priorityId] }

    fun spentMs(priorityId: String): Long =
        state.sessions.values.asSequence()
            .filter { it.priorityId == priorityId }
            .sumOf { it.overlapMs(window, now) }

    fun remainingMs(p: Priority): Long = p.budgetMs - spentMs(p.id)

    val statuses: List<PriorityStatus> = state.activePriorities.map {
        val spent = spentMs(it.id)
        PriorityStatus(it, spent, it.budgetMs - spent, openSession?.priorityId == it.id)
    }

    val runningStatus: PriorityStatus? = runningPriority?.let { p ->
        statuses.firstOrNull { it.priority.id == p.id } ?: PriorityStatus(p, spentMs(p.id), remainingMs(p), true)
    }

    /** Invariant 6: sum of `max(remaining, 0)` over non-archived priorities. */
    val hoursStillYoursMs: Long = statuses.sumOf { max(0L, it.remainingMs) }

    /** When the running priority's budget is (or was) spent; null when nothing runs. May be in the past. */
    val budgetEndsAt: Long? = runningPriority?.let { now + remainingMs(it) }

    /** When the wrap-up notification for the running priority is due; null if none. */
    val wrapUpAt: Long? = runningPriority?.wrapUpMinutes?.let { m -> budgetEndsAt!! - m * 60_000L }
}

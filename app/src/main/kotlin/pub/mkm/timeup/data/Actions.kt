package pub.mkm.timeup.data

import android.os.SystemClock
import pub.mkm.timeup.alarm.Scheduler
import pub.mkm.timeup.domain.Payload
import pub.mkm.timeup.domain.Priority
import pub.mkm.timeup.domain.PriorityArchived
import pub.mkm.timeup.domain.PriorityCreated
import pub.mkm.timeup.domain.PriorityUpdated
import pub.mkm.timeup.domain.SessionDeleted
import pub.mkm.timeup.domain.SessionStarted
import pub.mkm.timeup.domain.SessionStopped
import pub.mkm.timeup.domain.State
import pub.mkm.timeup.domain.TimeLogged
import pub.mkm.timeup.domain.Ulid
import pub.mkm.timeup.domain.lastUsedPriority
import java.time.ZoneId

/** The six user actions plus priority editing. Every one appends events and then resyncs alarms and surfaces. */
class Actions(private val repo: Repository, private val scheduler: Scheduler) {
    private val state: State get() = repo.state.value

    fun start(priorityId: String): Boolean {
        val p = state.priorities[priorityId] ?: return false
        if (p.isArchived) return false
        if (state.openSession?.priorityId == priorityId) return true
        val now = now()
        append(SessionStarted(Ulid.next(now), priorityId, now, SystemClock.elapsedRealtime()))
        return true
    }

    fun stop(): Boolean {
        val open = state.openSession ?: return false
        append(SessionStopped(open.id, now()))
        return true
    }

    /** Stop if running, else start the last-used priority (the Action Button semantics). */
    fun toggle(): String {
        if (state.openSession != null) {
            val name = state.priorities[state.openSession!!.priorityId]?.name ?: "session"
            stop()
            return "Stopped $name"
        }
        val p = state.lastUsedPriority() ?: return "No priorities yet"
        start(p.id)
        return "Started ${p.name}"
    }

    fun log(priorityId: String, minutes: Int, endAt: Long = now()): Boolean {
        if (minutes <= 0 || state.priorities[priorityId]?.isArchived != false) return false
        append(TimeLogged(Ulid.next(endAt), priorityId, minutes, endAt))
        return true
    }

    fun deleteSession(sessionId: String) {
        if (state.sessions.containsKey(sessionId)) append(SessionDeleted(sessionId))
    }

    fun createPriority(name: String, color: Int, budgetSeconds: Long, wrapUpMinutes: Int?): String {
        val id = Ulid.next(now())
        val order = (state.priorities.values.maxOfOrNull { it.sortOrder } ?: -1) + 1
        append(PriorityCreated(id, name.trim(), color, budgetSeconds, wrapUpMinutes, order))
        return id
    }

    fun updatePriority(id: String, name: String, color: Int, budgetSeconds: Long, wrapUpMinutes: Int?) {
        val cur = state.priorities[id] ?: return
        val upd = PriorityUpdated(
            priorityId = id,
            name = name.trim().takeIf { it != cur.name },
            color = color.takeIf { it != cur.color },
            budgetSeconds = budgetSeconds.takeIf { it != cur.budgetSeconds },
            wrapUpMinutes = wrapUpMinutes?.takeIf { it != cur.wrapUpMinutes },
            clearWrapUp = wrapUpMinutes == null && cur.wrapUpMinutes != null,
        )
        if (upd == PriorityUpdated(priorityId = id)) return
        append(upd)
    }

    fun archive(id: String) {
        if (state.priorities[id]?.isArchived == false) append(PriorityArchived(id))
    }

    /** Fuzzy name match for voice / automation: exact, then prefix, then substring, case-insensitive. */
    fun findPriority(query: String): Priority? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val active = state.activePriorities
        return active.firstOrNull { it.name.lowercase() == q }
            ?: active.firstOrNull { it.name.lowercase().startsWith(q) }
            ?: active.firstOrNull { it.name.lowercase().contains(q) }
    }

    private fun now() = System.currentTimeMillis()

    private fun append(vararg payloads: Payload) {
        repo.append(payloads.toList(), now(), ZoneId.systemDefault())
        scheduler.resync()
    }
}

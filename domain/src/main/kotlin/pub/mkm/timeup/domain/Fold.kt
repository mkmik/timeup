package pub.mkm.timeup.domain

/** Materialised view of the event log. Immutable; [apply] returns a new state. */
data class State(
    val priorities: Map<String, Priority> = emptyMap(),
    val sessions: Map<String, Session> = emptyMap(),
) {
    val openSession: Session? get() = sessions.values.firstOrNull { it.isOpen }

    val activePriorities: List<Priority>
        get() = priorities.values.filter { !it.isArchived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    fun apply(e: Event): State = when (val p = e.payload) {
        is PriorityCreated -> {
            if (priorities.containsKey(p.priorityId)) this
            else copy(
                priorities = priorities + (p.priorityId to Priority(
                    id = p.priorityId,
                    name = p.name,
                    color = p.color,
                    budgetSeconds = p.budgetSeconds,
                    wrapUpMinutes = p.wrapUpMinutes,
                    sortOrder = p.sortOrder,
                )),
            )
        }

        is PriorityUpdated -> {
            val cur = priorities[p.priorityId] ?: return this
            copy(
                priorities = priorities + (cur.id to cur.copy(
                    name = p.name ?: cur.name,
                    color = p.color ?: cur.color,
                    budgetSeconds = p.budgetSeconds ?: cur.budgetSeconds,
                    wrapUpMinutes = if (p.clearWrapUp) null else (p.wrapUpMinutes ?: cur.wrapUpMinutes),
                    sortOrder = p.sortOrder ?: cur.sortOrder,
                )),
            )
        }

        is PriorityArchived -> {
            val cur = priorities[p.priorityId] ?: return this
            // Archiving also stops a session running on that priority.
            val closed = closeOpenSessions(sessions, e.ts) { it.priorityId == cur.id }
            copy(priorities = priorities + (cur.id to cur.copy(archivedAt = cur.archivedAt ?: e.ts)), sessions = closed)
        }

        is SessionStarted -> {
            if (sessions.containsKey(p.sessionId) || !priorities.containsKey(p.priorityId)) return this
            val closed = closeOpenSessions(sessions, p.at) { true }
            copy(
                sessions = closed + (p.sessionId to Session(
                    id = p.sessionId,
                    priorityId = p.priorityId,
                    startedAt = p.at,
                    endedAt = null,
                    kind = SessionKind.LIVE,
                    deviceId = e.deviceId,
                    tz = e.tz,
                )),
            )
        }

        is SessionStopped -> {
            val cur = sessions[p.sessionId] ?: return this
            if (!cur.isOpen) this else copy(sessions = closeOne(sessions, cur, p.at))
        }

        is TimeLogged -> {
            if (sessions.containsKey(p.sessionId) || !priorities.containsKey(p.priorityId) || p.minutes <= 0) return this
            copy(
                sessions = sessions + (p.sessionId to Session(
                    id = p.sessionId,
                    priorityId = p.priorityId,
                    startedAt = p.at - p.minutes * 60_000L,
                    endedAt = p.at,
                    kind = SessionKind.LOGGED,
                    deviceId = e.deviceId,
                    tz = e.tz,
                )),
            )
        }

        is SessionDeleted -> if (sessions.containsKey(p.sessionId)) copy(sessions = sessions - p.sessionId) else this
    }

    private fun closeOpenSessions(map: Map<String, Session>, at: Long, filter: (Session) -> Boolean): Map<String, Session> {
        var out = map
        for (s in map.values) if (s.isOpen && filter(s)) out = closeOne(out, s, at)
        return out
    }

    /** Closes [s] at [at]; zero- or negative-length sessions are dropped (invariant 2). */
    private fun closeOne(map: Map<String, Session>, s: Session, at: Long): Map<String, Session> =
        if (at <= s.startedAt) map - s.id else map + (s.id to s.copy(endedAt = at))
}

/** Folds events in `(ts, id)` order. Order of the input does not matter (that is what makes sync a union). */
fun fold(events: Iterable<Event>): State =
    events.sortedWith(eventOrder).fold(State()) { s, e -> s.apply(e) }

/** The priority to start for "toggle" / "start" without arguments: the most recently used active one. */
fun State.lastUsedPriority(): Priority? {
    val byRecency = sessions.values.sortedByDescending { it.startedAt }
    for (s in byRecency) {
        val p = priorities[s.priorityId]
        if (p != null && !p.isArchived) return p
    }
    return activePriorities.firstOrNull()
}

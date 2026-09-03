package pub.mkm.timeup.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pub.mkm.timeup.BuildConfig
import pub.mkm.timeup.db.Database
import pub.mkm.timeup.domain.Event
import pub.mkm.timeup.domain.EventCodec
import pub.mkm.timeup.domain.Payload
import pub.mkm.timeup.domain.Priority
import pub.mkm.timeup.domain.Session
import pub.mkm.timeup.domain.SessionKind
import pub.mkm.timeup.domain.State
import pub.mkm.timeup.domain.Ulid
import pub.mkm.timeup.domain.fold
import java.time.ZoneId

/**
 * Append-only event log on SQLite plus the folded [State] in memory.
 * All methods are synchronous and thread-safe; callers pick the thread.
 */
class Repository(private val database: Database) {
    private val q get() = database.queries
    private val lock = Any()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    val deviceId: String by lazy { database.deviceId() }

    /** Reads every event, refolds, and rebuilds the materialised tables from scratch. */
    fun load() = synchronized(lock) {
        val events = q.selectEvents().executeAsList().mapNotNull { row ->
            EventCodec.decode(row.type, row.payload)?.let { Event(row.id, row.device_id, row.ts, row.tz, it) }
        }
        val s = fold(events)
        database.db.transaction {
            q.deleteAllPriorities()
            q.deleteAllSessions()
            s.priorities.values.forEach(::writePriority)
            s.sessions.values.forEach(::writeSession)
            database.setMeta("schema_version", "1")
            database.setMeta("app_version", BuildConfig.VERSION_NAME)
            database.setMeta("README", README)
        }
        _state.value = s
    }

    /** Appends [payloads] as events stamped with now / the current zone, applies them, and returns the new state. */
    fun append(payloads: List<Payload>, now: Long, zone: ZoneId): State = synchronized(lock) {
        val old = _state.value
        var s = old
        database.db.transaction {
            for (p in payloads) {
                val e = Event(id = Ulid.next(now), deviceId = deviceId, ts = now, tz = zone.id, payload = p)
                q.insertEvent(e.id, e.deviceId, e.ts, e.tz, e.type, EventCodec.encode(p))
                s = s.apply(e)
            }
            materialize(old, s)
        }
        _state.value = s
        s
    }

    fun deleteEverything() = synchronized(lock) {
        database.db.transaction {
            q.deleteEvents()
            q.deleteAllPriorities()
            q.deleteAllSessions()
        }
        _state.value = State()
    }

    fun setMeta(key: String, value: String) = synchronized(lock) { database.setMeta(key, value) }

    private fun materialize(old: State, new: State) {
        for ((id, p) in new.priorities) if (old.priorities[id] !== p) writePriority(p)
        for (id in old.priorities.keys) if (id !in new.priorities) q.deletePriority(id)
        for ((id, s) in new.sessions) if (old.sessions[id] !== s) writeSession(s)
        for (id in old.sessions.keys) if (id !in new.sessions) q.deleteSession(id)
    }

    private fun writePriority(p: Priority) = q.upsertPriority(
        p.id, p.name, p.color.toLong(), p.budgetSeconds, p.wrapUpMinutes?.toLong(), p.sortOrder.toLong(), p.archivedAt,
    )

    private fun writeSession(s: Session) = q.upsertSession(
        s.id, s.priorityId, s.startedAt, s.endedAt, s.kind.dbValue, s.deviceId, s.tz,
    )

    companion object {
        val README = """
            TimeUp export. `events` is the source of truth (append-only; fold in (ts, id) order).
            `priorities` and `sessions` are materialised from it. Timestamps are epoch milliseconds UTC;
            `tz` is the IANA zone in effect at the time.
            Spent time for a priority on a local day D = sum over its sessions of
            max(0, min(ended_at or now, end_of_D) - max(started_at, start_of_D)). Remaining = budget_seconds*1000 - spent.
            A session that crosses midnight is not split; the overlap rule above splits it. `kind` is 'live' or 'logged'.
        """.trimIndent()
    }
}

package pub.mkm.timeup.domain

/** A priority ("role") with a daily time budget. */
data class Priority(
    val id: String,
    val name: String,
    /** ARGB colour. */
    val color: Int,
    val budgetSeconds: Long,
    /** Minutes before the budget runs out at which a wrap-up notification is posted. Null = off, 0 = at the moment the budget ends. */
    val wrapUpMinutes: Int?,
    val sortOrder: Int,
    val archivedAt: Long? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
    val budgetMs: Long get() = budgetSeconds * 1000L
}

enum class SessionKind(val dbValue: String) {
    LIVE("live"),
    LOGGED("logged");

    companion object {
        fun fromDb(value: String): SessionKind = entries.first { it.dbValue == value }
    }
}

/** A stretch of time spent on a priority. Open while [endedAt] is null. */
data class Session(
    val id: String,
    val priorityId: String,
    /** Epoch millis, UTC. */
    val startedAt: Long,
    val endedAt: Long?,
    val kind: SessionKind,
    val deviceId: String,
    /** IANA zone id in effect when the session was started. */
    val tz: String,
) {
    val isOpen: Boolean get() = endedAt == null
}

/** A calendar day as an interval `[start, end)` in epoch millis. */
data class DayWindow(val start: Long, val end: Long) {
    init {
        require(end > start) { "empty day window" }
    }
}

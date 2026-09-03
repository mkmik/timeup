package pub.mkm.timeup.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

object Days {
    /** The local calendar day containing [nowMs] in [zone], as `[midnight, next midnight)`. */
    fun window(nowMs: Long, zone: ZoneId): DayWindow {
        val date = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return DayWindow(start, end)
    }

    fun nextMidnight(nowMs: Long, zone: ZoneId): Long = window(nowMs, zone).end
}

/**
 * Invariant 4: the part of `[startedAt, endedAt ?: now)` that falls inside [window].
 * A session crossing midnight is not split in storage; this rule does the accounting.
 */
fun Session.overlapMs(window: DayWindow, now: Long): Long {
    val end = endedAt ?: now
    val s = max(startedAt, window.start)
    val e = min(end, window.end)
    return max(0L, e - s)
}

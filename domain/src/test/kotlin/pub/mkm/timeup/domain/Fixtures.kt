package pub.mkm.timeup.domain

import java.time.LocalDateTime
import java.time.ZoneId

val ROME: ZoneId = ZoneId.of("Europe/Rome")
val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long =
    LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

/** 2026-09-03 at the given local time in Rome. */
fun rome(h: Int, mi: Int, s: Int = 0, day: Int = 3): Long = at(ROME, 2026, 9, day, h, mi, s)

fun ev(ts: Long, payload: Payload, device: String = "A", tz: String = "Europe/Rome", id: String = Ulid.next(ts)): Event =
    Event(id = id, deviceId = device, ts = ts, tz = tz, payload = payload)

fun created(id: String, name: String = id, budgetSeconds: Long = 3600, wrapUp: Int? = null, order: Int = 0) =
    PriorityCreated(priorityId = id, name = name, color = 0xFF1E88E5.toInt(), budgetSeconds = budgetSeconds, wrapUpMinutes = wrapUp, sortOrder = order)

package pub.mkm.timeup.domain

import kotlin.math.abs

/** `1h 20m`, `45m`, `−12m` (over budget). */
fun formatHm(ms: Long): String {
    val neg = ms < 0
    val totalMin = abs(ms) / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    val body = if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
    return if (neg) "−$body" else body
}

/** `1h 19m 59s` — for the row that ticks every second. */
fun formatHms(ms: Long): String {
    val neg = ms < 0
    val totalSec = abs(ms) / 1000L
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val body = if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m ${s.toString().padStart(2, '0')}s"
    else "${m}m ${s.toString().padStart(2, '0')}s"
    return if (neg) "−$body" else body
}

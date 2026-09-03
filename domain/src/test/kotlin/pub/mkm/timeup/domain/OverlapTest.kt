package pub.mkm.timeup.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OverlapTest {
    @Test
    fun `session 23_30 to 00_30 gives 30 minutes to each day`() {
        val s = Session("s", "p", rome(23, 30), rome(0, 30, day = 4), SessionKind.LIVE, "A", "Europe/Rome")
        val day3 = Days.window(rome(12, 0), ROME)
        val day4 = Days.window(rome(12, 0, day = 4), ROME)
        assertEquals(30 * 60_000L, s.overlapMs(day3, now = rome(12, 0, day = 4)))
        assertEquals(30 * 60_000L, s.overlapMs(day4, now = rome(12, 0, day = 4)))
    }

    @Test
    fun `open session counts up to now and never before its start`() {
        val s = Session("s", "p", rome(10, 0), null, SessionKind.LIVE, "A", "Europe/Rome")
        val day = Days.window(rome(12, 0), ROME)
        assertEquals(2 * 3600_000L, s.overlapMs(day, now = rome(12, 0)))
        assertEquals(0L, s.overlapMs(day, now = rome(9, 0)))
    }

    @Test
    fun `day window follows the zone`() {
        val w = Days.window(rome(12, 0), ROME)
        assertEquals(rome(0, 0), w.start)
        assertEquals(rome(0, 0, day = 4), w.end)
        assertEquals(w.end, Days.nextMidnight(rome(12, 0), ROME))
    }
}

package pub.mkm.timeup.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmTest {
    private val base = listOf(
        ev(rome(8, 0), created("P1", budgetSeconds = 3600, wrapUp = 5)),
        ev(rome(9, 0), TimeLogged("l1", "P1", 45, rome(9, 0))),
        ev(rome(12, 0), SessionStarted("s1", "P1", rome(12, 0))),
    )

    private fun view(events: List<Event>, now: Long) = DayView(fold(events), Days.window(now, ROME), now)

    @Test
    fun `budget 60 with 45 logged and a session from 12_00 rings at 12_15`() {
        val v = view(base, now = rome(12, 0))
        assertEquals(rome(12, 15), v.budgetEndsAt)
        assertEquals(rome(12, 10), v.wrapUpAt)
        assertEquals(15 * 60_000L, v.runningStatus!!.remainingMs)
    }

    @Test
    fun `editing the budget down mid session makes the alarm due immediately`() {
        val now = rome(12, 5)
        val v = view(base + ev(now, PriorityUpdated("P1", budgetSeconds = 1800)), now)
        assertNotNull(v.budgetEndsAt)
        assertTrue(v.budgetEndsAt!! <= now, "alarm must be in the past or now")
        assertEquals(-20 * 60_000L, v.runningStatus!!.remainingMs)
        assertTrue(v.runningStatus!!.overBudget)
    }

    @Test
    fun `nothing running means no alarm`() {
        val v = view(base + ev(rome(12, 10), SessionStopped("s1", rome(12, 10))), rome(12, 11))
        assertNull(v.budgetEndsAt)
        assertNull(v.wrapUpAt)
        assertEquals(5 * 60_000L, v.hoursStillYoursMs)
    }

    @Test
    fun `hours still yours clamps over budget priorities at zero`() {
        val events = base + ev(rome(8, 0), created("P2", budgetSeconds = 7200, order = 1))
        val v = view(events, now = rome(13, 0)) // P1 is 45 min over
        assertEquals(2 * 3600_000L, v.hoursStillYoursMs)
    }

    @Test
    fun `time zone change recomputes today without losing the session`() {
        // Session started 19:00 Rome; at 20:00 Rome the phone lands in New York (14:00 local).
        val events = listOf(
            ev(rome(8, 0), created("P1", budgetSeconds = 2 * 3600)),
            ev(rome(19, 0), SessionStarted("s1", "P1", rome(19, 0))),
        )
        val now = rome(20, 0)
        val before = DayView(fold(events), Days.window(now, ROME), now)
        val after = DayView(fold(events), Days.window(now, NEW_YORK), now)
        assertEquals(at(NEW_YORK, 2026, 9, 3, 0, 0), after.window.start)
        assertEquals("s1", after.openSession?.id)
        assertEquals(3600_000L, after.spentMs("P1"))
        assertEquals(before.budgetEndsAt, after.budgetEndsAt)
        assertEquals(now + 3600_000L, after.budgetEndsAt)
    }

    @Test
    fun `midnight rollover keeps the session running and resets the accounting`() {
        val events = listOf(
            ev(rome(8, 0), created("P1", budgetSeconds = 3600)),
            ev(rome(23, 30), SessionStarted("s1", "P1", rome(23, 30))),
        )
        val now = rome(0, 10, day = 4)
        val v = DayView(fold(events), Days.window(now, ROME), now)
        assertEquals("s1", v.openSession?.id)
        assertEquals(10 * 60_000L, v.spentMs("P1"))
        assertEquals(now + 50 * 60_000L, v.budgetEndsAt)
    }
}

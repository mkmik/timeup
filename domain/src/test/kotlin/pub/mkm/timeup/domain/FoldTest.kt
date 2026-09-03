package pub.mkm.timeup.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoldTest {
    @Test
    fun `merge of two disconnected starts closes the earlier one at the later start`() {
        val base = ev(rome(9, 0), created("P1"))
        val base2 = ev(rome(9, 0), created("P2"))
        val a = ev(rome(10, 0), SessionStarted("s1", "P1", rome(10, 0)), device = "A")
        val b = ev(rome(10, 5), SessionStarted("s2", "P2", rome(10, 5)), device = "B")

        for (order in listOf(listOf(base, base2, a, b), listOf(b, a, base2, base), listOf(a, base, b, base2))) {
            val s = fold(order)
            assertEquals(rome(10, 5), s.sessions.getValue("s1").endedAt)
            assertNull(s.sessions.getValue("s2").endedAt)
            assertEquals("s2", s.openSession?.id)
        }
    }

    @Test
    fun `starting one pauses the running one`() {
        val s = fold(
            listOf(
                ev(rome(9, 0), created("P1")),
                ev(rome(9, 0), created("P2")),
                ev(rome(10, 0), SessionStarted("s1", "P1", rome(10, 0))),
                ev(rome(11, 0), SessionStarted("s2", "P2", rome(11, 0))),
            ),
        )
        assertEquals(rome(11, 0), s.sessions.getValue("s1").endedAt)
        assertTrue(s.sessions.getValue("s2").isOpen)
        assertEquals(1, s.sessions.values.count { it.isOpen })
    }

    @Test
    fun `zero length sessions are dropped`() {
        val s = fold(
            listOf(
                ev(rome(9, 0), created("P1")),
                ev(rome(10, 0), SessionStarted("s1", "P1", rome(10, 0))),
                ev(rome(10, 0), SessionStopped("s1", rome(10, 0))),
            ),
        )
        assertTrue(s.sessions.isEmpty())
    }

    @Test
    fun `logged time creates a closed session ending at the log time and can be deleted`() {
        val base = listOf(ev(rome(9, 0), created("P1")), ev(rome(9, 0), TimeLogged("l1", "P1", 45, rome(9, 0))))
        val s = fold(base)
        val l = s.sessions.getValue("l1")
        assertEquals(SessionKind.LOGGED, l.kind)
        assertEquals(rome(8, 15), l.startedAt)
        assertEquals(rome(9, 0), l.endedAt)
        val s2 = fold(base + ev(rome(9, 1), SessionDeleted("l1")))
        assertTrue(s2.sessions.isEmpty())
    }

    @Test
    fun `archiving hides the priority, keeps history and stops its session`() {
        val s = fold(
            listOf(
                ev(rome(9, 0), created("P1")),
                ev(rome(10, 0), SessionStarted("s1", "P1", rome(10, 0))),
                ev(rome(10, 30), PriorityArchived("P1")),
            ),
        )
        assertTrue(s.priorities.getValue("P1").isArchived)
        assertTrue(s.activePriorities.isEmpty())
        assertEquals(rome(10, 30), s.sessions.getValue("s1").endedAt)
        assertNull(s.openSession)
    }

    @Test
    fun `update is last writer wins per field`() {
        val s = fold(
            listOf(
                ev(rome(9, 0), created("P1", name = "Deep Work", budgetSeconds = 3600, wrapUp = 5)),
                ev(rome(9, 1), PriorityUpdated("P1", name = "Focus")),
                ev(rome(9, 2), PriorityUpdated("P1", budgetSeconds = 1800)),
                ev(rome(9, 3), PriorityUpdated("P1", clearWrapUp = true)),
            ),
        )
        val p = s.priorities.getValue("P1")
        assertEquals("Focus", p.name)
        assertEquals(1800L, p.budgetSeconds)
        assertNull(p.wrapUpMinutes)
    }

    @Test
    fun `last used priority prefers the most recent session`() {
        val s = fold(
            listOf(
                ev(rome(9, 0), created("P1", order = 0)),
                ev(rome(9, 0), created("P2", order = 1)),
                ev(rome(10, 0), SessionStarted("s1", "P2", rome(10, 0))),
                ev(rome(10, 5), SessionStopped("s1", rome(10, 5))),
            ),
        )
        assertEquals("P2", s.lastUsedPriority()?.id)
        assertNotNull(fold(listOf(ev(rome(9, 0), created("P1")))).lastUsedPriority())
    }
}

package pub.mkm.timeup.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodecTest {
    @Test
    fun `every payload round trips`() {
        val payloads = listOf(
            PriorityCreated("p", "Deep Work", 0xFFE53935.toInt(), 3600, 5, 0),
            PriorityUpdated("p", name = "Focus", clearWrapUp = true),
            PriorityArchived("p"),
            SessionStarted("s", "p", 1_000, elapsedRealtime = 42),
            SessionStopped("s", 2_000),
            TimeLogged("l", "p", 30, 3_000),
            SessionDeleted("l"),
        )
        for (p in payloads) {
            assertEquals(p, EventCodec.decode(p.type, EventCodec.encode(p)))
        }
    }

    @Test
    fun `unknown types decode to null`() {
        assertNull(EventCodec.decode("SomethingNew", "{}"))
    }

    @Test
    fun `ulids sort by time and are unique`() {
        val a = Ulid.next(1_000)
        val b = Ulid.next(1_000)
        val c = Ulid.next(2_000)
        assertEquals(26, a.length)
        assertTrue(a < b)
        assertTrue(b < c)
    }
}

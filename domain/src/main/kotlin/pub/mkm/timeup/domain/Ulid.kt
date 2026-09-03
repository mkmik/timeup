package pub.mkm.timeup.domain

import java.math.BigInteger
import java.security.SecureRandom

/** ULID generator: 48-bit timestamp + 80-bit randomness, Crockford base32, monotonic within one millisecond. */
object Ulid {
    private const val ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val random = SecureRandom()
    private var lastTime = -1L
    private val lastRandom = ByteArray(10)

    @Synchronized
    fun next(now: Long = System.currentTimeMillis()): String {
        if (now == lastTime) {
            increment(lastRandom)
        } else {
            lastTime = now
            random.nextBytes(lastRandom)
        }
        return encode(now, lastRandom)
    }

    private fun increment(b: ByteArray) {
        for (i in b.indices.reversed()) {
            val v = (b[i].toInt() and 0xFF) + 1
            b[i] = v.toByte()
            if (v <= 0xFF) return
        }
    }

    private fun encode(time: Long, rnd: ByteArray): String {
        val out = CharArray(26)
        var t = time
        for (i in 9 downTo 0) {
            out[i] = ENCODING[(t and 0x1F).toInt()]
            t = t ushr 5
        }
        var acc = BigInteger(1, rnd)
        val mask = BigInteger.valueOf(31)
        for (i in 25 downTo 10) {
            out[i] = ENCODING[acc.and(mask).toInt()]
            acc = acc.shiftRight(5)
        }
        return String(out)
    }
}

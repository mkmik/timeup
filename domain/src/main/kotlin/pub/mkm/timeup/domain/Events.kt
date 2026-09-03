package pub.mkm.timeup.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Every change to the model is one of these. The UI state is `fold(events)`. */
sealed interface Payload {
    val type: String
}

@Serializable
data class PriorityCreated(
    val priorityId: String,
    val name: String,
    val color: Int,
    val budgetSeconds: Long,
    val wrapUpMinutes: Int? = null,
    val sortOrder: Int,
) : Payload {
    override val type: String get() = TYPE
    companion object { const val TYPE = "PriorityCreated" }
}

/** Last writer wins per field. Null fields are unchanged; [clearWrapUp] removes the wrap-up. */
@Serializable
data class PriorityUpdated(
    val priorityId: String,
    val name: String? = null,
    val color: Int? = null,
    val budgetSeconds: Long? = null,
    val wrapUpMinutes: Int? = null,
    val clearWrapUp: Boolean = false,
    val sortOrder: Int? = null,
) : Payload {
    override val type: String get() = TYPE
    companion object { const val TYPE = "PriorityUpdated" }
}

@Serializable
data class PriorityArchived(val priorityId: String) : Payload {
    override val type: String get() = TYPE
    companion object { const val TYPE = "PriorityArchived" }
}

/** Fold: every open session is closed at [at] (chess clock rule), then this one opens. */
@Serializable
data class SessionStarted(
    val sessionId: String,
    val priorityId: String,
    val at: Long,
    /** `SystemClock.elapsedRealtime()` on the device at [at]; lets a wall-clock jump be detected. */
    val elapsedRealtime: Long? = null,
) : Payload {
    override val type: String get() = TYPE
    companion object { const val TYPE = "SessionStarted" }
}

@Serializable
data class SessionStopped(val sessionId: String, val at: Long) : Payload {
    override val type: String get() = TYPE
    companion object { const val TYPE = "SessionStopped" }
}

/** Fold: a closed session of kind `logged` covering `[at - minutes, at]`. */
@Serializable
data class TimeLogged(
    val sessionId: String,
    val priorityId: String,
    val minutes: Int,
    val at: Long,
) : Payload {
    override val type: String get() = TYPE
    companion object { const val TYPE = "TimeLogged" }
}

@Serializable
data class SessionDeleted(val sessionId: String) : Payload {
    override val type: String get() = TYPE
    companion object { const val TYPE = "SessionDeleted" }
}

/** Event envelope, as stored in the `events` table. */
data class Event(
    /** ULID: unique and sortable by creation time. */
    val id: String,
    val deviceId: String,
    /** Epoch millis, UTC. */
    val ts: Long,
    /** IANA zone id at the time. */
    val tz: String,
    val payload: Payload,
) {
    val type: String get() = payload.type
}

val eventOrder: Comparator<Event> = compareBy<Event>({ it.ts }, { it.id })

/** Encodes payloads as JSON; the type lives in its own column. */
object EventCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Suppress("UNCHECKED_CAST")
    private fun serializerFor(type: String): KSerializer<Payload>? = when (type) {
        PriorityCreated.TYPE -> PriorityCreated.serializer()
        PriorityUpdated.TYPE -> PriorityUpdated.serializer()
        PriorityArchived.TYPE -> PriorityArchived.serializer()
        SessionStarted.TYPE -> SessionStarted.serializer()
        SessionStopped.TYPE -> SessionStopped.serializer()
        TimeLogged.TYPE -> TimeLogged.serializer()
        SessionDeleted.TYPE -> SessionDeleted.serializer()
        else -> null
    } as KSerializer<Payload>?

    fun encode(payload: Payload): String =
        json.encodeToString(serializerFor(payload.type)!!, payload)

    /** Returns null for unknown types so a newer app's events are skipped, not fatal. */
    fun decode(type: String, payload: String): Payload? =
        serializerFor(type)?.let { json.decodeFromString(it, payload) }
}

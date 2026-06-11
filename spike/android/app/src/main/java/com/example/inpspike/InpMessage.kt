package com.example.inpspike

import android.util.Log
import org.json.JSONException
import org.json.JSONObject

/**
 * DISPOSABLE Phase-0 spike (OC-5).
 *
 * The INP envelope (spec §2.2):
 *
 * ```json
 * { "inp": 1, "id": "uuid-v4", "replyTo": "uuid-v4|null", "type": "...", "payload": { } }
 * ```
 *
 * This is the smallest faithful representation. The production Android library
 * (N7.2) generates/handwrites Codable-style models conformance-tested against the
 * schemas (P1.2). Here we lean on org.json so the probe has zero codec deps.
 *
 * Hard rule (spec §8/§9): malformed inbound is DROPPED + logged, never crashes.
 */
data class InpMessage(
    val inp: Int,
    val id: String,
    val replyTo: String?,
    val type: String,
    val payload: JSONObject,
) {
    fun toJson(): String =
        JSONObject().apply {
            put("inp", inp)
            put("id", id)
            // org.json renders a Kotlin null as JSONObject.NULL when using put(String, null) overload,
            // but to be explicit and match the wire shape we set NULL deliberately.
            put("replyTo", replyTo ?: JSONObject.NULL)
            put("type", type)
            put("payload", payload)
        }.toString()

    companion object {
        const val PROTOCOL_MAJOR = 1
        private const val TAG = "InpMessage"

        /** Build an outbound envelope with a fresh uuid. */
        fun outbound(type: String, payload: JSONObject = JSONObject(), replyTo: String? = null): InpMessage =
            InpMessage(
                inp = PROTOCOL_MAJOR,
                id = randomUuid(),
                replyTo = replyTo,
                type = type,
                payload = payload,
            )

        /**
         * Parse + validate an inbound JSON string from the WebView.
         *
         * Returns null (and logs) for anything malformed or for a mismatched
         * protocol major — the caller must treat null as "drop silently". We never
         * throw out of here: the WebView is untrusted input (spec §9).
         */
        fun parseInbound(raw: String?): InpMessage? {
            if (raw.isNullOrBlank()) {
                Log.w(TAG, "dropped: empty inbound")
                return null
            }
            return try {
                val obj = JSONObject(raw)

                // `inp` must be present and equal to our major (spec §2.2: breaking
                // changes bump it; the handshake negotiates). A different/absent
                // major is dropped here in the probe.
                val major = obj.optInt("inp", -1)
                if (major != PROTOCOL_MAJOR) {
                    Log.w(TAG, "dropped: inp major mismatch (got $major)")
                    return null
                }

                val id = obj.optString("id", "")
                val type = obj.optString("type", "")
                if (id.isEmpty() || type.isEmpty()) {
                    Log.w(TAG, "dropped: missing id/type")
                    return null
                }

                val replyTo = if (obj.isNull("replyTo")) null else obj.optString("replyTo", null)
                // Unknown payload fields are ignored (forward compat §2.2): we keep
                // the whole object and let handlers read what they understand.
                val payload = obj.optJSONObject("payload") ?: JSONObject()

                InpMessage(major, id, type = type, replyTo = replyTo, payload = payload)
            } catch (e: JSONException) {
                // Malformed JSON => drop + log, never crash (spec §8/§9).
                Log.w(TAG, "dropped: malformed inbound json", e)
                null
            }
        }

        /** Spike-grade uuid-v4. The real lib uses a stronger source. */
        fun randomUuid(): String = java.util.UUID.randomUUID().toString()
    }
}

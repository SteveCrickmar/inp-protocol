package com.example.inpspike

import android.content.Context
import org.json.JSONObject

/**
 * DISPOSABLE Phase-0 spike (OC-5).
 *
 * The probe "Session" message brain: it owns an [InpWebView] (transport) and
 * implements the slice of protocol behaviour S0.5 needs:
 *   - Handshake: on `adapter.ready` reply `session.configure` (spec §2.3).
 *   - Commanded visits with REPLACE-style history (spec §3.5).
 *   - History/popstate neutralisation diagnostics (`history.blockedPop`).
 *
 * It deliberately does NOT implement the full Navigator/path-config/stack — that
 * is the production library's job (N7.x). The probe is single-screen.
 */
class Session(
    context: Context,
    baseUrl: String,
    private val onLog: (DebugLogView.Direction, String, String?) -> Unit,
) {
    private val screenId = "screen-" + InpMessage.randomUuid().take(8)

    val inp = InpWebView(
        context = context,
        baseUrl = baseUrl,
        appPrefix = APP_PREFIX,
        appVersion = APP_VERSION,
        screenId = screenId,
        onMessage = { msg, channel -> onMessage(msg, channel) },
        onLog = onLog,
    )

    /** Diagnostic counter feeding ADR-0003: how many web popstates we neutralised. */
    var blockedPopCount = 0
        private set

    fun start() {
        inp.configure()
        inp.load()
    }

    fun toggleChannel(): InpWebView.Channel = inp.toggleChannel()

    // ---------------------------------------------------------------------------
    // Inbound handling
    // ---------------------------------------------------------------------------

    private fun onMessage(msg: InpMessage, @Suppress("UNUSED_PARAMETER") channel: String) {
        when (msg.type) {
            "adapter.ready" -> handleAdapterReady(msg)

            // Diagnostics the adapter emits when it neutralises a web-originated
            // popstate (spec §3.5 / §2.4). The probe just counts + logs so a dev
            // can correlate it with predictive-back gestures (ADR-0003).
            "history.blockedPop" -> {
                blockedPopCount++
                onLog(DebugLogView.Direction.INFO, "history.blockedPop", "count=$blockedPopCount")
            }

            "visit.propose" -> handleVisitPropose(msg)

            "log" -> {
                val p = msg.payload
                onLog(DebugLogView.Direction.INFO, "log:${p.optString("level")}", p.optString("message"))
            }

            // Unknown types are ignored + already debug-logged at the transport.
            else -> { /* no-op (forward compat, spec §2.2) */ }
        }
    }

    /** Spec §2.3 handshake: reply to adapter.ready with session.configure. */
    private fun handleAdapterReady(msg: InpMessage) {
        val configure = InpMessage.outbound(
            type = "session.configure",
            payload = JSONObject().apply {
                put("screenId", screenId)
                put("settings", JSONObject())   // path-config settings would go here
                put("debug", true)
            },
            replyTo = msg.id,
        )
        inp.send(configure)
    }

    /**
     * In a real shell, native consults path-config and creates/selects a screen,
     * then commands the visit. The single-screen probe immediately commands the
     * proposed visit back with REPLACE-style history so the WebView's history
     * depth stays ~1/screen (spec §3.5). We pass `action: replace` straight
     * through to the adapter, which performs the Inertia visit with replace
     * history.
     *
     * PROBE S0.5: this is the "commanded visit using replace-style history" the
     * S0.5 acceptance calls for — observe the WebView's history.length staying
     * flat across these in the debug log + via `adb shell` console (see README).
     */
    private fun handleVisitPropose(msg: InpMessage) {
        val p = msg.payload
        val url = p.optString("url", "")
        if (url.isBlank()) return

        val execute = InpMessage.outbound(
            type = "visit.execute",
            payload = JSONObject().apply {
                put("proposalId", p.optString("proposalId", InpMessage.randomUuid()))
                put("screenId", screenId)
                put("url", url)
                put("options", JSONObject().apply {
                    // REPLACE-style history is the whole point (spec §3.5): the
                    // adapter must perform router.visit(url, { replace: true })-
                    // equivalent so depth stays ~1.
                    put("replace", true)
                    // Forward the whitelisted visit options the proposal carried.
                    p.optJSONObject("options")?.let { opts ->
                        opts.optBoolean("preserveScroll").let { put("preserveScroll", it) }
                        opts.optBoolean("preserveState").let { put("preserveState", it) }
                    }
                })
            },
        )
        inp.send(execute)
    }

    companion object {
        // UA pieces (spec §5): "<AppPrefix>; Inertia Native Android/<v>; INP/1"
        const val APP_PREFIX = "InpSpike/1.0"
        const val APP_VERSION = "0.0.1-spike"
    }
}

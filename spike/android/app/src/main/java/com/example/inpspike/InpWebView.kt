package com.example.inpspike

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject

/**
 * DISPOSABLE Phase-0 spike (OC-5). The probe's "Session" (spec §5): owns the one
 * WebView, configures injection + UA + both message channels, and exposes the
 * native -> web send path. Runs entirely on the main thread.
 *
 * This single class is where S0.5's two questions are exercised:
 *   - Channel A: @JavascriptInterface (InpChannel.kt)
 *   - Channel B: WebViewCompat.addWebMessageListener (origin-scoped, AndroidX)
 * Switchable at runtime via [useWebMessageListener].
 */
class InpWebView(
    context: Context,
    private val baseUrl: String,
    private val appPrefix: String,
    private val appVersion: String,
    private val screenId: String,
    /** Called on MAIN thread for every validated inbound envelope, from EITHER channel. */
    private val onMessage: (InpMessage, channel: String) -> Unit,
    /** Probe log sink for direction-tagged lines. Main thread. */
    private val onLog: (DebugLogView.Direction, String, String?) -> Unit,
) {
    val webView = WebView(context)

    /** First-party origin we trust (spec §9). Everything else is dropped. */
    private val firstPartyOrigin: String = originOf(baseUrl)

    /** Channel A handler (kept registered; gated by [activeChannel]). */
    private val inpChannel = InpChannel(
        onMessageMain = { msg -> if (activeChannel == Channel.JS_INTERFACE) handle(msg, "A:@JavascriptInterface") },
        originGate = { isOnFirstParty() },
    )

    enum class Channel { JS_INTERFACE, WEB_MESSAGE_LISTENER }

    /** Default to option (a); the menu toggle flips this and re-injects bootstrap. */
    var activeChannel: Channel = Channel.JS_INTERFACE
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Custom User-Agent EXACTLY per spec §5:
            //   "<AppPrefix>; Inertia Native Android/<v>; INP/1"
            userAgentString = "$appPrefix; Inertia Native Android/$appVersion; INP/1"
        }
        Log.i(TAG, "UA = ${webView.settings.userAgentString}")

        // -- Channel A: @JavascriptInterface (always added; gated at dispatch) --
        // NOTE (spec §9): addJavascriptInterface exposes the object to ALL origins
        // the WebView loads. Our origin gate + the WebViewClient guard below are
        // what keep this first-party-only. This is the manual burden option (b)
        // removes.
        webView.addJavascriptInterface(inpChannel, InpChannel.JS_NAME)

        // -- Channel B: WebMessageListener (origin-scoped by the platform) -------
        installWebMessageListenerIfSupported()

        // -- Document-start injection of window.__INP__ (spec §2.3) --------------
        // Returns the bootstrap JS to inject in onPageStarted as a fallback, or
        // null when the preferred DOCUMENT_START_SCRIPT path is active.
        val fallbackBootstrap = installDocumentStartScript()

        // -- Single WebViewClient: off-origin nav guard + (maybe) fallback inject -
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                // Only runs the (racy) fallback when DOCUMENT_START_SCRIPT is
                // unsupported; otherwise fallbackBootstrap is null and this no-ops.
                fallbackBootstrap?.let { view.evaluateJavascript(it, null) }
                super.onPageStarted(view, url, favicon)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                if (request.isForMainFrame && originOf(target.toString()) != firstPartyOrigin) {
                    // Production shells open an in-app browser (Custom Tabs) here
                    // (spec §5 policy layer). The probe just blocks + logs so the
                    // origin gate's invariant (WebView stays first-party) holds.
                    onLog(DebugLogView.Direction.INFO, "blocked off-origin nav", target.toString())
                    return true
                }
                return false
            }
        }
    }

    fun load() {
        onLog(DebugLogView.Direction.INFO, "load", baseUrl)
        webView.loadUrl(baseUrl)
    }

    // ---------------------------------------------------------------------------
    // Document-start injection (spec §2.3): define window.__INP__ BEFORE app JS.
    // ---------------------------------------------------------------------------

    /**
     * Installs document-start injection. Returns the bootstrap JS to inject from
     * onPageStarted as a FALLBACK when DOCUMENT_START_SCRIPT is unsupported, or
     * null when the preferred path handled it.
     */
    private fun installDocumentStartScript(): String? {
        val bootstrap = buildBootstrapJs()

        // PREFERRED: WebViewCompat.addDocumentStartJavaScript runs the script at
        // document-start on every matching navigation, before page scripts — the
        // Android analogue of iOS's WKUserScript atDocumentStart (spec §2.1/§2.3).
        // Requires the DOCUMENT_START_SCRIPT WebView feature (a recent WebView
        // provider; gated by the installed WebView version, NOT the OS API level).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                bootstrap,
                // Only inject into our first-party origin (spec §9). "*" would leak
                // handshake constants into any embedded origin.
                setOf(firstPartyOrigin),
            )
            onLog(DebugLogView.Direction.INFO, "inject via addDocumentStartJavaScript", firstPartyOrigin)
            return null
        }

        // FALLBACK for older WebView providers (S0.5 delta): no true document-start
        // hook. The closest is WebViewClient.onPageStarted + evaluateJavascript,
        // BUT that races app JS — there is no guarantee window.__INP__ exists
        // before the first app script reads it. This is a real risk the adapter's
        // detection must tolerate (it already treats a missing __INP__ as "not
        // native / degraded", spec §2.3). The production lib should additionally
        // require a minimum WebView version or pre-seed via the page
        // (server-rendered) when injection isn't available. Recorded for
        // ADR-0004 / ADR-0003 deltas.
        onLog(DebugLogView.Direction.INFO, "DOCUMENT_START_SCRIPT unsupported", "fallback=onPageStarted (racy)")
        return bootstrap
    }

    /**
     * The exact handshake object the adapter reads (spec §2.3). receive() is added
     * by the web adapter; we only seed the constants here. supportedComponents is
     * empty for the probe (no bridge components). settings is {} until
     * session.configure arrives.
     */
    private fun buildBootstrapJs(): String {
        val inp = JSONObject().apply {
            put("platform", "android")
            put("appVersion", appVersion)
            put("protocolVersions", JSONArray(listOf(InpMessage.PROTOCOL_MAJOR)))
            put("supportedComponents", JSONArray())
            put("screenId", screenId)
            put("settings", JSONObject())
        }
        // Define __INP__ idempotently and DO NOT clobber a receive() that the
        // adapter may attach. The adapter merges receive onto whatever exists.
        return """
            (function () {
              var seed = $inp;
              window.__INP__ = window.__INP__ || {};
              for (var k in seed) { if (!(k in window.__INP__)) window.__INP__[k] = seed[k]; }
            })();
        """.trimIndent()
    }

    // ---------------------------------------------------------------------------
    // Channel B: WebMessageListener (AndroidX, origin-scoped).
    // ---------------------------------------------------------------------------

    private fun installWebMessageListenerIfSupported() {
        // API-LEVEL REACH (S0.5): WebMessageListener is an androidx.webkit feature
        // gated on the *WebView provider* version (WEB_MESSAGE_LISTENER feature),
        // NOT on the framework API level. At minSdk 26 it is therefore AVAILABLE
        // ON MOST DEVICES (Chrome/AndroidX WebView updates independently of the OS)
        // but is NOT GUARANTEED — a device with a very old, un-updatable WebView
        // (rare, but exists on locked-down/AOSP-only devices) will report the
        // feature unsupported. So: option (b) needs option (a) as a fallback to
        // cover the long tail. This is the core ADR-0004 trade-off.
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            onLog(DebugLogView.Direction.INFO, "WEB_MESSAGE_LISTENER unsupported", "channel B unavailable on this WebView")
            return
        }

        // allowedOriginRules: the PLATFORM drops any message whose source origin
        // doesn't match — origin scoping is structural here (spec §9), unlike
        // option (a). We scope to exactly our first-party origin.
        WebViewCompat.addWebMessageListener(
            webView,
            // jsObjectName: exposed as window.InpChannelB.postMessage(...) on the
            // web side. (We use a distinct name so both channels can coexist for
            // side-by-side comparison; production would reuse one name.)
            "InpChannelB",
            setOf(firstPartyOrigin),
        ) { _, message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean, _ ->
            // THREADING NOTE (S0.5): this callback is delivered on the UI/main
            // thread by default (the listener is registered without a custom
            // Handler), so NO marshalling is needed — a concrete ergonomics win
            // for option (b) over option (a)'s background-thread hop.
            if (activeChannel != Channel.WEB_MESSAGE_LISTENER) return@addWebMessageListener

            // Defense in depth: the platform already enforced allowedOriginRules,
            // but we also require the main frame (spec §9: ignore sub-frames).
            if (!isMainFrame || originOf(sourceOrigin.toString()) != firstPartyOrigin) {
                Log.w(TAG, "dropped inbound (B): origin=$sourceOrigin mainFrame=$isMainFrame")
                return@addWebMessageListener
            }

            val msg = InpMessage.parseInbound(message.data) ?: return@addWebMessageListener
            handle(msg, "B:WebMessageListener")
        }
        onLog(DebugLogView.Direction.INFO, "addWebMessageListener", "InpChannelB origin=$firstPartyOrigin")
    }

    // ---------------------------------------------------------------------------
    // Native -> web send path (spec §2.1): evaluateJavascript on the MAIN thread.
    // ---------------------------------------------------------------------------

    fun send(msg: InpMessage) {
        val json = msg.toJson()
        onLog(DebugLogView.Direction.OUTBOUND, msg.type, msg.payload.toString().take(120))
        // window.__INP__.receive(<json>) — the adapter's native->web entry point.
        // We embed the JSON as a single string literal arg to dodge quoting bugs.
        val script = "window.__INP__ && window.__INP__.receive(${JSONObject.quote(json)});"
        // evaluateJavascript MUST run on the main thread (we always are here).
        webView.evaluateJavascript(script, null)
    }

    /** Flip channels at runtime (menu toggle). Re-loads so injection re-runs cleanly. */
    fun toggleChannel(): Channel {
        activeChannel = if (activeChannel == Channel.JS_INTERFACE) {
            Channel.WEB_MESSAGE_LISTENER
        } else {
            Channel.JS_INTERFACE
        }
        onLog(DebugLogView.Direction.INFO, "channel ->", activeChannel.name)
        // The web adapter picks its egress channel from window.__INP__; in a real
        // build we'd push the choice via session.configure. For the probe we just
        // reload so both seeds re-apply and the user can drive the chosen channel.
        webView.reload()
        return activeChannel
    }

    // ---------------------------------------------------------------------------
    // Internal dispatch
    // ---------------------------------------------------------------------------

    private fun handle(msg: InpMessage, channelLabel: String) {
        onLog(DebugLogView.Direction.INBOUND, msg.type, "via $channelLabel")
        onMessage(msg, channelLabel)
    }

    private fun isOnFirstParty(): Boolean {
        val current = webView.url ?: return false
        return originOf(current) == firstPartyOrigin
    }

    private fun originOf(url: String): String =
        try {
            val u = Uri.parse(url)
            val port = if (u.port == -1) "" else ":${u.port}"
            "${u.scheme}://${u.host}$port"
        } catch (e: Exception) {
            ""
        }

    companion object {
        private const val TAG = "InpWebView"
    }
}

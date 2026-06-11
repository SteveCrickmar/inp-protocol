package com.example.inpspike

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface

/**
 * DISPOSABLE Phase-0 spike (OC-5). PROBE for S0.5 / spec §12.5 + §2.1.
 *
 * Web -> native message ingress. This file implements **channel option (a)**
 * (the classic `@JavascriptInterface`). Channel option (b)
 * (`WebViewCompat.addWebMessageListener`) is wired in InpWebView.kt because it is
 * configured against the WebView itself, not a standalone object; the threading
 * and origin-scoping notes for BOTH options are gathered here so ADR-0004 has one
 * place to read from.
 *
 * ============================================================================
 *  ADR-0004 INPUT — Channel option (a): @JavascriptInterface
 * ============================================================================
 *
 * Web side (matches the spike adapter):
 *     window.InpChannel.postMessage(JSON.stringify(envelope))
 *
 * Availability: works on every WebView back to the dawn of time; zero API-level
 * constraints at minSdk 26. No AndroidX dependency.
 *
 * THREADING NOTE (the headline S0.5 finding):
 *   @JavascriptInterface methods are invoked on a PRIVATE WebView background
 *   thread (a "JavaBridge" thread), NOT the UI thread. Anything that touches the
 *   WebView (evaluateJavascript), Views, or app state that expects the main
 *   thread MUST be marshalled. We do that with a main-thread Handler below
 *   (see the `// PROBE S0.5:` boundary). Forgetting this is the #1 footgun:
 *   it "works" until it sporadically crashes or corrupts state under load.
 *
 * ORIGIN-SCOPING / SECURITY NOTE (spec §9):
 *   @JavascriptInterface has NO built-in origin scoping. The interface object is
 *   injected into EVERY frame/origin the WebView loads (main frame, sub-frames,
 *   iframes, redirected-to pages). If the WebView ever navigates to or embeds an
 *   untrusted origin, that origin can call postMessage() and impersonate the
 *   adapter. Spec §9 requires first-party-only ingress. With option (a) we must
 *   enforce that OURSELVES — e.g. gate every inbound message on the WebView's
 *   current first-party URL, and/or only addJavascriptInterface for trusted
 *   loads. The probe demonstrates a coarse gate via [originGate]. This manual
 *   burden is the principal mark AGAINST option (a).
 *
 * Compare: option (b) addWebMessageListener takes `allowedOriginRules` and the
 * platform itself drops messages from non-matching origins, and it delivers a
 * `sourceOrigin` + `isMainFrame` per message — origin scoping is structural, not
 * something we hand-roll. See InpWebView.kt.
 */
class InpChannel(
    /** Called on the MAIN thread with each validated inbound envelope. */
    private val onMessageMain: (InpMessage) -> Unit,
    /**
     * Returns true if the WebView's CURRENT origin is first-party and may speak
     * INP. For option (a) we cannot know the *sender* frame's origin, so this is
     * a coarse main-frame gate — a documented limitation feeding ADR-0004.
     */
    private val originGate: () -> Boolean,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Exposed to JS as `window.InpChannel.postMessage(json)`.
     *
     * Invoked on the WebView's background JavaBridge thread. Keep this method's
     * own work minimal and thread-safe; hop to main for everything else.
     */
    @JavascriptInterface
    fun postMessage(json: String?) {
        // We are on a BACKGROUND thread here. Do not touch Views or the WebView.
        val threadName = Thread.currentThread().name
        Log.d(TAG, "postMessage on thread=$threadName (expected: NOT main)")

        // PROBE S0.5: threading boundary — marshal the background callback to the
        // main thread before doing anything that the rest of the app (View tree,
        // WebView.evaluateJavascript, message handlers) expects on main.
        mainHandler.post {
            // --- now on the MAIN thread ---

            // Origin scoping (spec §9): option (a) gives us no per-message origin,
            // so we reject everything unless the WebView is currently on a
            // first-party page. Coarse, but it's the best option (a) allows.
            if (!originGate()) {
                Log.w(TAG, "dropped inbound: origin gate closed (non first-party)")
                return@post
            }

            val msg = InpMessage.parseInbound(json) ?: return@post  // malformed => dropped+logged
            onMessageMain(msg)
        }
    }

    companion object {
        private const val TAG = "InpChannel"

        /** The JS-visible name. Must match the web adapter: `window.InpChannel`. */
        const val JS_NAME = "InpChannel"
    }
}

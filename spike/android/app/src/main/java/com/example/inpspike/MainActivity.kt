package com.example.inpspike

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * DISPOSABLE Phase-0 spike (OC-5). INP S0.5 Android probe — single Activity,
 * single WebView, on-screen debug log, runtime channel toggle, and back /
 * predictive-back wiring for the history-neutralisation question.
 *
 * Base URL is hard-coded to the emulator host alias (documented below). Change it
 * if you run on a physical device with `adb reverse`.
 */
class MainActivity : AppCompatActivity() {

    // 10.0.2.2 is the Android EMULATOR's alias for the host machine's loopback,
    // so this reaches `php artisan serve --port=8111` running on the host. On a
    // physical device use `adb reverse tcp:8111 tcp:8111` and switch to
    // http://localhost:8111 (both are allow-listed in network_security_config.xml).
    private val baseUrl = "http://10.0.2.2:8111"

    private lateinit var debugLog: DebugLogView
    private lateinit var session: Session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        debugLog = DebugLogView(this)
        session = Session(
            context = this,
            baseUrl = baseUrl,
            onLog = { dir, type, detail -> debugLog.append(dir, type, detail) },
        )

        setContentView(buildUi())
        wireBackHandling()

        session.start()
        debugLog.append(DebugLogView.Direction.INFO, "probe started", "channel=${session.inp.activeChannel.name}")
        debugLog.append(DebugLogView.Direction.INFO, "predictive-back opt-in", predictiveBackStatus())
    }

    // ---------------------------------------------------------------------------
    // UI: WebView (weight 3) over a debug log (weight 1) with a channel toggle.
    // ---------------------------------------------------------------------------

    private fun buildUi(): ViewGroup {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val toggle = Button(this).apply {
            text = "Toggle channel (now: A @JavascriptInterface)"
            setOnClickListener {
                val now = session.toggleChannel()
                text = when (now) {
                    InpWebView.Channel.JS_INTERFACE -> "Toggle channel (now: A @JavascriptInterface)"
                    InpWebView.Channel.WEB_MESSAGE_LISTENER -> "Toggle channel (now: B WebMessageListener)"
                }
            }
        }
        root.addView(toggle, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(
            session.inp.webView,
            LinearLayout.LayoutParams(MATCH, 0, 3f),
        )
        root.addView(
            debugLog,
            LinearLayout.LayoutParams(MATCH, 0, 1f).apply { gravity = Gravity.BOTTOM },
        )
        return root
    }

    // ---------------------------------------------------------------------------
    // Back handling (spec §3.5 / §5): native owns back. The WebView's goBack() is
    // NEVER used — hardware/gesture/predictive back routes through the Navigator
    // (here: a single screen, so back exits after logging an observation).
    // ---------------------------------------------------------------------------

    private fun wireBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {

            // PROBE S0.5: predictive-back observation point (API 34+).
            // On API 34+ with android:enableOnBackInvokedCallback="true", these
            // progress callbacks fire DURING the back GESTURE (before commit),
            // driving the system's predictive animation. Record here whether the
            // adapter sees any web-originated popstate while the gesture is in
            // flight — the worry (spec §12.3) is that the predictive swipe peeks
            // the WebView's *own* history and triggers a popstate the re-push
            // must catch. Watch the log for `history.blockedPop` lines correlated
            // with these progress events.
            override fun handleOnBackStarted(backEvent: androidx.activity.BackEventCompat) {
                debugLog.append(
                    DebugLogView.Direction.INFO,
                    "predictiveBack:started",
                    "edge=${backEvent.swipeEdge} progress=${"%.2f".format(backEvent.progress)}",
                )
            }

            override fun handleOnBackProgressed(backEvent: androidx.activity.BackEventCompat) {
                // PROBE S0.5: high-frequency; uncomment to trace the full curve.
                // debugLog.append(Direction.INFO, "predictiveBack:progress",
                //     "progress=${"%.2f".format(backEvent.progress)}")
            }

            override fun handleOnBackCancelled() {
                // PROBE S0.5: gesture aborted — did the WebView's history move and
                // need re-pushing even though we cancelled? Note it in ADR-0003.
                debugLog.append(DebugLogView.Direction.INFO, "predictiveBack:cancelled", null)
            }

            override fun handleOnBackPressed() {
                // PROBE S0.5: back COMMITTED. In the real Navigator this pops a
                // native screen / dismisses a modal. The single-screen probe logs
                // and then lets the system handle it (exit). We must NEVER call
                // webView.goBack() — native owns history (spec §3.5).
                debugLog.append(
                    DebugLogView.Direction.INFO,
                    "back committed",
                    "neutralisedPops=${session.blockedPopCount}",
                )
                isEnabled = false
                onBackPressedDispatcher.onBackPressed() // forward to system => exit
            }
        })
    }

    private fun predictiveBackStatus(): String =
        if (Build.VERSION.SDK_INT >= 34) {
            "API ${Build.VERSION.SDK_INT}: predictive system animations active (manifest opt-in set)"
        } else {
            "API ${Build.VERSION.SDK_INT}: progress callbacks fire (33+) but no predictive UI <34"
        }

    // ---------------------------------------------------------------------------
    // Overflow menu mirror of the toggle (S0.5: "button/menu to switch channels").
    // ---------------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_TOGGLE, 0, "Toggle INP channel")
        menu.add(0, MENU_PING, 1, "Send native->web ping")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            MENU_TOGGLE -> { session.toggleChannel(); true }
            MENU_PING -> {
                // Exercises the native->web send path (evaluateJavascript). The
                // spike adapter has no 'echo' on its own initiative, but
                // window.__INP__.receive will see it and (unknown type) debug-log,
                // which still proves the channel is alive end-to-end.
                session.inp.send(InpMessage.outbound("echo", org.json.JSONObject().put("from", "native")))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val MENU_TOGGLE = 1
        private const val MENU_PING = 2
    }
}

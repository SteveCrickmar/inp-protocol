package com.example.inpspike

import android.content.Context
import android.graphics.Color
import android.text.method.ScrollingMovementMethod
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.MainThread
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DISPOSABLE Phase-0 spike (OC-5).
 *
 * A dead-simple on-screen debug log: a [TextView] inside a [ScrollView] showing
 * inbound/outbound INP messages with direction + type + timestamp (S0.1 / S0.5
 * harness requirement). Newest line at the bottom; auto-scrolls.
 *
 * MUST be touched on the main thread only — see [append].
 */
class DebugLogView(context: Context) : ScrollView(context) {

    enum class Direction(val arrow: String) {
        INBOUND("← in "),   // native ← web   (JS -> native)
        OUTBOUND("→ out"),  // native → web   (native -> JS)
        INFO("·  info"),    // probe-internal note (channel switch, predictive back, etc.)
    }

    private val text = TextView(context).apply {
        setTextColor(Color.parseColor("#9AE6B4"))
        setBackgroundColor(Color.parseColor("#101418"))
        typeface = android.graphics.Typeface.MONOSPACE
        textSize = 11f
        setPadding(16, 16, 16, 16)
        movementMethod = ScrollingMovementMethod()
    }

    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val sb = StringBuilder()

    init {
        addView(text)
    }

    /**
     * Append one line. Callers from a background thread (notably the
     * @JavascriptInterface channel — see InpChannel.kt) MUST marshal to main
     * first; this method asserts main-thread to catch mistakes early in the probe.
     */
    @MainThread
    fun append(direction: Direction, type: String, detail: String? = null) {
        val ts = clock.format(Date())
        sb.append(ts).append("  ").append(direction.arrow).append("  ").append(type)
        if (!detail.isNullOrBlank()) sb.append("   ").append(detail)
        sb.append('\n')
        // Keep the buffer bounded so a chatty session doesn't grow without limit.
        if (sb.length > 16_000) sb.delete(0, sb.length - 12_000)
        text.text = sb
        post { fullScroll(FOCUS_DOWN) }
    }
}

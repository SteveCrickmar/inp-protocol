# INP Android Probe — Phase-0 Spike (S0.5)

> **DISPOSABLE probe code (OC-5).** This app is throwaway. Its only outputs are
> the observations feeding **ADR-0003** (history / predictive-back) and
> **ADR-0004** (Android message-channel choice). It is never imported by the
> production `inertia-native-android` library.
>
> **Authored on Linux WITHOUT an Android SDK — a compile pass is PENDING.** The
> Kotlin is written to be idiomatic and spec-faithful, but it has not been built.
> Expect to nudge AGP / Kotlin / dependency versions to whatever your installed
> Android Studio supports, and to fix any minor API drift on first open.

## What this probe answers (spec §12.3 + §12.5)

1. **Message channel choice (→ ADR-0004).** Implements BOTH ingress channels
   side-by-side, switchable at runtime:
   - **(a) `@JavascriptInterface`** — `InpChannel.postMessage(json)` (`InpChannel.kt`).
   - **(b) `WebViewCompat.addWebMessageListener`** — origin-scoped, AndroidX
     (`InpWebView.kt`).
2. **History neutralisation under gesture nav + predictive back (→ ADR-0003).**
   `OnBackPressedCallback` + the predictive-back progress APIs (`MainActivity.kt`),
   commanded visits using replace-style history, `history.blockedPop` diagnostics
   (`Session.kt`).

## Open in Android Studio & run

1. Open `spike/android/` as a project in Android Studio (Giraffe+ / AGP 8.x).
2. Let Gradle sync. Fix version mismatches if your SDK differs from `app/build.gradle.kts`.
3. **Start the spike server** (the Laravel + Inertia v3 app from S0.1) on the host:
   `php artisan serve --host=0.0.0.0 --port=8111` (default base URL is
   `http://10.0.2.2:8111`).
4. Run the app on an emulator. `10.0.2.2` is the emulator's alias for the host's
   loopback; cleartext to it is allow-listed in `network_security_config.xml`
   (dev-only, spec §9).
   - **Physical device:** run `adb reverse tcp:8111 tcp:8111`, then change
     `baseUrl` in `MainActivity.kt` to `http://localhost:8111`.

### Emulator matrix to exercise (S0.5)

| API | Why |
|-----|-----|
| **26** | minSdk floor (OC-8). Confirm channel (b) availability on the device's WebView; confirm `DOCUMENT_START_SCRIPT` path vs the racy `onPageStarted` fallback. |
| **29** | Mid baseline. |
| **33** | Predictive-back **progress callbacks** fire, but **no** predictive system UI yet. |
| **35** | **Predictive back is the headline case** — system back animations are user-visible (manifest opt-in is set). Record gesture behaviour here. |

## Switching channels at runtime

- The on-screen **"Toggle channel"** button, or the overflow-menu **"Toggle INP
  channel"** item, flips between (a) and (b). The WebView reloads so injection
  re-runs. The active channel is shown on the button and logged.
- **"Send native→web ping"** (overflow menu) exercises the native→web send path
  (`evaluateJavascript("window.__INP__.receive(<json>)")`).

> **Known web-side divergence (document, don't fix here):** the disposable web
> adapter (`spike/server/.../inp-spike.js`) only egresses via
> `window.InpChannel.postMessage` (channel a). Channel (b)'s JS object is exposed
> as `window.InpChannelB`; to exercise (b)'s **web→native** direction you must
> either point the adapter at `InpChannelB`, or read inbound (b) via a quick
> console call `window.InpChannelB.postMessage(JSON.stringify({inp:1,...}))`.
> The **native→web** direction is channel-agnostic (always `__INP__.receive`).

## What to observe — observation points feeding the ADRs

### ADR-0004 (channel choice) — see `InpChannel.kt` and `InpWebView.kt`
- **Threading:** `@JavascriptInterface` `postMessage` runs on a **background**
  WebView thread (logged: `thread=...`); the probe marshals to main via a
  `Handler` at the `// PROBE S0.5:` boundary. `addWebMessageListener` delivers on
  the **main** thread — no marshalling needed.
- **Origin scoping (spec §9):** option (a) has **none** built-in (injected into
  every frame/origin) — the probe hand-rolls a coarse `originGate`. Option (b)
  takes `allowedOriginRules`; the **platform** drops non-matching origins and
  gives `sourceOrigin` + `isMainFrame` per message.
- **API-level reach:** option (b) is gated on the **WebView provider** version
  (`WEB_MESSAGE_LISTENER` feature), not the OS API — available on most updated
  devices at minSdk 26 but **not guaranteed** on an old/locked-down WebView, so
  it needs (a) as a long-tail fallback. Confirm on the API-26 emulator.
- **Document-start injection:** preferred `addDocumentStartJavaScript`
  (`DOCUMENT_START_SCRIPT` feature) vs the racy `onPageStarted` fallback — check
  which path each emulator takes (logged).

### ADR-0003 (history / predictive back) — see `MainActivity.kt` and `Session.kt`
- The `// PROBE S0.5:` points in `handleOnBackStarted/Progressed/Cancelled/Pressed`
  are where a dev **records predictive-back behaviour on API 34+**: does a
  predictive back-swipe peek the WebView's own history and emit a `popstate` the
  adapter must re-push? Watch the log for `history.blockedPop` lines correlated
  with `predictiveBack:*` lines.
- Commanded visits go out as `visit.execute` with `options.replace = true`
  (`Session.kt`) so the WebView's `history.length` stays ~1/screen (spec §3.5).
  Inspect `history.length` via `chrome://inspect` against the emulator's WebView.
- The probe **never** calls `webView.goBack()` — native owns history (spec §3.5).

## Spec ambiguities found (→ potential RFC issues, OC-3)

See the summary returned with this probe; none were "fixed" in code (OC-3: the
spike files RFCs, it does not invent fields).

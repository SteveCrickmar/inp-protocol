# ADR-0003 — History neutralisation (iOS + Android)

- **Status:** Proposed (desk analysis) — **empirical sections PENDING MEASUREMENT**
- **Tasks:** S0.4 (iOS) + S0.5 (Android history half) · **Spec refs:** §3.5, §12.3
- **Co-owned:** authored on the S0.4 branch to cover both platforms (S0.5 lists it as a
  deliverable); the Android probe app lands with S0.5.

> ⚠️ **Authoring caveat.** Written on Linux without an iOS simulator or Android emulator. The
> `history.length` expectations and the predictive-back behaviour are the *experimental
> design*; result cells say `PENDING` and MUST be filled from device/emulator transcripts
> before G0. Browsers cannot cancel `popstate`; the re-push workaround below is the standard
> technique but its robustness under Android gesture-nav + predictive back is exactly what
> S0.5 must confirm.

## Context

Inside the shell, **native owns history** (spec §3.5). The adapter must neutralise the
webview's own history as a navigation surface so the webview's history depth stays ~1 entry per
screen and web-originated `popstate` never moves the native stack out from under the user.

Two mechanisms combine:
1. **Commanded visits use replace-style history** — `visit.execute` renders without pushing a
   new web history entry, keeping depth flat.
2. **`popstate` re-push neutralisation** — a web-originated `popstate` is detected, suppressed
   by immediately re-pushing the current state, and reported via `history.blockedPop` (spec
   §2.4) for diagnostics. The browser can't cancel `popstate`, so re-push is the workaround.

Android adds hardware/gesture back and **predictive back (API 34+)**: hardware/gesture back is
handled natively via `OnBackPressedCallback` (pop screen / dismiss modal / exit) and must never
reach `WebView.goBack()` (spec §3.5, §5).

## Options considered

**Replace vs push for commanded visits.**
- *Replace-style (chosen).* Keep web history flat; native stack is the source of truth.
- *Push + neutralise.* Let the visit push, then collapse — more moving parts, more popstate
  events to suppress. **Rejected.**

**popstate handling.**
- *Re-push current state (chosen).* The only workable approach since popstate isn't cancelable.
- *Disable history entirely.* Not possible without breaking Inertia's router internals.

**Android back routing.**
- *`OnBackPressedCallback` → Navigator (chosen).* Single native enforcement point; predictive
  back integrates via the AndroidX predictive-back APIs.
- *Let WebView handle back.* **Rejected** — re-introduces web history as a navigation surface.

## Decision (provisional)

- **iOS & Android adapter:** commanded visits use replace-style history; web-originated
  `popstate` is neutralised by re-push and reported as `history.blockedPop`. The module is
  **fully inert on plain web** (spec §3.5; A2.6 AC3).
- **Android native:** route all back gestures through `OnBackPressedCallback` to the Navigator;
  never call `WebView.goBack()`. Predictive-back (API 34+) is wired through the AndroidX
  predictive-back callback so the system animation previews a *native* pop, not a web one.
- Encode **platform-conditional caveats** discovered by S0.5 into the adapter's history module
  as documented behaviour (A2.6 references this ADR).

## iOS findings (S0.4) — **PENDING MEASUREMENT**

Harness: `spike/ios/HistoryProbeViewController.swift` (`// PROBE S0.4:` markers).

| Check | Expectation | Result |
|---|---|---|
| `history.length` after 5 pushes + 3 pops | stays ~1 per live screen (documented constant) | PENDING |
| Forced `history.back()` in page JS | neutralised (re-push) + one `history.blockedPop` emitted | PENDING |
| Logged message transcript attached | transcript in ADR appendix | PENDING |

## Android findings (S0.5) — **PENDING MEASUREMENT**

Harness: `spike/android/` (`// PROBE S0.5:` markers).

| Check | Expectation | Result |
|---|---|---|
| popstate re-push under **gesture nav** | popstate neutralised; native pop occurs instead | PENDING |
| **Predictive back (API 34+)** preview | shows native pop preview; commit pops native stack | PENDING |
| Predictive back **cancel** (release mid-gesture) | no web navigation, no stack change | PENDING |
| `history.length` after 5 pushes + 3 pops | matches iOS expectation | PENDING |
| Recording of predictive-back on API 34+ | linked in ADR | PENDING |

## Consequences

- `history.blockedPop` is confirmed as a real (diagnostic-only) message in SPEC.md §2.4.
- If predictive-back cannot be reconciled with re-push on some API level, the finding becomes a
  **spec RFC issue before G0** (S0.5 AC3) — e.g. a `history.willPop` native→web pre-notify.
- The adapter history module (A2.6) gains a Playwright real-browser smoke test (jsdom history
  is unfaithful) plus platform-conditional Android branches keyed to this ADR.
- Documented constant: **web `history.length` ≈ 1 per live screen** — surfaced in the protocol
  reference so third-party shells implement the same discipline.

# ADR-0002 — WKWebView re-parenting under a live SPA

- **Status:** Proposed (desk analysis) — **empirical sections PENDING MEASUREMENT**
- **Task:** S0.3 · **Spec refs:** §1 (shared webview), §2.7, §4 (ScreenViewController), §12.2

> ⚠️ **Authoring caveat.** Written on Linux without Xcode/simulator. The findings table and the
> "no white flash" / media / IME assertions are the *experimental design*; result cells say
> `PENDING` and MUST be filled from a simulator recording before G0. The workarounds below are
> derived from WKWebView semantics and the Hotwire Native precedent, not yet observed here.

## Context

The whole architecture rests on **one shared `WKWebView` re-parented between native screens**
(spec §1, §2.7): the screen hosting the live webview shows real content; other screens show a
cached screenshot. Moving a live webview between two `UIViewController` view hierarchies
mid-SPA-lifecycle can disturb the React/Vue tree (focus/IME, fixed-position layout, `<video>`,
in-flight XHR/WebSocket) and can flash white if the snapshot is taken at the wrong moment.
S0.3 must prove the choreography works and document the adapter's obligations.

The harness probe (`spike/ios/ReparentProbeViewController.swift`) moves the same `WKWebView`
instance from VC A into VC B and back, emitting `screen.willDetach` / `screen.didAttach` /
`page.restore` around the move (spec §2.7), with `// PROBE S0.3:` markers at each observation
point.

## Options considered

This ADR is primarily a *findings* record, but two design choices fall out of it:

**Snapshot timing — when to capture the screenshot that covers the outgoing screen.**
- *Option A: snapshot on `screen.willDetach`, synchronously before re-parenting.* Guarantees a
  non-blank image exists before the webview moves. Risk: a frame of cost on the main thread.
- *Option B: snapshot lazily after the transition starts.* Cheaper, but races the re-parent →
  white-flash risk. **Rejected** on the hypothesis that it cannot guarantee a covered frame.

**Pre-detach pause behaviours the adapter must perform on `screen.willDetach`.**
- Pause media (`<video>`/audio), blur the active element (release IME/first responder), and
  snapshot scroll positions (spec §3.2). These are emitted as adapter obligations regardless of
  the native snapshot mechanism.

## Decision (provisional)

1. **Snapshot on `screen.willDetach` (Option A), synchronously**, using
   `WKWebView.takeSnapshot(with:)` (or `drawViewHierarchyInRect` as a fallback), and only swap
   the screenshot view in **after** the snapshot resolves — the `ScreenViewController` shows the
   live webview until then. This is the white-flash-avoidance contract (I3.3 AC1).
2. **On `screen.willDetach` the adapter MUST:** (a) snapshot scroll positions; (b) pause media;
   (c) blur the active element to dismiss the keyboard/IME cleanly. Encode these as the
   `screen.willDetach` semantics in SPEC.md §2.5.
3. **In-flight XHR/WebSocket survive the move** (they belong to the web content process, which
   is not destroyed by re-parenting) — to be *confirmed* by the probe, not assumed. If a case
   fails, file a spec RFC adding a pre-detach drain/notify message.

## Findings — **PENDING MEASUREMENT**

Run each scenario in the harness; record pass/fail + a screen recording link.

| Scenario | Expectation | Result |
|---|---|---|
| Push A→B, pop B→A, ×5 | No white flash at any transition midpoint (pixel check non-blank) | PENDING |
| Keyboard open in a form on A, then push | IME dismisses cleanly; no stuck first responder | PENDING |
| `<video>` playing on A, then push then pop | Playback pauses on detach, resumes/holds correctly | PENDING |
| `position: fixed` header on A | No layout jump / detachment artefact after re-parent | PENDING |
| In-flight XHR during push | Request completes; response renders on the correct screen | PENDING |
| Open WebSocket during push/pop | Socket stays connected across the move | PENDING |
| Snapshot timing sweep | Earliest capture point that still avoids a white flash | PENDING |

## Consequences

- SPEC.md §2.5 `screen.willDetach` gains explicit adapter obligations (pause media, blur,
  snapshot scroll) — this ADR is the source for that wording (folded in by S0.7).
- The iOS `SnapshotCache` + `ScreenViewController` (I3.3) implement the Option-A timing; the
  XCUITest pixel check at transition midpoint (I3.3 AC1) is the regression guard.
- Any scenario that fails becomes a **spec RFC issue before G0** (S0.3 AC3) — e.g. if WebSocket
  drops on re-parent, the protocol may need a `screen.willDetach` ack/drain handshake.
- React vs Vue: both are tested in the demo (D5.1); if one framework misbehaves on re-parent
  (e.g. effect double-fire), the finding is recorded per-framework here.

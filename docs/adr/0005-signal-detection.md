# ADR-0005 — Server signal detection mechanism

- **Status:** Proposed — core mechanism **demonstrated on Linux**; sub-path/proxy robustness
  **PENDING MEASUREMENT**
- **Task:** S0.6 · **Spec refs:** §2.4 (`signal`), §6.2, §12.4, FRM-2/FRM-3

> ⚠️ **Authoring caveat.** The reserved-shared-prop mechanism is *implemented and verified* in
> `spike/server` (the `/_inp/*` routes render a page carrying `inp.signal`, confirmed by curl —
> see that README). What remains `PENDING` is the empirical confirmation behind a real
> reverse proxy + `/app` sub-path deployment, which needs a proxy the Linux box did not host.

## Context

The server manipulates the native stack by sending **signals** — `recede` (pop), `refresh`,
`resume` — when a native client follows a redirect (spec §6.2, FRM-2). Web clients must get a
plain redirect. The adapter has to *detect* that a response is a signal so it can emit
`signal{name, flash, fallbackUrl}` to native and **never render** the signal page (§2.4).

Two detection mechanisms were proposed (§12.4):

- **(a) Reserved shared prop** — the signal route returns a normal Inertia page whose shared
  props carry `inp.signal = {name, flash, fallbackUrl}`. The adapter inspects page props on
  navigation and, if present, emits the signal and suppresses rendering.
- **(b) Reserved route / URL sniffing** — the adapter recognises the signal by the response URL
  matching the configured signal prefix (e.g. `/_inp/recede`).

## Options considered

**(a) Reserved shared prop.**
- *Upside:* robust across proxies, sub-path deployments, and route renames — detection does not
  depend on URL shape, only on a prop the server controls. Carries flash data inline (FRM-3).
  Works identically whether the app is at `/` or `/app` or behind a CDN that rewrites paths.
- *Downside:* requires the page object to actually reach the adapter (a real, if minimal,
  Inertia render) — but that is cheap and already the redirect target.

**(b) Reserved route / URL sniffing.**
- *Upside:* trivial to implement; no server prop needed.
- *Downside:* **brittle** under sub-path deployments (`APP_URL=/app`), reverse proxies that
  strip/rewrite prefixes, and trailing-slash/locale-prefix variations. The configured prefix
  must be kept in sync on both ends. **Rejected as the primary** for exactly the §12.4 reason
  ("reserved shared prop is more robust than URL sniffing across proxies/subdirectories").

## Decision

Adopt **(a) the reserved shared prop `inp.signal`** as the detection mechanism. Exact shape to
encode in SPEC.md (§6.2 + path-config/handshake docs):

```jsonc
// shared props of the signal-route page object
{
  "inp": {
    "signal": {
      "name": "recede" | "refresh" | "resume",
      "flash": { "message": "Saved ✓", "...": "arbitrary flashed data" } | null,
      "fallbackUrl": "https://app.example.com/orders/7"   // where web (or a non-native
                                                          // fallback) should land instead
    }
  }
}
```

Adapter behaviour (A2.8): on the Inertia `navigate` event, if `page.props.inp.signal` is
present **and** running native, emit `signal{name, flash, fallbackUrl}` and suppress the
component swap (the signal page never paints, §6.2). On plain web, no detection runs and the
page (a normal redirect target) renders as usual.

Server side (L4.4): `recede_or_redirect()` / `refresh_or_redirect()` / `resume_or_redirect()`
redirect native requests (303) to the signal route carrying flash in the session; the signal
route renders the minimal page with `inp.signal`. Web requests get the plain redirect.

## Evidence

**Demonstrated (Linux, curl):**

```
GET /_inp/recede  (X-Inertia) → component "Signal", props.inp.signal.name == "recede",
                                 fallbackUrl present, flash carried from session.
PUT /products/{id} with native UA → 303 → /_inp/recede   (web UA → plain redirect)
```

(See `spike/server/README.md` "Verified behaviours".)

## Sub-path + proxy robustness — **PENDING MEASUREMENT**

Run the harness behind these deployment shapes and confirm detection + flash carriage
(4 transcripts, S0.6 test criteria):

| Deployment | Check | Result |
|---|---|---|
| Root (`/`) | signal detected via prop; page not painted (native) | PENDING (mechanism OK on Linux) |
| Sub-path (`APP_URL` with `/app`) | signal still detected (prop is path-independent) | PENDING |
| Reverse proxy stripping a prefix | detection unaffected (no URL dependence) | PENDING |
| Flash carriage across the redirect | `flash.message` arrives in the `signal` payload | PENDING (prop carries it; verify natively) |

The hypothesis is that (a) is **invariant** to all of these because detection never inspects the
URL — that invariance is the whole reason for choosing it; confirm it on-device.

## Consequences

- SPEC.md §2.4 `signal` payload and §6.2 are finalised to the `inp.signal` prop shape above.
- The Laravel package (L4.4) commits a **byte-for-byte fixture** of the signal response shape
  (its AC1); this ADR's JSON is the source for that fixture.
- Path-config sub-path handling (P1.4 / L4.5) inherits the "detection is URL-independent"
  property — signal detection is decoupled from path-config matching.
- If any proxy shape *does* break flash carriage (e.g. session not shared across the redirect),
  that becomes a spec RFC before G0.

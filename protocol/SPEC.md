# INP — Inertia Native Protocol

**Version:** `1.0.0-rc.1` · **Protocol major:** `INP/1` · **Status:** Release candidate

> This document is **normative**. It is the product's stable contract; the npm adapter and the
> iOS/Android libraries are implementations of it (technical spec §2). Message names, payloads,
> and semantics here win over any implementation.
>
> **Release-candidate caveat (G0).** Several decisions are folded in from Phase-0 ADRs that
> still carry `PENDING MEASUREMENT` sections (restore fidelity — ADR-0001; re-parenting —
> ADR-0002; Android predictive back — ADR-0003). The `-rc.1` suffix reflects this: the spec is
> complete and coherent enough to pin via `INP_SPEC_REF` and build against, but **G0 sign-off
> is gated on those measurements** (see `docs/measurements/` and the per-ADR matrices). Decision
> provenance is in `docs/adr/0006-protocol-v1-decisions.md` (D1–D12).

---

## 1. Scope & terms

INP is a versioned, transport-agnostic JSON message protocol between an **adapter** running in a
WebView-hosted Inertia v3 SPA and a **native host** (iOS/Android). The native host owns
navigation, history, and the screen stack; the web side *proposes* and *reports*, never drives
the stack itself.

- **screen** — a native screen backed by a web page object, keyed by `screenId`.
- **proposal** — a requested visit the native host may accept (push/modal/replace/pop/native) or
  reject.
- **signal** — a server-driven stack operation (`recede`/`refresh`/`resume`).
- **handshake** — the `adapter.ready` ⇄ `session.configure` exchange that establishes a session.

Compatibility is anchored to the **INP major**: a native ↔ adapter pair is compatible iff they
share an INP major, verified at handshake (§11 of the technical spec).

## 2. Transport

- **iOS → web:** `webView.evaluateJavaScript("window.__INP__.receive(<json>)")`.
  **web → iOS:** `window.webkit.messageHandlers.inp.postMessage(json)` (`WKScriptMessageHandler`).
- **Android → web:** `webView.evaluateJavascript("window.__INP__.receive(<json>)", null)`.
  **web → Android:** **preferred** `WebViewCompat.addWebMessageListener` (origin-scoped, main-
  thread delivery); **fallback** `InpChannel.postMessage(json)` via `@JavascriptInterface` at
  minSdk 26 (ADR-0004 / D8). The two are wire-identical. The adapter selects its egress channel
  from `session.configure.transport` (§2.3) — defaulting to `InpChannel` when unspecified.
- The adapter bootstrap + handshake constants are injected as a document-start user script
  (iOS `WKUserScript`; Android `addDocumentStartJavaScript`, with a documented fallback for old
  WebView providers — see RFC note below).
- Messages are injected/accepted **only on first-party origins** (§9 of the technical spec).
  Messages from other origins or sub-frames are dropped.

> **Open RFC (Android, from S0.5):** the JS object name for the `WebMessageListener` egress and
> the doc-start ordering guarantee on old WebView providers are flagged as spec RFC issues to
> resolve before the final `1.0.0`. `rc.1` ships with `transport` selection in
> `session.configure` as the forward-compatible hook.

## 3. Envelope (D1, D2)

```json
{
  "inp": 1,
  "id": "9b2c1f7e-3a4d-4c2b-8e10-2f6a7c9d0e11",
  "replyTo": null,
  "type": "visit.propose",
  "payload": {}
}
```

| Field | Type | Notes |
|---|---|---|
| `inp` | integer | Protocol **major**. A receiver MUST ignore a message whose `inp` it cannot speak. |
| `id` | string (uuid-v4) | Unique per message. |
| `replyTo` | string \| null | The `id` this message answers, for request/response pairs. |
| `type` | string | Namespaced message type (table §5/§6). |
| `payload` | object | Type-specific; MAY contain unknown fields (ignored). |

**Forward compatibility (behavioural, not schema-enforced):**
- Unknown `type` ⇒ **ignore + debug-log**. Envelope schemas do **not** reject unknown `type`.
- Unknown payload fields ⇒ **ignore**. Payload schemas permit `additionalProperties`; a
  `--strict` mode (used only in `inp-protocol` CI) flags them.
- Breaking changes bump `inp`; the handshake negotiates (§4).

## 4. Handshake (D3, D4)

The document-start user script defines `window.__INP__` **before app JS runs** so the first
render can read it:

```json
{
  "platform": "ios",
  "appVersion": "1.4.0",
  "protocolVersions": [1],
  "supportedComponents": ["button", "form", "share", "toast"],
  "screenId": "scr_01HX...",
  "settings": { }
}
```

Then:

```
web → native   adapter.ready     { adapterVersion, inertiaVersion, protocolVersion, page }
native → web   session.configure { screenId, settings, debug, transport? }
```

- **Degraded mode:** if `adapter.ready` does not arrive within the host's timeout (default 10s),
  native treats the screen as a **plain web page** and falls back to WebView-level navigation
  interception (§8, technical spec §2.3).
- **Major mismatch (D4):** if the adapter cannot speak any of `protocolVersions` (or native
  cannot speak `adapter.ready.protocolVersion`), the adapter emits one `log{level:"error"}` and
  **hard-disables**; native shows a mismatch error screen.

`adapter.ready.page` is `{ url, component }`. `session.configure.transport` (optional) is
`"InpChannel" | "WebMessageListener"` (Android egress selection; ignored on iOS).

## 5. Web → native messages

| Type | Payload | Semantics |
|---|---|---|
| `adapter.ready` | `{adapterVersion, inertiaVersion, protocolVersion, page:{url,component}}` | Handshake (§4). |
| `visit.propose` | `{proposalId, url, method, action, options}` | Sent **instead of** an intercepted GET visit. `action`: `advance`\|`replace`. `options` ⊆ `{preserveScroll, preserveState, only, native}` — **nothing else** (no headers/body). |
| `visit.started` | `{screenId, url}` | A commanded visit began (request in flight). |
| `visit.completed` | `{screenId, url, component, title}` | Page object rendered. Native sets title, refreshes screenshot on detach, ends pull-to-refresh. |
| `visit.failed` | `{screenId, url, kind, status?}` | `kind`: `network`\|`http`\|`version`\|`non_inertia`\|`cancelled`. |
| `form.started` | `{screenId, url, method}` | Non-GET visit began. |
| `form.finished` | `{screenId, url, method}` | Non-GET visit settled. |
| `navigation.completedInPlace` | `{screenId, fromUrl, toUrl, cause}` | SPA URL changed without a proposal. `cause`: `redirect`\|`replace`\|`client`. |
| `signal` | `{name, flash?, fallbackUrl}` | `name`: `recede`\|`refresh`\|`resume`. Detected via the `inp.signal` shared prop (D9). The signal page never paints. |
| `page.restored` | `{screenId, url, ok}` | Reply to `page.restore`; `ok:false` ⇒ native commands a fresh visit. |
| `bridge.send` | `{component, event, data, callbackId?}` | Web component → native component. |
| `history.blockedPop` | `{}` | Diagnostic: a web-originated popstate was neutralised (D7). |
| `log` | `{level, message, context?}` | Forwarded to the native logger when `debug`. |

## 6. Native → web messages

| Type | Payload | Semantics |
|---|---|---|
| `session.configure` | `{screenId, settings, debug, transport?}` | Handshake reply + runtime config. |
| `visit.execute` | `{proposalId?, screenId, url, options}` | Perform the Inertia visit now (adapter sets an internal flag so its own interception passes it). |
| `page.restore` | `{screenId, scroll}` | Re-render the cached page object for `screenId` with **no network request**, then restore scroll (D5). |
| `page.refresh` | `{screenId, bypassCache}` | Force a fresh GET for the screen's URL. |
| `screen.willDetach` | `{screenId}` | Webview leaving this screen. Adapter MUST snapshot scroll, **pause media**, and **blur the active element** (D6). |
| `screen.didAttach` | `{screenId}` | Webview attached to this screen. |
| `bridge.reply` | `{callbackId, data}` | Reply to `bridge.send`. |
| `bridge.receive` | `{component, event, data}` | Native-initiated message to a web component. |
| `echo` | `{...}` | **Spike/diagnostic only** — bounce target; not part of the production contract. |

## 7. Sequences

### 7.1 Link tap (advance) — D10

```
WebView (screen A)                     Native
  user taps <Link href="/orders/7">
  router 'before' fires → cancel
  ── visit.propose {proposalId, url:/orders/7, method:get, action:advance} ──▶
                                        path config: context=default
                                        create screen B, push (animated)
                                        screen.willDetach{A} → snapshot A → cache
                                        re-parent webview → B
  ◀── screen.willDetach{A} / screen.didAttach{B} / visit.execute{B,/orders/7} ──
  router.visit('/orders/7') [internal flag set]
  ── visit.started {B} ──▶               (progress affordance)
  page rendered; cache[B] = page object
  ── visit.completed {B, component:Orders/Show, title} ──▶
                                        set nav-bar title from path config / page
```

### 7.2 Native back (restore) — D5

```
  user swipes back (B → A)
                                        pop B (native anim; A shows cached screenshot)
                                        re-parent webview → A
  ◀── screen.willDetach{B} / screen.didAttach{A} / page.restore{A, scroll:true} ──
  adapter renders cache[A] page object (no network), restores scroll
  ── page.restored {A, url, ok:true} ──▶  swap screenshot → live webview
  (cache miss / stale ⇒ ok:false)  ◀── visit.execute {A, urlA, options:{preserveScroll}} ──
```

### 7.3 Form + recede signal — D9, D11

```
  user submits edit form (PUT)
  ── form.started {A, method:PUT} ──▶
  server validates, 303 → /_inp/recede (native UA), flash carried in session
  Inertia follows redirect → page object whose shared props include inp.signal
  adapter detects inp.signal, does NOT render it
  ── form.finished {A} ── signal {name:recede, flash:{message}, fallbackUrl} ──▶
                                        pop A (or dismiss modal); show flash via toast bridge
```

## 8. Error handling (D11)

| Condition | Adapter emits | Native default (delegate-overridable) |
|---|---|---|
| Network unreachable / timeout | `visit.failed{kind:network}` | Error view + Retry (`visit.execute`). |
| HTTP 4xx/5xx (non-auth) | `visit.failed{kind:http, status}` | Error view; 404-on-push ⇒ pop + toast option. |
| 401 / 419 | `visit.failed{kind:http, status}` | `sessionDidDetectUnauthorized` ⇒ login flow ⇒ refresh. |
| 409 version mismatch | `visit.failed{kind:version}` **first**, then controlled reload | Invalidate snapshots; lazy per-screen refresh on attach. |
| Non-Inertia response | `visit.failed{kind:non_inertia}` | Degraded-mode render or error view (config flag). |
| Render-process death | n/a (native detects) | Rebuild webview; cold-boot current screen; lazy-refresh stack. |
| Adapter absent | handshake timeout | Degraded plain-web mode for that screen (§4). |

Ordering rule (D11): on 409 the adapter emits `visit.failed{kind:version}` **before** issuing
the controlled reload, so native can invalidate snapshots first.

## 9. Path-configuration schema (INP v1) — D12

```jsonc
{
  "settings": { "feature_flags": {} },
  "rules": [
    {
      "patterns": ["/new$", "/edit$"],
      "properties": {
        "context": "modal",          // default | modal
        "presentation": "default",   // default|replace|pop|refresh|none|replace_root|clear_all
        "pull_to_refresh": false,
        "title": null,               // static override (web title wins when present)
        "animated": true,
        "uri": null,                 // native destination, e.g. "app://map" (NAV-9)
        "fallback_uri": null
      }
    }
  ]
}
```

Matching: first-to-last rule accumulation, later rules override earlier for conflicting keys;
regex on path **and** query (query matching opt-out supported). Precedence:
**explicit `native` hint > matching rule > default** (technical spec §3.4). The full JSON Schema
+ a reference resolver and ≥20 matching fixtures are produced by P1.4.

## 10. Signal shared-prop shape — D9

The signal-route page object carries:

```json
{
  "inp": {
    "signal": {
      "name": "recede",
      "flash": { "message": "Saved ✓" },
      "fallbackUrl": "https://app.example.com/orders/7"
    }
  }
}
```

Detection is **URL-independent** (robust behind proxies / sub-paths, ADR-0005). On plain web no
detection runs and the redirect target renders normally.

## 11. Conformance & versioning

- Behavioural conformance fixtures (P1.3) are the cross-platform source of truth; each
  implementation vendors them read-only at the `INP_SPEC_REF` tag.
- Every inline JSON example in this document MUST validate against the JSON Schemas (P1.2). *(Note
  for P1.2: wire an extractor that validates these fenced `json`/`jsonc` blocks in CI.)*
- Semver tracks the spec; compatibility is the INP major. Spec changes land here first (RFC →
  fixtures → tag), then implementations.

## Appendix A — Worked example per message type

Every type in §5/§6 with a minimal valid envelope. (Schemas: P1.2.)

```json
{"inp":1,"id":"a1","replyTo":null,"type":"adapter.ready","payload":{"adapterVersion":"0.1.0","inertiaVersion":"3.3.1","protocolVersion":1,"page":{"url":"/products","component":"Products/Index"}}}
{"inp":1,"id":"a2","replyTo":null,"type":"visit.propose","payload":{"proposalId":"p1","url":"/products/7","method":"get","action":"advance","options":{"preserveScroll":false,"preserveState":false,"only":[],"native":null}}}
{"inp":1,"id":"a3","replyTo":null,"type":"visit.started","payload":{"screenId":"scr_B","url":"/products/7"}}
{"inp":1,"id":"a4","replyTo":null,"type":"visit.completed","payload":{"screenId":"scr_B","url":"/products/7","component":"Products/Show","title":"Pro Watch 7"}}
{"inp":1,"id":"a5","replyTo":null,"type":"visit.failed","payload":{"screenId":"scr_B","url":"/products/7","kind":"http","status":500}}
{"inp":1,"id":"a6","replyTo":null,"type":"form.started","payload":{"screenId":"scr_C","url":"/products/7","method":"PUT"}}
{"inp":1,"id":"a7","replyTo":null,"type":"form.finished","payload":{"screenId":"scr_C","url":"/products/7","method":"PUT"}}
{"inp":1,"id":"a8","replyTo":null,"type":"navigation.completedInPlace","payload":{"screenId":"scr_C","fromUrl":"/products/7/edit","toUrl":"/products/7","cause":"redirect"}}
{"inp":1,"id":"a9","replyTo":null,"type":"signal","payload":{"name":"recede","flash":{"message":"Saved ✓"},"fallbackUrl":"/products/7"}}
{"inp":1,"id":"a10","replyTo":"r1","type":"page.restored","payload":{"screenId":"scr_A","url":"/products","ok":true}}
{"inp":1,"id":"a11","replyTo":null,"type":"bridge.send","payload":{"component":"share","event":"present","data":{"url":"/products/7"},"callbackId":"cb1"}}
{"inp":1,"id":"a12","replyTo":null,"type":"history.blockedPop","payload":{}}
{"inp":1,"id":"a13","replyTo":null,"type":"log","payload":{"level":"debug","message":"hello","context":{}}}
{"inp":1,"id":"b1","replyTo":"a1","type":"session.configure","payload":{"screenId":"scr_A","settings":{},"debug":true,"transport":"WebMessageListener"}}
{"inp":1,"id":"b2","replyTo":"p1","type":"visit.execute","payload":{"proposalId":"p1","screenId":"scr_B","url":"/products/7","options":{"preserveScroll":false}}}
{"inp":1,"id":"b3","replyTo":null,"type":"page.restore","payload":{"screenId":"scr_A","scroll":true}}
{"inp":1,"id":"b4","replyTo":null,"type":"page.refresh","payload":{"screenId":"scr_A","bypassCache":true}}
{"inp":1,"id":"b5","replyTo":null,"type":"screen.willDetach","payload":{"screenId":"scr_A"}}
{"inp":1,"id":"b6","replyTo":null,"type":"screen.didAttach","payload":{"screenId":"scr_B"}}
{"inp":1,"id":"b7","replyTo":"cb1","type":"bridge.reply","payload":{"callbackId":"cb1","data":{"ok":true}}}
{"inp":1,"id":"b8","replyTo":null,"type":"bridge.receive","payload":{"component":"button","event":"tapped","data":{}}}
```

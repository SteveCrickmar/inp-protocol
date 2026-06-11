# Technical Specification — Inertia Native

**Status:** Draft v0.2 — task breakdown published separately (see `03-task-breakdown.md`)
**Companion documents:** PRD v0.1 · Task Breakdown v0.1
**Decision record:**
- Option B adopted — purpose-built native libraries owning the full web↔native protocol; Hotwire Native is an architectural reference only, never a dependency.
- **Locked (11 Jun 2026):** polyrepo structure (§11); **Inertia v3 only** for first release; **React + Vue bindings together**; **iOS first, Android follows** (Android limited to two de-risking probes during the spike).

---

## 1. System overview

```
┌─────────────────────────────────────────────────────────────────┐
│ Laravel application                                             │
│  ┌──────────────┐  ┌─────────────────────────────────────────┐ │
│  │ inertia-     │  │ Inertia responses (JSON page objects)   │ │
│  │ native/      │  │ + signal routes + path-config endpoint  │ │
│  │ laravel      │  └─────────────────────────────────────────┘ │
└──┴──────────────┴───────────────▲───────────────────────────────┘
                                  │ HTTPS (cookies/session, X-Inertia)
┌─────────────────────────────────┴───────────────────────────────┐
│ Native shell (iOS: Swift / Android: Kotlin)                     │
│  ┌───────────┐ ┌──────────────┐ ┌──────────────┐ ┌───────────┐ │
│  │ Navigator │ │ PathConfig   │ │ BridgeRouter │ │ Native    │ │
│  │ (stacks,  │ │ (rules,      │ │ (components) │ │ screens   │ │
│  │ screens,  │ │ settings)    │ └──────▲───────┘ │ (SwiftUI/ │ │
│  │ snapshot  │ └──────────────┘        │         │ Compose)  │ │
│  │ cache)    │                         │         └───────────┘ │
│  └─────▲─────┘   single shared WebView │                       │
└────────┼────────────────────────────────┼──────────────────────┘
         │  INP protocol (JSON messages)  │
┌────────▼────────────────────────────────▼──────────────────────┐
│ WebView: Inertia SPA (React/Vue)                                │
│  ┌──────────────────────┐  ┌─────────────────────────────────┐ │
│  │ @inertia-native/     │  │ @inertia-native/react | /vue    │ │
│  │ adapter (core)       │  │ (hooks / composables)           │ │
│  │ · visit interception │  └─────────────────────────────────┘ │
│  │ · screen page-cache  │                                      │
│  │ · lifecycle reports  │     Inertia router + app pages       │
│  │ · bridge transport   │                                      │
│  └──────────────────────┘                                      │
└─────────────────────────────────────────────────────────────────┘
```

Core ideas, inherited from the Hotwire Native model and adapted to Inertia:

1. **One shared WebView per session/stack.** The webview is re-parented between native screens. Screens not hosting the live webview display a cached screenshot, so back-swipes and stack transitions look native and instant.
2. **Proposal-based navigation.** The web side never navigates the stack itself. It *proposes* visits to native; native consults the path configuration and decides (push, modal, replace, pop, native screen, external browser, reject). Only after a screen exists does native command the webview to actually perform the Inertia visit.
3. **Server signals.** Redirects to reserved URLs let the Laravel app manipulate the native stack (pop/refresh/resume) — the server stays in charge of flow.
4. **Bridge components.** Named JSON message channels pair a web component (hook/composable) with a registered native component.

### 1.1 The Inertia-specific deltas vs. Turbo (drives the whole design)

| Concern | Turbo (Hotwire Native) | Inertia (this project) |
|---|---|---|
| Page identity | URL → HTML document | URL → page object `{component, props, url, version}` |
| Navigation hijack point | Turbo `Session`/`Navigator` adapter API | `router.on('before')` (cancelable) + a small internal-visit flag |
| Back/restore | Snapshot cache of HTML; "restore visits" | Adapter-maintained **page-object cache keyed by screenId**; re-render without server round-trip, fall back to fresh GET |
| Forms | Full-page form visits | XHR with 303-follow inside the SPA → default to in-place render, signals for stack control |
| Asset staleness | – | `X-Inertia-Version` 409 → controlled webview reload |
| State container | Mostly per-page | Long-lived SPA: global stores persist across native screens (must be exposed/documented) |

## 2. The INP protocol (Inertia Native Protocol)

A versioned, transport-agnostic JSON message protocol. **This protocol is the product's stable contract**; native libraries and the adapter are implementations of it. Published as `protocol/SPEC.md` with a JSON Schema per message and a cross-platform conformance test suite (§10).

### 2.1 Transport

- **iOS → web:** `webView.evaluateJavaScript("window.__INP__.receive(<json>)")`. **Web → iOS:** `window.webkit.messageHandlers.inp.postMessage(json)` via `WKScriptMessageHandler`. Adapter bootstrap + handshake constants are injected as a `WKUserScript` at document start.
- **Android → web:** `webView.evaluateJavascript(...)`. **Web → Android:** `InpChannel.postMessage(json)` exposed with `@JavascriptInterface` (alternatively `WebViewCompat.addWebMessageListener` where available — decision in §12).
- Messages are only injected/accepted on first-party origins (§9).

### 2.2 Envelope

```json
{
  "inp": 1,                  // protocol major version
  "id": "uuid-v4",           // unique message id
  "replyTo": "uuid-v4|null", // for request/response pairs
  "type": "visit.propose",   // namespaced message type
  "payload": { }
}
```

Rules: unknown `type` ⇒ ignore + debug-log (forward compatibility). Unknown payload fields ⇒ ignore. Breaking changes bump `inp`; the handshake (§2.3) negotiates.

### 2.3 Handshake

On first page load, the injected user script defines `window.__INP__` with `{platform, appVersion, protocolVersions: [1], supportedComponents: [...], screenId, settings}` **before** app JS runs (so even the first render can read it). When the adapter initialises it sends:

```
web → native   adapter.ready { adapterVersion, inertiaVersion, protocolVersion: 1, page: {url, component} }
native → web   session.configure { screenId, settings, debug }
```

If `adapter.ready` never arrives within a timeout (page without the adapter, e.g. an error page or third-party page that slipped through), native treats the screen as a **plain web page**: navigation falls back to intercepting `decidePolicyFor` / `shouldOverrideUrlLoading` at the WebView level (degraded mode, still usable).

### 2.4 Web → native messages

| Type | Payload | Semantics |
|---|---|---|
| `adapter.ready` | see §2.3 | Handshake. |
| `visit.propose` | `{proposalId, url, method, action, options}` | Sent **instead of** performing an intercepted Inertia GET visit. `action`: `advance` \| `replace` (mirrors Inertia `replace: true` or adapter heuristics §3.3). `options` carries relevant Inertia visit options (`preserveScroll`, `preserveState`, `only`, custom `native` hints — see §3.4). |
| `visit.started` | `{screenId, url}` | A commanded visit began (request in flight). Native may show progress. |
| `visit.completed` | `{screenId, url, component, title}` | Page object received & rendered. Native sets title, takes/refreshes screenshot when webview detaches, ends pull-to-refresh. |
| `visit.failed` | `{screenId, url, kind, status?}` | `kind`: `network` \| `http` \| `version` \| `non_inertia` \| `cancelled`. Native shows error view / runs auth hook (§7). |
| `form.started` / `form.finished` | `{screenId, url, method}` | Non-GET visit lifecycle (progress UI, double-tap guards). |
| `navigation.completedInPlace` | `{screenId, fromUrl, toUrl, cause}` | The SPA's URL changed *without* a proposal (e.g. redirect follow after a form, `router.replace`). Native updates the screen's bound URL + path-config-derived chrome. `cause`: `redirect` \| `replace` \| `client`. |
| `signal` | `{name: "recede"\|"refresh"\|"resume", flash?: {...}, fallbackUrl}` | Adapter detected a signal route response (§6.2). Native manipulates the stack; never renders the signal page. |
| `page.restored` | `{screenId, url, ok}` | Reply to `page.restore`; `ok:false` ⇒ native should command a fresh visit. |
| `bridge.send` | `{component, event, data, callbackId?}` | Web component → native component message. |
| `history.blockedPop` | `{}` | Diagnostics: a web-originated popstate occurred and was neutralised (§3.5). |
| `log` | `{level, message, context}` | Forwarded to native logger when debug enabled. |

### 2.5 Native → web messages

| Type | Payload | Semantics |
|---|---|---|
| `session.configure` | `{screenId, settings, debug}` | Handshake reply + runtime config updates. |
| `visit.execute` | `{proposalId?, screenId, url, options}` | Perform the Inertia visit now (adapter sets an internal flag so its own interception lets it through). Issued after a screen was created/selected for an accepted proposal, after pull-to-refresh, retry, deep link, or cold boot. |
| `page.restore` | `{screenId, scroll: true}` | Webview was re-parented onto an existing screen (back-nav). Adapter re-renders the cached page object for `screenId` and restores scroll (§3.2). |
| `page.refresh` | `{screenId, bypassCache: true}` | Force fresh GET for the screen's URL (signal `refresh`, pull-to-refresh). |
| `screen.willDetach` / `screen.didAttach` | `{screenId}` | Webview re-parenting lifecycle; adapter snapshots scroll positions on `willDetach` and pauses media. |
| `bridge.reply` | `{callbackId, data}` | Native reply to `bridge.send`. |
| `bridge.receive` | `{component, event, data}` | Native-initiated message to a web component (e.g. nav-bar button tapped). |
| `historyLocation.execute` | reserved | Future: server-driven stack ops beyond the three signals. |

### 2.6 Sequence — link tap (advance)

```
WebView (screen A)                    Native
  user taps <Link href="/orders/7">
  router 'before' fires → cancel
  ── visit.propose {url:/orders/7, action:advance} ──▶
                                       path config: context=default
                                       create screen B, push (animated)
                                       screenshot A → cache, re-parent webview → B
  ◀── screen.didAttach {B} ── visit.execute {B, /orders/7} ──
  router.visit('/orders/7') [flagged internal]
  ── visit.started {B} ──▶              (progress affordance)
  page object rendered; cache[B] = page
  ── visit.completed {B, component:Orders/Show, title} ──▶
                                       set nav-bar title from path config/page
```

### 2.7 Sequence — native back

```
  user swipes back (B → A)
                                       pop B (native anim, A shows cached screenshot)
                                       re-parent webview → A
  ◀── screen.willDetach {B} / screen.didAttach {A} / page.restore {A}
  adapter renders cache[A] page object, restores scroll
  ── page.restored {A, ok:true} ──▶     swap screenshot → live webview
  (cache miss / stale ⇒ ok:false)  ◀── visit.execute {A, urlA, options:{preserveScroll}} ──
```

## 3. `@inertia-native/adapter` (npm, core)

TypeScript, zero runtime deps beyond Inertia. Peer-depends on `@inertiajs/core` **v3.x only** (locked decision; v2 support deliberately dropped — v3 is current and dual-major support doubles the contract-test surface).

### 3.1 Initialisation & detection

```ts
import { initInertiaNative } from '@inertia-native/adapter'
initInertiaNative({ /* options */ })   // no-op when window.__INP__ absent
```

Detection = presence of the injected `window.__INP__` object (authoritative) ; the server independently detects via User-Agent (§6.1). On the web, every API degrades gracefully (`isNative === false`, bridge `supported === false`), so one codebase runs everywhere.

### 3.2 Screen page-cache & restore

- `Map<screenId, ScreenEntry>` where `ScreenEntry = { url, page: PageObject, scrollPositions, componentState?: opaque, cachedAt }`.
- Populated on every `visit.completed` and updated on partial reloads/prop merges.
- `page.restore` ⇒ render `entry.page` through the Inertia client's page-swap mechanism **without a network request**, then restore scroll.
- **Implementation risk (top item for M0):** Inertia does not publicly expose "set current page from object". Strategies, in order of preference: (a) `router.restore`-style history navigation if usable; (b) the documented client `setPage`/`swapComponent` seams used by SSR/hydration; (c) upstream PR adding a small public hook; (d) last-resort fallback `visit.execute` with `preserveScroll` (correct but costs a request). The adapter isolates this behind `PageRenderer` so the strategy can change without protocol impact.
- Staleness: entries older than `staleAfter` (default 5 min, path-config overridable) restore instantly **then** trigger a background partial reload (`only: []` full refresh prop merge) — stale-while-revalidate for screens.

### 3.3 Visit interception rules

In `router.on('before')` (cancelable):

1. Internal flag set (visit came from `visit.execute`) ⇒ allow.
2. `method !== 'GET'` ⇒ allow (forms run in place; lifecycle reported via `form.*`; post-redirect handled by §3.4/§6.2).
3. Prefetch/poll/partial-only visits (`only` present and URL unchanged) ⇒ allow (they refresh the current screen's cache entry).
4. External origin ⇒ propose with `action: external` is **not** sent; instead the adapter allows the navigation and native's WebView policy layer intercepts and opens the in-app browser (single enforcement point).
5. Otherwise ⇒ `event.preventDefault()`, send `visit.propose`. Heuristics matching Hotwire defaults: target URL == current URL ⇒ `action: replace`; target == previous screen's URL ⇒ native treats as pop.

### 3.4 Post-form navigation & the `native` visit option

After a non-GET visit, Inertia follows the redirect internally and renders the new page on the **current** screen. The adapter reports `navigation.completedInPlace {cause:'redirect'}` so native rebinds the screen's URL/title. When stack changes are wanted, the server uses signal routes (§6.2) — the canonical, Hotwire-proven pattern. Additionally, a client-side escape hatch is supported:

```ts
router.visit(url, { native: { action: 'advance' | 'replace' | 'modal' | 'pop' } })
// React: <Link href="/x" native={{ action: 'modal' }}>
```

`native` hints are carried in `visit.propose.options.native` and win over path-config rule properties of lower specificity (precedence: explicit hint > matching rule > defaults).

### 3.5 History discipline

Inside the shell, **native owns history**. The adapter neutralises web history as a navigation surface: commanded visits use `replace`-style history so the webview's history depth stays ~1 per screen; web-originated `popstate` is detected, suppressed (re-push current state), and reported via `history.blockedPop`. Android hardware back is handled natively (pop stack / dismiss modal / exit), never by the WebView's `goBack()`. (Note: browsers can't cancel `popstate` — the re-push approach is the standard workaround; validate thoroughly on Android WebView during M0.)

### 3.6 Bridge core + framework bindings

```ts
// core
bridge.send(component, event, data?, callback?)        // → bridge.send / bridge.reply
bridge.on(component, event, handler)                    // ← bridge.receive
bridge.isSupported(component): boolean                  // from handshake supportedComponents

// @inertia-native/react
const { supported, send, on } = useBridgeComponent('button', {
  connect: { title: 'Save' },                 // auto-send on mount (declarative)
  onReceive: { tapped: () => formRef.current?.submit() },
})                                            // auto disconnect message on unmount

// @inertia-native/vue — same shape as a composable
```

Wire format goal (PRD BRG-4): payload structure `{component, event, data}` mirrors Hotwire Native's bridge JSON so native-side component implementations port with renamed registration only. Components declare lifecycle events `connect`/`disconnect` by convention; the binding layer emits them automatically.

## 4. `inertia-native-ios` (Swift Package)

Targets iOS 16+ (proposal), Swift 5.9+, no third-party dependencies.

- **`Navigator`** — owns a main `UINavigationController` and a modal stack; entry point `navigator.start(url:)`; routes accepted proposals; mirrors Hotwire Native's two-stack (default/modal) model.
- **`NavigatorDelegate`** — `func handle(proposal: VisitProposal) -> ProposalResult` with `.accept`, `.acceptCustom(UIViewController)` (fully native screens), `.reject`; plus `func handle(error:for:)`, `func didReceiveAuthChallenge`, `func sessionDidDetectUnauthorized(screen:)`.
- **`Session`** — owns the single `WKWebView` (configured user scripts, `inp` message handler, custom UA `"<AppPrefix>; Inertia Native iOS/<libVersion>; INP/1"`), encodes/decodes INP messages, exposes typed async APIs (`execute(visit:)`, `restore(screenId:)`).
- **`ScreenViewController`** (`Visitable`-equivalent) — container view that hosts either the live webview or its cached screenshot; pull-to-refresh; activity indicator; error view with retry.
- **`SnapshotCache`** — screenshot per screenId (memory + disk eviction); invalidated on `page.refresh`, signals, and 409 reloads.
- **`PathConfiguration`** — sources `[.file(URL), .server(URL)]`; merge semantics: later sources override; cached `ETag`-aware fetch; exposes `properties(for url: URL)` and `settings`.
- **`BridgeRegistry` / `BridgeComponent`** — `Hotwire.registerBridgeComponents`-style registration: `Inp.registerComponents([ButtonComponent.self, ...])`; base class delivers `onReceive(message:)`, `reply(to:with:)`, screen lifecycle (`onScreenAppear/Disappear`).
- **WebView policy layer** — `WKNavigationDelegate` catches non-INP navigations: external domains → `SFSafariViewController`; same-domain full navigations (degraded mode / non-Inertia pages) → treated as advance proposals at the native level; downloads/tel/mailto handed to the system.
- **Process recovery** — `webViewWebContentProcessDidTerminate` ⇒ recreate webview, cold-boot current screen URL, restore stack representation (URLs survive; page objects are refetched lazily per screen on attach).

## 5. `inertia-native-android` (Kotlin / AndroidX)

Targets minSdk 26 (proposal), Kotlin 1.9+, AndroidX Navigation + Fragments (matching Hotwire Native Android's proven shape).

- **`NavigatorHost`** (Fragment) + **`Navigator`** — manages a nav graph of `ScreenFragment`s sharing one `WebView`; modal presentation via a dialog/bottom-sheet destination or second nav graph (M0 decision).
- **`Session`** — WebView setup (`@JavascriptInterface InpChannel` or `WebMessageListener`), UA `"<AppPrefix>; Inertia Native Android/<v>; INP/1"`, message codec, main-thread marshalling (JS interface callbacks arrive on a background thread).
- **`ScreenFragment`** — webview/screenshot container, swipe-refresh, error/retry view, transition animations matching platform defaults.
- **`SnapshotCache`**, **`PathConfiguration`**, **`BridgeRegistry`** — mirrors of the iOS components; identical JSON handling (shared conformance tests, §10).
- **Back handling** — `OnBackPressedCallback` routes hardware/gesture back through the Navigator (pop screen / dismiss modal / forward to system).
- **Recovery** — `onRenderProcessGone` ⇒ rebuild WebView and cold-boot, same policy as iOS.

## 6. `inertia-native/laravel` (Composer)

### 6.1 Detection & shared props

- Middleware `DetectInertiaNative` parses the UA marker; registers request macros `inertiaNative(): bool`, `inertiaNativePlatform(): ?string ('ios'|'android')`, `inertiaNativeAppVersion(): ?string`, `inpVersion(): ?int`.
- Documented snippet for `HandleInertiaRequests::share()` (or auto-registered via the package's own middleware) exposes:

```php
'native' => fn (Request $r) => [
    'enabled'  => $r->inertiaNative(),
    'platform' => $r->inertiaNativePlatform(),
    'appVersion' => $r->inertiaNativeAppVersion(),
],
```

- Blade directives for the root template: `@inertiaNative ... @endInertiaNative` (e.g. omit cookie banners, web nav chrome).

### 6.2 Signal routes & response helpers

```php
// routes (one-liner, prefix configurable, default /_inp)
Route::inertiaNative();   // registers /_inp/recede, /_inp/refresh, /_inp/resume

// helpers (mirror turbo-laravel / turbo-rails semantics)
return recede_or_redirect(route('trays.show', $tray));   // native: pop; web: redirect
return refresh_or_redirect();                            // native: refresh visible screen
return resume_or_redirect(route('home'));                // native: just dismiss modal
```

Mechanics: for native requests the helper redirects (303) to the signal route, carrying flash data via the session as normal; the signal route responds with a minimal Inertia page whose shared props include `inp.signal` — the adapter recognises it, emits `signal {name, flash, fallbackUrl}` to native, and never renders it. Web requests get the plain redirect. (Implementation detail to validate in M0: signal detection via a reserved shared prop is more robust than URL sniffing across proxies/subdirectories.)

### 6.3 Path configuration authoring & serving

- `config/inertia-native.php` defines `settings` and `rules` in PHP (route helpers usable):

```php
'rules' => [
    ['patterns' => ['/login$', '/register$'], 'properties' => ['context' => 'modal']],
    ['patterns' => ['/settings/native$'],     'properties' => ['uri' => 'app://settings']],
    ['patterns' => ['.*'],                    'properties' => ['pull_to_refresh' => true]],
],
```

- Served at `GET /_inp/path-configuration?platform=ios&app_version=1.4.0` (ETag, cacheable). Per-platform/per-version overrides supported (`rules_ios`, version constraints) so breaking app releases can pin older config — same versioning discipline Hotwire recommends.
- `php artisan inertia-native:path-config {platform}` exports the JSON for bundling into the shell at build time (the local copy guarantees correct first launch offline of config).

### 6.4 Scaffolding & testing

- `php artisan inertia-native:install {ios|android}` — generates a shell project from stubs (app name, bundle id, base URL, bundled path config, app icons placeholder), pinned to a released native-library version.
- Test helpers: `$this->asInertiaNative('ios', appVersion: '1.2.0')->get('/orders')`; assertion sugar `assertRecedeSignal()`, `assertRefreshSignal()`.

### 6.5 Supporting concerns

- **Auth:** standard Laravel session cookies inside the webview (no token layer). Recommend long-lived "remember" sessions for app contexts; document Sanctum-free happy path. 401/419 ⇒ adapter `visit.failed {kind:'http', status}` ⇒ native auth hook (present login URL modally; on success, refresh originating screen).
- **Asset version (409):** Inertia's version-mismatch normally forces `window.location` reload. Adapter intercepts, performs a controlled reload of the current screen URL, and notifies native to invalidate all snapshots (`visit.failed {kind:'version'}` → native commands per-screen lazy refresh on attach).
- **Flash → native toast:** signal payloads include flashed messages; a `toast` bridge component renders them natively; falls back to the app's web toast system otherwise.

## 7. Path configuration schema (INP v1)

```json
{
  "settings": { "feature_flags": { } },
  "rules": [
    { "patterns": ["/new$", "/edit$"],
      "properties": {
        "context": "modal",          // default | modal
        "presentation": "default",   // default|replace|pop|refresh|none|replace_root|clear_all
        "pull_to_refresh": false,
        "title": null,               // static title override (web title wins when present)
        "animated": true,
        "uri": null,                 // native destination, e.g. "app://map" (NAV-9)
        "fallback_uri": null
      } }
  ]
}
```

Matching: first-to-last rule accumulation with later rules overriding earlier ones for conflicting keys (document clearly; mirror Hotwire's regex-on-path-and-query approach, with an opt-out for query matching).

## 8. Error handling matrix

| Condition | Adapter emits | Native behaviour (default, overridable via delegate) |
|---|---|---|
| Network unreachable / timeout | `visit.failed{kind:network}` | Error view on the affected screen with Retry (`visit.execute`). |
| HTTP 4xx/5xx (non-auth) | `visit.failed{kind:http,status}` | Error view; 404 on push ⇒ pop + toast option. |
| 401 / 419 | `visit.failed{kind:http,status}` | `sessionDidDetectUnauthorized` hook ⇒ login flow, then refresh. |
| 409 version mismatch | `visit.failed{kind:version}` | Invalidate snapshots; controlled reload (§6.5). |
| Non-Inertia response (HTML/redirect loop) | `visit.failed{kind:non_inertia}` | Degraded-mode render of that URL in webview, or error view (config flag). |
| Render process death | n/a (native detects) | Rebuild webview, cold boot current screen, lazy-refresh stack. |
| Adapter absent on page | handshake timeout | Degraded plain-web mode for that screen (§2.3). |

## 9. Security considerations

- JS message handlers and user scripts attach **only** for the configured first-party origin(s); messages from other origins/frames are dropped. The in-app browser (external links) is a separate, bridge-less web context.
- Native validates every inbound message against JSON Schemas; malformed ⇒ drop + log. Treat all webview input as untrusted (the page could be compromised via XSS): bridge components must never execute dynamic code or open privileged actions without their own validation (e.g. file paths, URLs restricted to first-party).
- Path configuration fetched over HTTPS only; remote config cannot grant new native capabilities, only toggle declared behaviours (rules can't register message handlers).
- Cookies: rely on platform cookie stores; document `SameSite`/`Secure` requirements; no tokens persisted by the libraries themselves. Secure-storage bridge component (P2) uses Keychain/Keystore.
- No secrets in path configuration or `settings` (explicit docs warning); UA string carries no user identifiers.
- ATS / cleartext: dev-mode instructions for local HTTP (`localhost`/LAN) with explicit debug-only exceptions.

## 10. Testing & quality strategy

- **Protocol conformance suite:** language-neutral fixture set (JSON in / expected behaviour out) executed by adapter unit tests (Vitest), iOS (XCTest), Android (JUnit/Robolectric). The spec repo is the source of truth; CI in each implementation pins a spec version.
- **Adapter:** unit tests against a mocked Inertia router (Inertia v3.x); contract tests that fail loudly if relied-upon Inertia seams change.
- **Native:** UI tests (XCUITest / Espresso) against the demo Laravel app served by a test container: push/pop/restore, modal+signals, error injection (proxy faults), 409 flow, process-kill recovery.
- **Demo app** (own repo, `inertia-native-demo`): a single Laravel app with switchable React and Vue front ends exercising every PRD requirement; doubles as the docs example and the app-store submission guinea pig.
- **Device matrix:** iOS latest-2 majors on simulator + 1 physical; Android API 26/29/33/35 emulators + 2 physical OEMs (Samsung + Pixel).

## 11. Repositories, packaging, versioning

- Polyrepo (locked): `inp-protocol` (spec + schemas + conformance fixtures), `inertia-native-adapter` (npm workspaces: core/react/vue), `inertia-native-ios`, `inertia-native-android`, `inertia-native-laravel`, `inertia-native-demo` (demo Laravel app + error-injection harness), `docs`.
- Cross-repo coordination: every implementation repo pins a spec version via an `INP_SPEC_REF` (git tag of `inp-protocol`) checked in CI; fixtures are vendored at that ref, never hand-edited.
- Semver everywhere; **compatibility is anchored to the INP major**: any release advertises `INP/1`; native ↔ adapter pairs are compatible iff they share an INP major (handshake-verified, with a clear native-side error screen on mismatch).
- Release discipline: protocol changes land in `inp-protocol` first (RFC + fixtures), then implementations.

## 12. M0 spike — exit criteria & open technical decisions

The spike (iOS + adapter against the demo app, **plus two deliberately small Android probes** — items 3 and 5 — so Android risks shape the protocol before it freezes) must answer:

1. **Restore fidelity:** can a cached page object be re-rendered via public/stable Inertia seams (§3.2) with acceptable fidelity (scroll, local component state expectations documented)? Which strategy (a–d) wins on Inertia v3?
2. **Webview re-parenting under a live SPA:** any React/Vue issues when the `WKWebView` moves between view hierarchies mid-lifecycle (media, focus, IME, fixed-position elements)?
3. **History neutralisation on Android WebView:** does the popstate re-push approach hold across gesture nav and predictive back?
4. **Signal detection mechanism:** reserved shared prop vs. reserved route — confirm robustness behind proxies/sub-path deployments.
5. **Android message channel:** `@JavascriptInterface` vs `WebMessageListener` (origin-scoped, but API-level/WebView-version constraints).
6. **Modal stack model on Android:** dialog destination vs. second navigator.
7. Measure: cold start → first interactive screen; push → live page p50/p95 on mid-tier Android.

Failure of (1) at acceptable fidelity downgrades NAV-2 to "restore = preserveScroll re-fetch" for v1 and triggers an upstream conversation with Inertia maintainers — this is the single highest-leverage technical risk and the reason M0 precedes all scheduling.

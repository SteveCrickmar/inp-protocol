# PRD — "Inertia Native" (working title)

**Status:** Draft v0.1 — for refinement
**Owner:** TBC
**Last updated:** 11 June 2026

---

## 1. Summary

Inertia Native is an open-source toolkit that lets developers ship native iOS and Android apps powered by an existing Laravel + Inertia.js application. It replicates the Hotwire Native model for the Laravel/Inertia stack: the server-rendered-SPA remains the single source of truth for screens, business logic, and data, while thin native shells provide real native navigation (stacks, modals, gestures, transitions), access to platform APIs via bridge components, and the ability to mix in fully native screens where fidelity demands it.

Unlike Hotwire Native, which depends on Turbo and is maintained by 37signals, Inertia Native owns its entire web↔native protocol end-to-end (Decision: **Option B** — custom native libraries modelled on, but not depending on, Hotwire Native). This gives the project full control over protocol evolution and lets the protocol be designed around Inertia's concepts (page objects, props, partial reloads, non-GET visits) rather than Turbo's HTML-over-the-wire model.

## 2. Background & problem

Laravel + Inertia teams have no good "wrap my existing app" path to the app stores:

- **Hotwire Native** requires Turbo Drive intercepting navigation. Inertia apps don't (and can't sensibly) run Turbo, so the official native libraries have nothing to plug into.
- **NativePHP for Mobile** embeds a PHP runtime on the device — a fundamentally different architecture (offline-first, app runs on-device, app-store review needed for code changes). Many teams instead want the Hotwire model: *the server is the app*; deploys update the mobile experience instantly.
- **Capacitor/Cordova** expect a static, client-built SPA. Inertia is server-driven; there is no static `index.html` to bundle, and naive wrapping loses native navigation entirely (everything renders inside one webview with web-feeling transitions).
- **React Native / Flutter** mean a second codebase and a parallel API layer — exactly the cost Inertia exists to avoid.

The Rails ecosystem proves demand and viability: Hotwire Native powers 37signals' apps and dozens of production apps. The Laravel ecosystem has the server half partially mirrored (Turbo Laravel) but only for Blade/Turbo apps. Inertia — the dominant front-end approach in modern Laravel (it is the basis of the official Laravel starter kits) — has nothing.

## 3. Goals

1. **One codebase, three platforms.** An existing Laravel + Inertia (React or Vue) app can ship to web, iOS, and Android with no rewrite of pages.
2. **Native feel by default.** Screen-stack navigation, platform push/modal transitions, interactive back gestures, pull-to-refresh, cached-screenshot back navigation — all out of the box.
3. **Instant updates.** New screens and most changes ship by deploying the Laravel app; no app-store review for routine product work.
4. **Progressive enhancement.** Escape hatches at every fidelity level: per-URL behaviour via remote path configuration → bridge components for native UI/platform APIs → fully native screens for the highest-value flows.
5. **Protocol ownership.** A versioned, documented web↔native protocol controlled by this project, designed for Inertia semantics.
6. **Low ceremony.** `composer require` + `npm install` + `php artisan native:install ios android` should produce runnable shell apps pointing at the dev server in under 30 minutes.

## 4. Non-goals (v1)

- Running PHP or the app offline on the device (NativePHP's territory). The app requires connectivity; v1 ships only a native offline/error screen with retry.
- Supporting non-Inertia Laravel front ends (Blade/Livewire — Turbo Laravel + Hotwire Native already serve Blade; Livewire is out of scope).
- Supporting non-Laravel Inertia backends (Rails, Django, AdonisJS). The npm adapter and native libraries should be backend-agnostic *by design*, but only the Laravel server package is built, tested, and documented in v1.
- A React Native / Expo shell variant (possible later; the protocol should not preclude it).
- Push notification delivery infrastructure (a bridge component exposes the device token; sending is the app's concern).
- Windows/desktop targets.

## 5. Target users & personas

**P1 — The full-stack Laravel team (primary).** 1–10 devs, existing production Inertia app, little or no Swift/Kotlin experience. Wants an app-store presence without hiring mobile engineers. Success = shipped app with near-zero native code written.

**P2 — The product engineer adding native fidelity.** Comfortable copy-pasting Swift/Kotlin, wants native tab bars, share sheets, biometrics, camera. Success = bridge components are as easy as the Hotwire Native ones, with React hooks / Vue composables instead of Stimulus.

**P3 — The agency.** Builds many client apps on the Laravel+Inertia stack; wants a repeatable, supported path to "and we'll do you an app too."

## 6. User stories & requirements

Priorities: **P0** = MVP (must ship in first usable release), **P1** = v1.0, **P2** = post-1.0.

### Navigation & shell

| ID | Story | Priority |
|----|-------|----------|
| NAV-1 | As a user, tapping an Inertia `<Link>` pushes a new native screen with the platform transition; the previous screen shows a cached screenshot. | P0 |
| NAV-2 | As a user, the native back button / iOS edge-swipe pops to the previous screen and the page state (incl. scroll position) is restored. | P0 |
| NAV-3 | As a developer, navigating to the same URL replaces the current screen instead of pushing a duplicate; navigating to the previous URL pops. | P0 |
| NAV-4 | As a developer, I can mark routes (via path configuration) to present modally, replace, refresh, or clear the stack. | P0 |
| NAV-5 | As a user, links to external domains open in an in-app browser (SFSafariViewController / Custom Tabs), not the app webview. | P0 |
| NAV-6 | As a user, pull-to-refresh reloads the current screen (configurable per route). | P1 |
| NAV-7 | As a developer, I can build native tab bars where each tab hosts its own navigation stack (documented recipe + reference implementation in demo app). | P1 |
| NAV-8 | As a user, deep links / universal links open the corresponding screen in the app. | P1 |
| NAV-9 | As a developer, I can route specific URLs to fully native screens (SwiftUI/Compose) registered against the path configuration. | P1 |

### Path configuration

| ID | Story | Priority |
|----|-------|----------|
| PC-1 | Navigation rules are defined in JSON (regex URL patterns → properties), loaded from a bundled file at launch. | P0 |
| PC-2 | A remote path configuration is fetched from the Laravel app and overrides the bundled one, so behaviour can change without an app-store release. Remote configs are cached and versioned per platform. | P1 |
| PC-3 | `settings` in the path config carry app-level flags (e.g. feature flags) readable by native code. | P1 |

### Forms, redirects & server signals

| ID | Story | Priority |
|----|-------|----------|
| FRM-1 | Inertia form submissions (POST/PUT/PATCH/DELETE + 303 redirect) work unmodified; by default the redirect target renders on the current screen (replace). | P0 |
| FRM-2 | The server can send navigation *signals* — recede (pop), refresh, resume — by redirecting native clients to special routes; web clients get a normal redirect. Helpers: `recede_or_redirect()`, `refresh_or_redirect()`, `resume_or_redirect()`. | P0 |
| FRM-3 | Flash messages survive signal redirects and can be displayed as native toasts (bridge component) or web toasts. | P1 |

### Server-side integration (Laravel package)

| ID | Story | Priority |
|----|-------|----------|
| SRV-1 | The server detects native clients via User-Agent: `$request->inertiaNative()`, `$request->inertiaNativePlatform()` macros + middleware. | P0 |
| SRV-2 | A `native` shared prop (enabled, platform, app version, supported bridge components) is available to every page component. | P0 |
| SRV-3 | Signal routes are registerable with one line: `Route::inertiaNative()`. | P0 |
| SRV-4 | Path configuration is authored in `config/inertia-native.php` (or published JSON) and served per-platform/per-version from a route. | P1 |
| SRV-5 | Test helpers: `$this->asInertiaNative('ios')->get(...)`. | P1 |
| SRV-6 | `php artisan inertia-native:install {ios,android}` scaffolds shell projects preconfigured with app name, URL, and bundled path configuration. | P1 |

### Bridge components

| ID | Story | Priority |
|----|-------|----------|
| BRG-1 | A web component can exchange JSON messages with a registered native component of the same name (send/receive/reply with callbacks). | P0 |
| BRG-2 | React hook (`useBridgeComponent`) and Vue composable (`useBridgeComponent`) provide idiomatic access, including a `supported` flag for graceful degradation on the web and on older app versions. | P0 |
| BRG-3 | Reference components ship with v1: **button** (native nav-bar button), **form** (native submit button + progress), **share** (native share sheet), **toast**. | P1 |
| BRG-4 | The native bridge message format is structurally compatible with Hotwire Native's bridge so existing Swift/Kotlin component knowledge (and most code) transfers with minimal change. | P1 |
| BRG-5 | Additional components (haptics, secure storage, biometrics, camera/barcode, push token, review prompt, theme) as a separate "components" package. | P2 |

### Errors, auth & resilience

| ID | Story | Priority |
|----|-------|----------|
| ERR-1 | Network failures and HTTP errors show a native error view with retry; the web stack never shows a browser error page. | P0 |
| ERR-2 | 401/419 responses invoke a delegate hook so apps can present a (web or native) login flow, then resume. | P0 |
| ERR-3 | Inertia asset-version changes (409) are handled transparently: the webview reloads fresh assets without corrupting the native stack. | P0 |
| ERR-4 | WebView render-process termination recovers automatically (recreate webview, restore current screen). | P1 |

### Developer experience

| ID | Story | Priority |
|----|-------|----------|
| DX-1 | A demo Laravel app (React + Vue variants) exercises every feature; used for manual QA and docs screenshots. | P0 |
| DX-2 | Debug logging on both sides of the bridge, toggleable. | P0 |
| DX-3 | Documentation site covering: quick start, navigation, path configuration, bridge components, native screens, auth, app-store submission guidance. | P1 |
| DX-4 | Protocol reference is published and versioned (so others can build alternative shells, e.g. React Native). | P1 |

## 7. Deliverables (the four artefacts)

1. **`@inertia-native/adapter`** (npm) — framework-agnostic core: native detection, visit interception, page-stack cache & restore, lifecycle reporting, bridge transport. Plus `@inertia-native/react` and `@inertia-native/vue` bindings.
2. **`inertia-native-ios`** (Swift Package) — Navigator, screen stack, shared WKWebView + screenshot cache, path configuration, bridge, error views. Modelled on Hotwire Native iOS architecture; zero dependency on Turbo.
3. **`inertia-native-android`** (Maven/Gradle) — equivalent for Android (single shared WebView across fragment destinations, AndroidX Navigation).
4. **`inertia-native/laravel`** (Composer) — detection middleware & macros, shared props, signal routes & response helpers, path-config authoring/serving, scaffolding artisan commands, test helpers.

## 8. Success metrics

- **Activation:** time from `composer require` to app running on a simulator < 30 min (measured via docs walkthrough testing).
- **Adoption:** 500 GitHub stars across repos and ≥ 25 apps known in production within 12 months of v1.0 (tracked via showcase submissions).
- **Native-code-free coverage:** the demo app reaches App Store / Play Store review acceptance with < 50 lines of app-specific Swift/Kotlin.
- **Stability:** crash-free sessions > 99.5% in the demo/reference apps.
- **Community:** ≥ 5 third-party bridge components published within 6 months of the bridge API stabilising.

## 9. Competitive landscape

| | Inertia Native | Hotwire Native | NativePHP Mobile | Capacitor | React Native |
|---|---|---|---|---|---|
| Works with existing Inertia app | ✅ | ❌ (needs Turbo) | ⚠️ (re-architect to on-device) | ⚠️ (loses native nav, server-driven model awkward) | ❌ (rewrite) |
| Server-driven, instant updates | ✅ | ✅ | ❌ | ⚠️ | ❌ |
| Native navigation primitives | ✅ | ✅ | ⚠️ (webview + some native components) | ❌ | ✅ |
| Offline-first | ❌ | ❌ | ✅ | ⚠️ | ✅ |
| Second codebase | thin shell | thin shell | no, but new runtime | thin shell | full |

Positioning: **"Your Laravel deploy is your app release."** Same Inertia pages on web, iOS, and Android; native where it counts.

## 10. Release plan

- **M0 — Spike (internal):** protocol PoC on iOS only. Push/pop with screenshot cache driven by Inertia `before`-event interception; validate restore fidelity and webview re-parenting. Go/no-go on protocol draft.
- **M1 — Alpha (P0 scope, iOS + Android):** navigation, local path config, forms + signals, error/auth handling, Laravel detection/shared props, bridge core, demo app. Single "happy path" docs page. Audience: early adopters from the Laravel community.
- **M2 — Beta (P1 scope):** remote path config, reference bridge components, native screens routing, tabs recipe, deep links, scaffolding command, docs site, protocol reference v1. Public announcement.
- **M3 — v1.0:** API freeze, semver + protocol-version compatibility policy, app-store submission guide, showcase. 

(Deliberately no calendar dates until after M0 — the spike de-risks the two genuinely hard problems: back-navigation restore and webview re-parenting under Inertia.)

## 11. Risks & mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Inertia client internals offer no public "restore this page without a server round-trip" API; v2→v3 churn. | High | Build restore on documented APIs where possible; isolate all Inertia-touching code in one adapter module; pin supported Inertia versions; engage Inertia maintainers early (possible upstream hook PR). |
| Back-navigation state fidelity (scroll, component state) disappoints vs. Hotwire's mature snapshot system. | High | M0 spike is scoped to exactly this; accept "re-render page object + scroll restore" fidelity for v1 and document limits. |
| SPA state bleeds across native screens (stores, websockets, toasts render on the wrong screen). | Medium | Document patterns; expose current `screenId` to the app; bridge `toast` component renders natively. |
| App Store Guideline 4.2 (minimum functionality) rejections for thin wrappers. | Medium | Submission guide; encourage bridge components + at least platform-integrated features (share, push, etc.); reference app accepted before v1.0. |
| Maintaining two native libraries with a small team. | Medium | Keep shells deliberately thin; mirror Hotwire Native's proven architecture to avoid novel design risk; heavy protocol conformance test suite reused on both platforms. |
| Trademark/namespace: "Inertia" brand usage. | Low | Confirm naming with Inertia maintainers before public release; fallback neutral name. |
| Android WebView fragmentation (OEM/old WebView versions). | Medium | Set minSdk realistically (proposal: API 26+), test matrix in CI via emulators, feature-detect in adapter. |

## 12. Open questions

1. Final name + npm/composer/package namespaces (pending trademark check).
2. Monorepo vs. per-artefact repos (proposal in tech spec: polyrepo with shared protocol-spec repo + conformance tests).
3. License (proposal: MIT across all packages).
4. Minimum supported versions: iOS 16+? Android API 26+? Inertia v2 + v3, or v3 only?
5. Svelte bindings in v1 or post-1.0?
6. Should bridge wire format be byte-compatible with Hotwire Native (maximise reuse) or merely structurally similar (maximise freedom)? (Tech spec proposes structural compatibility with a versioned envelope.)
7. Commercial model, if any (sponsorware, paid components pack, paid scaffolding/cloud build à la NativePHP) — out of scope for this PRD but affects packaging.

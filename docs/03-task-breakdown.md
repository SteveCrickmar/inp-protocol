# Inertia Native — Task Breakdown v0.1

**Companion documents:** PRD v0.1 · Technical Spec v0.2 (the spec is normative; section references like "§2.4" point at it)
**Locked decisions:** polyrepo · Inertia v3 only · React + Vue together · iOS first, Android follows

---

## 0. How to use this document (sub-agent operating conventions)

These conventions apply to **every** task below. Tasks intentionally omit repeating them.

**OC-1 — One task, one agent run, one PR.** Each task maps to a single branch (`task/<ID>-slug`) and a single PR against the named repo's `main`. No task may modify more than one repository. If a task discovers it needs a change in another repo, it stops and files a written blocker note instead of making the change.

**OC-2 — Definition of Done (all tasks).** (a) All acceptance criteria demonstrably met; (b) all test criteria implemented and green in CI; (c) repo lint/format passes; (d) public APIs have doc comments; (e) PR description lists each acceptance criterion with evidence (test name, screenshot, or recording); (f) no decrease in repo test coverage thresholds once set by the scaffold tasks.

**OC-3 — Spec is law.** Protocol message names, payloads, and semantics come from `inp-protocol` at the ref pinned in the repo's `INP_SPEC_REF` file. If an implementation task believes the spec is wrong, it files a spec RFC issue on `inp-protocol`; it does not invent fields. Conformance fixtures are vendored read-only at the pinned ref.

**OC-4 — ADRs.** Tasks marked **[ADR]** must produce an Architecture Decision Record in `docs/adr/NNNN-title.md` (template: context → options considered → decision → consequences). Spike ADRs live in `inp-protocol/docs/adr/`.

**OC-5 — Spike code is disposable.** Phase 0 code lives in `inp-protocol/spike/` and is never imported by production packages. Its outputs are ADRs, measurements, and fixture drafts — not code.

**OC-6 — Naming placeholder.** All namespaces (`@inertia-native/*`, `inertia-native/laravel`, `InertiaNative*`) are placeholders pending the trademark check (PRD §12.1). A single rename task is scheduled before first public publish (R6.4); do not pre-emptively bikeshed names.

**OC-7 — Sizes** are human-equivalent effort guides for slicing, not commitments: **S** ≤ ½ day, **M** ½–1 day, **L** 1–3 days. Anything that grew beyond L should have been split; agents encountering scope creep stop and propose a split.

**OC-8 — Toolchain baselines.** Adapter: Node 20 LTS, TypeScript 5.x, Vitest, ESLint+Prettier, Changesets. iOS: Xcode 16+, Swift 5.10, SwiftPM, XCTest/XCUITest, SwiftFormat. Android: AGP current LTS, Kotlin 2.x, minSdk 26, JUnit5 + Robolectric + Espresso, ktlint. Laravel: PHP 8.2–8.4 matrix, Laravel 11 + 12, Pest, Orchestra Testbench, Pint. Demo: Laravel 12, Vite, React 18 + Vue 3, Inertia v3.

---

## 1. Phase & gate map

```
Phase 0  SPIKE (inp-protocol/spike) ───────────────► G0: protocol v1.0.0-rc frozen
Phase 1  PROTOCOL repo productionised ─────────────► (tags consumable)
                    │
        ┌───────────┼──────────────────┬───────────────────┐
Phase 2 ADAPTER     │   Phase 4 LARAVEL│   Phase 5 DEMO APP│   (parallel lanes)
        ▼           │                  ▼                   ▼
        G1: adapter conformance green  G3: laravel alpha   demo usable by UI tests
                    │
Phase 3 iOS ────────┴──────────────────────────────► G2: iOS alpha (manual QA on demo)
Phase 6 INTEGRATION + ALPHA RELEASE ───────────────► G4: integrated alpha shipped
Phase 7 ANDROID PORT + BETA ───────────────────────► G5: beta, both platforms
```

Parallelisation guide: after **G0**, Phases 2, 4, and 5 can run fully in parallel; Phase 3 can start its scaffold/Session tasks immediately after G0 but its Navigator tasks (I3.4+) go faster once A2.x lands (a real adapter beats the mock harness). Phase 7 starts only after G4.

**Gate criteria** (gatekeeper = human maintainer review):
- **G0:** all Phase-0 ADRs accepted; spec questions §12.1–.7 answered; `inp-protocol` tagged `v1.0.0-rc.1`; restore-fidelity decision recorded (including the NAV-2 downgrade call if strategy (d) won).
- **G1:** adapter conformance runner green on all vendored fixtures; demo app boots with adapter in web (no-op) mode with zero console errors.
- **G2:** iOS demo shell passes the manual QA script (Q6.1) and the XCUITest suite (I3.11) against `inertia-native-demo`.
- **G3:** Laravel package green across the full CI matrix; demo app consumes it (not a local copy).
- **G4:** R6.1 cross-repo pipeline green; alpha versions published; quick-start doc verified by a clean-machine walkthrough.
- **G5:** Android Espresso suite green; protocol conformance green on Android; beta published.

---

## 2. Phase 0 — Spike (repo: `inp-protocol`, directory `spike/`)

> Purpose: kill or confirm the project's hard risks **before** the protocol freezes. Every task here ends in an ADR or measurement report, not merged library code.

#### S0.1 — Spike harness · Size M · Depends: —
**Goal:** A minimal but realistic playground: a Laravel 12 + Inertia v3 + React app (5 pages: list → detail → edit-form(modal-ish) → settings → external-link page; seeded SQLite) and a bare-bones iOS app (single `WKWebView`, `inp` script message handler, `evaluateJavaScript` send path, hard-coded base URL, debug message log overlay).
**Deliverables:** `spike/server/`, `spike/ios/`, `spike/README.md` with run instructions (`composer run dev` + open Xcode project), recorded baseline of the app browsing normally inside the bare webview.
**Acceptance criteria:** (1) Fresh checkout to running-on-simulator in ≤ 15 min following the README; (2) JS→native and native→JS echo messages round-trip and appear in the overlay; (3) all five pages navigable in the webview.
**Test criteria:** No automated tests required (OC-5); README includes a 6-step manual smoke script with expected results.

#### S0.2 — Restore strategy evaluation · Size L · **[ADR]** · Depends: S0.1
**Goal:** Answer spec §12.1: can a cached Inertia v3 page object be re-rendered without a network request, and at what fidelity? Implement and compare strategies (a) history-based restore, (b) client `setPage`/hydration seams, (c) sketch of an upstream hook (patch Inertia locally to prove the shape), (d) `preserveScroll` re-fetch fallback.
**Deliverables:** ADR-0001 selecting a primary + fallback strategy; a fidelity matrix (scroll position, form input state, component local state, mounted-effects re-runs, media/iframes) per strategy; if (c) is the winner, a draft upstream issue/PR text for the Inertia maintainers.
**Acceptance criteria:** (1) Each of (a), (b), (d) demonstrably runs in the harness (screen recordings linked in ADR); (2) ADR states the chosen `PageRenderer` strategy and the exact Inertia v3 APIs/seams it touches, each tagged public/internal; (3) explicit go/no-go on PRD NAV-2 fidelity, with the downgrade wording if no-go.
**Test criteria:** A repeatable manual scenario script (scroll 1500px on list → push detail → pop) executed per strategy with results recorded in the matrix.

#### S0.3 — Webview re-parenting validation · Size M · **[ADR]** · Depends: S0.1
**Goal:** Answer §12.2: move the live `WKWebView` between two view controllers mid-SPA-lifecycle; observe React behaviour (focus/IME, fixed-position elements, video element, in-flight XHR, WebSocket).
**Deliverables:** ADR-0002 with findings, workarounds (e.g. `screen.willDetach` pre-pause behaviours the adapter must implement), and screenshot-cache timing guidance (when must the snapshot be taken to avoid white flashes).
**Acceptance criteria:** (1) Push/pop with re-parenting works with no white flash on a recorded capture; (2) the keyboard/IME, video, and in-flight-request cases are each explicitly tested and documented; (3) any required protocol additions are filed as spec RFC issues.
**Test criteria:** Manual scripted scenarios listed in the ADR appendix with pass/fail per scenario.

#### S0.4 — History neutralisation (iOS) · Size S · Depends: S0.1
**Goal:** Validate §3.5 on WKWebView: commanded visits with replace-style history; web-originated `popstate` re-push; confirm webview history depth stays ~1/screen.
**Deliverables:** Findings note appended to ADR-0003 (shared with S0.5); harness code demonstrating the technique.
**Acceptance criteria:** (1) After 5 pushes and 3 pops, `history.length` within the webview matches the documented expectation; (2) a forced JS `history.back()` is neutralised and reported.
**Test criteria:** Manual script with logged message transcript attached.

#### S0.5 — Android risk probes · Size M · **[ADR]** · Depends: S0.1 (server only)
**Goal:** Answer §12.3 + §12.5 with a throwaway Android activity: (i) message channel choice — `@JavascriptInterface` vs `WebViewCompat.addWebMessageListener` (origin scoping, threading, API-level reach at minSdk 26); (ii) popstate re-push behaviour under gesture nav and predictive back.
**Deliverables:** ADR-0003 (history neutralisation, both platforms) and ADR-0004 (Android channel choice), `spike/android/` probe app.
**Acceptance criteria:** (1) Channel ADR includes a threading note (which thread JS-interface callbacks arrive on) and an origin-scoping security note; (2) predictive-back behaviour on API 34+ documented with recording; (3) protocol implications (if any) filed as spec RFC issues before G0.
**Test criteria:** Manual scripts with transcripts, as S0.4.

#### S0.6 — Signal detection mechanism · Size S · **[ADR]** · Depends: S0.1
**Goal:** Answer §12.4: reserved shared prop vs reserved route for recede/refresh/resume signals; verify behind a sub-path deployment (`/app` prefix) and a reverse proxy.
**Deliverables:** ADR-0005 with the chosen mechanism and the exact prop/route shape to encode in the spec.
**Acceptance criteria:** (1) Both mechanisms prototyped in the harness; (2) sub-path + proxy cases demonstrated; (3) flash-message carriage across the signal redirect demonstrated.
**Test criteria:** Manual transcript per mechanism per deployment shape (4 runs).

#### S0.7 — Protocol v1 freeze · Size M · **[ADR]** · Depends: S0.2–S0.6
**Goal:** Fold all ADR outcomes into the protocol document; resolve every TODO in spec §2; tag `inp-protocol v1.0.0-rc.1`.
**Deliverables:** `protocol/SPEC.md` complete (all message types, envelope, handshake, error kinds, path-config schema §7, sequence diagrams §2.6–2.7 updated); ADR-0006 recording protocol-level decisions and rejected alternatives; **measurement report** for §12.7 (cold start → first interactive; push → live page p50/p95 from the spike harness, with device/sim noted).
**Acceptance criteria:** (1) Zero unresolved TODO/TBD markers in SPEC.md; (2) every message type has a worked JSON example; (3) `v1.0.0-rc.1` tag exists; (4) G0 checklist in §1 satisfied and signed off by the maintainer.
**Test criteria:** None (document task); SPEC.md examples must be valid JSON (checked by P1.2's validator once it exists — note left for that task).

---

## 3. Phase 1 — Protocol repo productionisation (repo: `inp-protocol`)

#### P1.1 — Repo scaffold & governance · Size S · Depends: S0.7
**Goal:** Turn the repo into the coordination point: directory layout (`protocol/`, `schemas/`, `fixtures/`, `docs/adr/`, `spike/` [frozen]), CONTRIBUTING with the RFC process (issue template → discussion → spec PR → fixture PR → tag), semver-for-spec policy (§11), CODEOWNERS.
**Acceptance criteria:** (1) RFC issue template exists and references OC-3; (2) tagging/versioning policy documented incl. the `INP/1` major rule; (3) CI runs schema validation (placeholder until P1.2) and markdown lint.
**Test criteria:** CI green on a no-op PR; a dry-run RFC issue created and closed as exercise.

#### P1.2 — JSON Schemas for all messages · Size M · Depends: P1.1
**Goal:** JSON Schema (2020-12) for the envelope and every message type in SPEC.md §2.4–2.5, plus the handshake objects.
**Deliverables:** `schemas/envelope.json`, `schemas/messages/<type>.json`, a `schemas/index.json` manifest, and a tiny validator CLI (`npm run validate <file>`).
**Acceptance criteria:** (1) Every SPEC.md example validates; (2) schemas reject: unknown `type` is NOT an error at envelope level (forward-compat is behavioural, not schema — document this), missing required payload fields, wrong primitive types; (3) `additionalProperties` permitted in payloads (forward compat) but flagged by a `--strict` mode used only inside this repo's CI.
**Test criteria:** Vitest suite: ≥ 2 positive + ≥ 2 negative fixtures per message type; CI validates all SPEC.md inline examples automatically (extracted by fenced-block tag).

#### P1.3 — Conformance fixtures: behavioural scenarios · Size L · Depends: P1.2
**Goal:** The cross-platform behavioural test corpus (§10): each scenario is a YAML/JSON file describing a starting state, a sequence of inbound messages/user actions, and the expected outbound messages + terminal state. Initial corpus (≈ 25 scenarios): link-tap advance (§2.6), back-restore happy path + cache-miss (§2.7), replace heuristic, same-URL replace, previous-URL pop, modal context, external URL, form in-place render + `navigation.completedInPlace`, each of the three signals, each `visit.failed` kind, 409 flow, handshake timeout → degraded mode, unknown-message-type ignore, malformed-message drop.
**Acceptance criteria:** (1) Every scenario file validates against a scenario meta-schema; (2) each maps to ≥ 1 spec section reference; (3) a `fixtures/README.md` defines the runner contract (what an implementation's conformance runner must provide: clock, transport stub, assertion semantics, ordering rules for async messages).
**Test criteria:** Meta-schema validation in CI; scenario count and coverage table (message type × ≥1 scenario) generated and checked in CI.

#### P1.4 — Path-configuration schema + matching fixtures · Size M · Depends: P1.2
**Goal:** Schema for §7 (settings/rules/properties incl. `context`, `presentation`, `pull_to_refresh`, `title`, `animated`, `uri`, `fallback_uri`), plus a fixture table of (rules, URL) → resolved-properties cases covering: rule accumulation/override order, query-string matching + opt-out, sub-path deployments, precedence vs `native` visit hints (§3.4).
**Acceptance criteria:** (1) ≥ 20 matching cases incl. at least 4 adversarial regex cases (catastrophic-backtracking-safe guidance documented); (2) precedence (hint > rule > default) explicitly fixtured; (3) merge semantics for multiple sources (file + server) fixtured.
**Test criteria:** Cases executable by a reference resolver written in TS inside this repo (the resolver is *itself* spec-illustrative, ≤ 200 LOC, fully unit-tested) — implementations must match its outputs.

#### P1.5 — `@inertia-native/protocol` types package · Size S · Depends: P1.2
**Goal:** Generate TypeScript types from the schemas and publish (initially to a private registry/GitHub Packages until OC-6 rename) a package exporting types + raw schema JSON + the message-type constants.
**Acceptance criteria:** (1) `npm run build` is reproducible (codegen committed or deterministic); (2) types compile under `strict`; (3) package version tracks the spec tag it was generated from and embeds it (`PROTOCOL_SPEC_REF` export).
**Test criteria:** Type-level tests (tsd or vitest + expect-type) asserting representative message shapes; CI fails if schemas changed but codegen wasn't re-run.

---

## 4. Phase 2 — Adapter (repo: `inertia-native-adapter`, npm workspaces `core` / `react` / `vue`)

#### A2.1 — Repo scaffold · Size M · Depends: G0
**Goal:** Workspaces (`packages/core`, `packages/react`, `packages/vue`, `packages/conformance` [private]), TS strict, Vitest + jsdom, ESLint/Prettier, Changesets, CI (lint/test/build matrix Node 20+22), `INP_SPEC_REF` file + CI step that vendors fixtures from `inp-protocol` at that ref into `vendor/inp-fixtures/` (read-only).
**Acceptance criteria:** (1) `npm test`/`lint`/`build` green from clean clone; (2) fixture-vendoring step verifiable (`npm run sync-fixtures` is a no-op when in sync, CI fails when drifted); (3) coverage thresholds set (core ≥ 90% lines) and enforced.
**Test criteria:** One placeholder test per package proving the harness; CI run linked in PR.

#### A2.2 — Detection & handshake module · Size M · Depends: A2.1
**Goal:** §2.3 + §3.1: read `window.__INP__`, expose `isNative`, `platform`, `screenId`, `settings`; send `adapter.ready`; apply `session.configure`; expose a typed `onConfigure` hook; full web no-op behaviour when `__INP__` absent.
**Acceptance criteria:** (1) `initInertiaNative()` on plain web: zero messages sent, zero globals mutated beyond a namespaced object, all public getters return inert defaults; (2) handshake sends within one microtask of init with correct payload incl. `adapterVersion`, `inertiaVersion`, `protocolVersion`; (3) `session.configure` updates `screenId` and `settings` and re-emits to subscribers; (4) protocol-major mismatch from native triggers a single `log{level:error}` and hard-disables the adapter (documented behaviour).
**Test criteria:** Unit tests for all four ACs using the test transport (A2.3 stub acceptable via inversion: this task defines the `Transport` interface); jsdom test proving no-op mode leaves Inertia events un-hooked.

#### A2.3 — Message codec & transports · Size M · Depends: A2.2
**Goal:** Envelope encode/decode with validation against vendored schemas (dev builds) / lightweight structural checks (prod builds); transports: iOS (`webkit.messageHandlers.inp`), Android (`InpChannel.postMessage` *and* `WebMessageListener` port per ADR-0004), and `TestTransport` (records sent, scripts received); `window.__INP__.receive` wiring; reply/`replyTo` correlation with timeouts.
**Acceptance criteria:** (1) Malformed inbound JSON → dropped + `log` message, never throws into app code; (2) unknown `type` → ignored + debug log (P1.3 scenario passes); (3) request/reply correlation works incl. timeout rejection (default 10s, configurable); (4) all messages stamped `inp:1`, uuid `id`.
**Test criteria:** Unit tests incl. fuzz-ish corpus (the negative fixtures from P1.2) fed through decode; timer tests with fake clock for timeouts.

#### A2.4 — Visit interception engine · Size L · Depends: A2.3
**Goal:** §3.3 rules 1–5 implemented on Inertia v3's `router.on('before')`: internal-flag pass-through, non-GET pass-through, partial/prefetch pass-through, external-origin pass-through (native policy layer enforces), otherwise cancel + `visit.propose` with `action` heuristics (same-URL ⇒ replace) and the `native` hint carriage (§3.4).
**Acceptance criteria:** (1) Each of the five rules has a dedicated unit test; (2) proposals carry `proposalId`, full URL, method, action, and the whitelisted Inertia options (`preserveScroll`, `preserveState`, `only`, `native`) — nothing else (no headers, no body); (3) on plain web the engine is never registered; (4) double-tap a link → exactly one proposal (in-flight proposal de-dupes by URL+action until `visit.execute` or 2s).
**Test criteria:** Unit tests with a mocked Inertia router covering rules + de-dupe; a contract test that imports real `@inertiajs/core` v3 and asserts the `before` event remains cancelable and carries `visit.url`/`visit.method` (fails loudly on upstream change, per spec §10).

#### A2.5 — Screen page-cache & PageRenderer · Size L · Depends: A2.4, ADR-0001
**Goal:** §3.2: `Map<screenId, ScreenEntry>`; populate on `visit.completed`-relevant renders and prop merges; implement `page.restore` using ADR-0001's primary strategy behind a `PageRenderer` interface with the (d) re-fetch fallback; scroll snapshot on `screen.willDetach`, restore after render; `staleAfter` stale-while-revalidate.
**Acceptance criteria:** (1) Restore renders the cached page object with **no network request** (asserted via mocked fetch) when cache is fresh; (2) cache miss/stale → `page.restored{ok:false}` and renderer falls back per spec; (3) scroll restored within one frame of render (jsdom approximation: restored before the `page.restored` message is sent); (4) stale entry → instant render **then** background partial reload issued; (5) `PageRenderer` is swappable via DI (strategy isolation requirement, spec §3.2).
**Test criteria:** Unit tests for ACs 1–5; contract test pinning the specific Inertia v3 seams the chosen strategy uses (kept in one file, referenced by ADR-0001).

#### A2.6 — History discipline module · Size M · Depends: A2.4, ADR-0003
**Goal:** §3.5: replace-style history for commanded visits; popstate neutralisation (re-push) + `history.blockedPop`; Android predictive-back caveats from ADR-0003 encoded as platform-conditional behaviour.
**Acceptance criteria:** (1) After scripted push/pop sequences the synthetic history depth matches ADR-0003 expectations; (2) programmatic `history.back()` inside the page is neutralised and reported exactly once; (3) module fully inert on plain web.
**Test criteria:** jsdom history simulation tests; one karma-style real-browser smoke test (Playwright, chromium) because jsdom history is unfaithful — add Playwright as a dev-only dependency in this task.

#### A2.7 — Lifecycle reporting · Size M · Depends: A2.4
**Goal:** Emit `visit.started`, `visit.completed` (with component + `document.title`), `visit.failed` (kind classification per §8: network/http/version/non_inertia/cancelled), `form.started`/`form.finished`, `navigation.completedInPlace` (cause detection: redirect vs replace vs client).
**Acceptance criteria:** (1) Every Inertia v3 global event that should map to a message does, and a table in the module doc comments shows the mapping; (2) failure classification has a dedicated function with exhaustive unit tests incl. 0-status network errors, 503, 401, 409, and an HTML (non-Inertia) response; (3) `navigation.completedInPlace` fires for post-form redirects with `cause:'redirect'` and for `router.replace` with `cause:'replace'`.
**Test criteria:** Unit tests per row of the mapping table; fixture scenarios from P1.3 (`visit.failed` kinds, form flow) pass via the conformance runner once A2.13 lands (note dependency forward).

#### A2.8 — Signal detection & emission · Size S · Depends: A2.7, ADR-0005
**Goal:** Implement ADR-0005's mechanism; on detection emit `signal{name, flash, fallbackUrl}` and suppress rendering of the signal page (§6.2).
**Acceptance criteria:** (1) All three signals detected and emitted with flash payload; (2) the signal page never paints (no component swap observable); (3) non-native web flow untouched (no detection logic runs).
**Test criteria:** Unit tests with synthetic page objects matching ADR-0005's shape; sub-path deployment case from S0.6 reproduced as a unit test.

#### A2.9 — 409 version flow & error funnel · Size S · Depends: A2.7
**Goal:** §6.5: intercept Inertia's version-mismatch reload, perform controlled current-screen reload, emit `visit.failed{kind:'version'}` first so native invalidates snapshots.
**Acceptance criteria:** (1) Default Inertia hard-reload is prevented in native mode; (2) message ordering (failed → reload) guaranteed; (3) web mode untouched.
**Test criteria:** Unit test with mocked 409 response; contract test pinning the Inertia v3 seam used to intercept the reload.

#### A2.10 — Bridge core · Size M · Depends: A2.3
**Goal:** §3.6 core: `bridge.send` (with optional callback → `bridge.reply` correlation), `bridge.on`, `isSupported` from handshake `supportedComponents`, auto `connect`/`disconnect` lifecycle event convention, per-component message queues until handshake completes.
**Acceptance criteria:** (1) Send before handshake → queued, flushed in order after `session.configure`; (2) callback timeout surfaces as a rejected promise with component+event context; (3) `isSupported` false ⇒ `send` resolves as no-op (documented) and `on` never fires — web-safe by construction; (4) wire payload shape `{component, event, data}` matches the Hotwire-compatible structure in SPEC.md (BRG-4).
**Test criteria:** Unit tests for ACs; round-trip test against `TestTransport` scripted from P1.3 bridge scenarios.

#### A2.11 — React bindings · Size M · Depends: A2.10, A2.2
**Goal:** `@inertia-native/react`: `useInertiaNative()` (isNative/platform/settings/screenId, reactive to configure), `useBridgeComponent(name, {connect, onReceive})` with mount/unmount lifecycle, `<NativeProvider>` optional context, and a documented pattern for `native` visit hints on `<Link>` (pass-through of §3.4 option).
**Acceptance criteria:** (1) Hooks are SSR-safe (no window access at module scope; render on server returns inert values); (2) `useBridgeComponent` sends `connect` on mount with the `connect` payload, `disconnect` on unmount, and re-sends `connect` if the payload's identity changes (documented equality rule); (3) `supported` flag drives conditional rendering example in README; (4) double-mount under StrictMode does not duplicate native-visible connects (de-dupe rule documented + implemented).
**Test criteria:** @testing-library/react tests for mount/unmount/StrictMode/`onReceive` dispatch; type tests for hook signatures.

#### A2.12 — Vue bindings · Size M · Depends: A2.10, A2.2
**Goal:** `@inertia-native/vue`: `useInertiaNative()`, `useBridgeComponent()` (composables mirroring A2.11 semantics with Vue reactivity), plugin install (`app.use(InertiaNative)`).
**Acceptance criteria:** Parity with A2.11 ACs 1–3 (Vue equivalents; SSR-safety via `import.meta.SSR`-safe guards); API shape documented side-by-side with React in one table.
**Test criteria:** @vue/test-utils tests mirroring A2.11's suite; a shared spec file pattern so React/Vue behavioural tests assert the same scenarios.

#### A2.13 — Conformance runner · Size L · Depends: A2.4–A2.10
**Goal:** `packages/conformance`: execute every vendored P1.3 scenario against the real core wired to `TestTransport` + mocked Inertia router + fake clock, per the runner contract in `fixtures/README.md`.
**Acceptance criteria:** (1) 100% of vendored scenarios pass or are explicitly skip-listed with a linked issue (skip list must be empty at G1); (2) runner output names scenario IDs so failures are traceable to spec sections; (3) CI job `conformance` is separate from unit tests.
**Test criteria:** The runner *is* the test; additionally one meta-test asserting the runner fails when a scenario's expected message is altered (guards against a vacuous runner).

#### A2.14 — Web no-op hardening & bundle budget · Size S · Depends: A2.11, A2.12
**Goal:** Guarantee the production-web cost of shipping the adapter: tree-shakeable entry points, side-effect flags, bundle-size budget, and a "no behaviour change on web" audit.
**Acceptance criteria:** (1) `core` adds ≤ 3 kB gzip to a web bundle when `__INP__` absent (size-limit CI check); (2) no event listeners registered on web (asserted in a Playwright test); (3) `sideEffects` correctly declared; ESM + CJS builds verified.
**Test criteria:** size-limit CI; Playwright assertion on a built demo-like page.

---

## 5. Phase 3 — iOS library (repo: `inertia-native-ios`)

#### I3.1 — Repo scaffold · Size M · Depends: G0
**Goal:** SwiftPM library + an in-repo `Harness` app target (thin shell pointing at a configurable URL); XCTest unit target; XCUITest target (wired to demo app later); SwiftFormat + CI (macOS runner: build, unit tests, format check); `INP_SPEC_REF` + fixture vendoring as in A2.1.
**Acceptance criteria:** (1) `swift build`/`swift test` green; (2) Harness app boots to a URL from an xcconfig; (3) fixture drift check in CI.
**Test criteria:** Placeholder unit test; CI link in PR.

#### I3.2 — Session: webview, codec, injection, UA · Size L · Depends: I3.1
**Goal:** §4 `Session`: owns the `WKWebView`; `WKUserScript` at document-start defining `window.__INP__` (platform, appVersion, protocolVersions, screenId, settings, supportedComponents); `inp` script message handler; `evaluateJavaScript` send path with main-actor isolation; Codable models for every message (generated-or-handwritten from schemas — handwritten acceptable with conformance cover); UA string per §4; typed async APIs `execute(visit:)`, `restore(screenId:)`, `configure(...)`.
**Acceptance criteria:** (1) Handshake completes against the spike server (manual) and against a stubbed page (automated); (2) malformed inbound message → dropped + os_log, no crash (fuzz corpus from P1.2 negatives); (3) messages only accepted from main frame + first-party origin list (§9) — cross-origin iframe message demonstrably ignored; (4) UA exactly matches the spec format incl. lib version + `INP/1`.
**Test criteria:** Unit tests for codec (all P1.2 positive/negative fixtures); WKWebView integration test (loads a local HTML stub via `loadHTMLString`/local server) asserting handshake + origin filtering.

#### I3.3 — ScreenViewController, re-parenting & SnapshotCache · Size L · Depends: I3.2, ADR-0002
**Goal:** §4: screen container hosting live webview *or* snapshot; re-parenting choreography (`screen.willDetach`/`didAttach` ordering per ADR-0002, snapshot timing to avoid flashes); `SnapshotCache` (memory + disk, eviction by count/bytes, invalidation API); pull-to-refresh (emits `page.refresh`); activity indicator.
**Acceptance criteria:** (1) Push/pop in the Harness shows no white flash (recorded; and an automated pixel check: snapshot view non-blank at transition midpoint); (2) cache invalidation removes disk artefacts; (3) memory pressure (simulated) evicts memory layer but screens recover from disk; (4) pull-to-refresh round-trips and ends the spinner on `visit.completed`.
**Test criteria:** Unit tests for cache policy; XCUITest (against stub server) for push/pop visuals and pull-to-refresh.

#### I3.4 — Navigator, proposals & delegate · Size L · Depends: I3.3
**Goal:** §4 `Navigator` with main + modal `UINavigationController` stacks; consume `visit.propose` → path-config resolution → `NavigatorDelegate.handle(proposal:)` (`.accept` / `.acceptCustom` / `.reject`); default heuristics (same-URL replace, previous-URL pop); issue `visit.execute` / `page.restore` with correct screenIds; modal context handling incl. dismiss-then-continue rules; interactive-pop-gesture support driving the restore flow.
**Acceptance criteria:** (1) The §2.6 and §2.7 sequences execute against the conformance transport exactly as fixtured; (2) `.acceptCustom` pushes an arbitrary `UIViewController` and back-nav from it restores the web stack correctly; (3) `.reject` leaves web + native state unchanged (idempotence asserted); (4) modal context: propose-from-modal pushes within the modal stack; signal `resume` dismisses it.
**Test criteria:** Unit tests with a scripted Session double for proposal routing; conformance runner (I3.9 dependency note) covers sequences; XCUITest: 10-deep push/pop soak with assertions on title bar + content.

#### I3.5 — PathConfiguration loader · Size M · Depends: I3.1 (parallel-safe)
**Goal:** §4/§7: sources `[.file, .server]`, ETag-aware fetch + cache, merge semantics, `properties(for:)` resolver matching P1.4's reference resolver, `settings` exposure.
**Acceptance criteria:** (1) All P1.4 matching fixtures pass; (2) offline launch uses bundled file then hot-swaps when server config arrives (observable callback); (3) malformed remote config → keep last-known-good + log (never crash, never partial-apply).
**Test criteria:** Unit tests driven directly by vendored P1.4 fixture table; URLProtocol-stubbed fetch tests for ETag/304, malformed payload, timeout.

#### I3.6 — WebView policy layer · Size M · Depends: I3.4
**Goal:** §4: `WKNavigationDelegate` catching non-INP navigations — external domains → `SFSafariViewController`; same-domain full navigations → native-level advance proposal (degraded mode §2.3); `tel:`/`mailto:`/downloads → system; handshake-timeout degraded mode per screen.
**Acceptance criteria:** (1) External link opens Safari VC and the web stack is untouched; (2) a page without the adapter still navigates via degraded mode (spike's "plain page" reproduced); (3) target=_blank / `window.open` handled (policy: same-domain → advance, external → Safari VC) and documented.
**Test criteria:** Integration tests with local stub pages for each branch; XCUITest for the external-link UX.

#### I3.7 — Errors, auth hook, process recovery · Size M · Depends: I3.4
**Goal:** §8 matrix native side: error view (retry button → `visit.execute`), 404-on-push pop+toast option, `sessionDidDetectUnauthorized` delegate flow, version-kind handling (invalidate all snapshots, lazy per-screen refresh on attach), `webViewWebContentProcessDidTerminate` recovery (rebuild webview, cold-boot current screen, lazily refresh stack on attach).
**Acceptance criteria:** (1) Every row of the §8 matrix has an implemented default + a delegate override point; (2) process-kill (simulated via `WKWebView` termination test hook) recovers to an interactive current screen with the stack's URLs preserved; (3) auth flow: 401 → delegate presents login (modal web screen by default) → success signal → originating screen refreshed.
**Test criteria:** Unit tests for the decision table; XCUITests using demo error-injection endpoints (D5.3) for 500/401/409/timeout; process-kill integration test.

#### I3.8 — Bridge registry & BridgeComponent base · Size M · Depends: I3.2
**Goal:** §4: `Inp.registerComponents([...])`; base class with `onReceive(message:)`, `reply(to:with:)`, screen lifecycle hooks; `supportedComponents` injected into the handshake from the registry; per-screen component instantiation + teardown on `disconnect`/screen destroy.
**Acceptance criteria:** (1) Component receives `connect` with payload, can reply, and receives `disconnect` on unmount/screen pop; (2) unregistered component messages are dropped + logged; (3) registry is immutable after Navigator start (documented + enforced).
**Test criteria:** Unit tests with scripted Session; P1.3 bridge scenarios via conformance runner.

#### I3.9 — Conformance runner (iOS) · Size L · Depends: I3.4, I3.7, I3.8
**Goal:** XCTest-based runner executing vendored P1.3 scenarios against Navigator + Session with a scripted transport and fake clock, per the runner contract.
**Acceptance criteria:** As A2.13 ACs 1–3, for iOS; skip list empty at G2.
**Test criteria:** Runner + the vacuous-runner meta-test (as A2.13).

#### I3.10 — Signals (native side) · Size S · Depends: I3.4
**Goal:** Handle `signal{recede|refresh|resume}` incl. modal interactions per spec §6.2/§2.4 and flash hand-off to the bridge `toast` component when registered (graceful no-op otherwise).
**Acceptance criteria:** (1) Recede pops (and first dismisses a modal if present); (2) refresh reloads visible screen + invalidates its snapshot; (3) resume dismisses modal only; (4) all three fixtured scenarios pass.
**Test criteria:** Conformance scenarios; XCUITest for the modal-form → recede UX on the demo app.

#### I3.11 — XCUITest suite vs demo app · Size L · Depends: I3.4–I3.10, D5.1, D5.3
**Goal:** The G2 gate suite: scripted end-to-end flows against `inertia-native-demo` running in CI (container): cold start, advance ×5/pop ×5 with scroll restore assertion, modal form + recede, external link, pull-to-refresh, each error kind, 409, auth flow, process recovery.
**Acceptance criteria:** (1) Suite green on CI simulator (latest iOS) and locally on latest-1; (2) total runtime ≤ 15 min; (3) flake rate < 2% over 20 CI runs (tracked in PR with a soak-run link).
**Test criteria:** The suite is the deliverable; include a manual QA script (`Q6.1` referenced by the gate) generated from the same flow list.

---

## 6. Phase 4 — Laravel package (repo: `inertia-native-laravel`) — parallel with Phase 3

#### L4.1 — Repo scaffold · Size S · Depends: G0
**Goal:** Composer package skeleton (service provider, config publish), Pest + Testbench, CI matrix (PHP 8.2/8.3/8.4 × Laravel 11/12), Pint, coverage threshold ≥ 90%.
**Acceptance criteria:** Matrix green from clean clone; config publishes via `vendor:publish`.
**Test criteria:** Placeholder feature test booting the provider in Testbench.

#### L4.2 — Detection middleware & macros · Size M · Depends: L4.1
**Goal:** §6.1: parse the UA marker (`Inertia Native iOS|Android/<v>; INP/<n>`); macros `inertiaNative()`, `inertiaNativePlatform()`, `inertiaNativeAppVersion()`, `inpVersion()`; tolerant parsing (prefix text, future fields).
**Acceptance criteria:** (1) Exact spec UA strings parse for both platforms; (2) spoof-resistant only in the documented sense (it's a UA — docs must state it is presentation logic, not security); (3) absent/garbled UA ⇒ all macros return false/null, never throw; (4) parser fuzz: 50 mutated UA strings never throw.
**Test criteria:** Pest unit tests incl. the fuzz corpus committed as a dataset; macro availability feature test.

#### L4.3 — Shared props & Blade directives · Size S · Depends: L4.2
**Goal:** §6.1: auto-share the `native` prop group (toggleable in config); `@inertiaNative` / `@unlessInertiaNative` directives.
**Acceptance criteria:** (1) Prop present on every Inertia response for native UAs, `enabled:false` shape (not absent) for web — stable prop shape for front-end typing; (2) directives compile and branch correctly; (3) opt-out config removes auto-share without breaking helpers.
**Test criteria:** Feature tests asserting the Inertia response payload via Testbench + inertia-laravel; Blade compilation tests.

#### L4.4 — Signal routes, helpers & flash carriage · Size M · Depends: L4.2, ADR-0005
**Goal:** §6.2: `Route::inertiaNative()` registering the three signal routes under a configurable prefix; `recede_or_redirect()`, `refresh_or_redirect()`, `resume_or_redirect()` (+ `Responsable` class equivalents for those avoiding global helpers); flash data preserved across the signal redirect per ADR-0005; web fallback = plain redirect.
**Acceptance criteria:** (1) Native request → 303 to signal route → signal response matches ADR-0005's exact shape (prop name, payload) byte-for-byte against a committed fixture; (2) web request → normal redirect with flash intact; (3) sub-path deployment (`APP_URL` with `/app`) produces correct signal URLs; (4) helpers respect named-route + URL + back() forms.
**Test criteria:** Feature tests per signal × per client type × sub-path; fixture snapshot test of the signal response.

#### L4.5 — Path configuration authoring & serving · Size M · Depends: L4.1
**Goal:** §6.3: `config/inertia-native.php` rules/settings; serving endpoint with `platform` + `app_version` params, per-platform/per-version overrides, ETag + cache headers; `artisan inertia-native:path-config {platform}` export for bundling.
**Acceptance criteria:** (1) Endpoint output validates against the vendored P1.4 schema (validation test uses the schema file directly); (2) ETag/304 behaviour correct; (3) version-constrained overrides resolve per documented precedence; (4) artisan export byte-identical to the endpoint output for the same inputs.
**Test criteria:** Feature tests incl. schema validation; precedence table test mirroring P1.4 merge fixtures.

#### L4.6 — Test helpers · Size S · Depends: L4.2, L4.4
**Goal:** §6.4: `$this->asInertiaNative('ios', appVersion: '1.2.0')`; assertions `assertRecedeSignal()`, `assertRefreshSignal()`, `assertResumeSignal()`, `assertInertiaNativeProp(...)`.
**Acceptance criteria:** Helpers compose with Laravel's HTTP test API; assertions give actionable failure messages (show the actual response category).
**Test criteria:** Tests that test the helpers (positive + failure-message snapshot).

#### L4.7 — Scaffolding command (iOS) · Size L · Depends: L4.5, **G2** (needs a released ios lib tag)
**Goal:** §6.4: `php artisan inertia-native:install ios` generating an Xcode project from stubs: app name/bundle id prompts, base URL from `APP_URL`, bundled path config (calls L4.5 export), pinned SwiftPM dependency, README with run instructions and the local-HTTP/ATS dev note (§9).
**Acceptance criteria:** (1) Generated project builds in Xcode with zero manual edits against the demo app URL; (2) re-running is idempotent or fails safely with a clear message; (3) generated code total app-specific Swift ≤ 50 lines (PRD metric).
**Test criteria:** Pest tests for stub rendering/prompt handling; a CI macOS job that generates and `xcodebuild`s the project against a stub URL.

---

## 7. Phase 5 — Demo app (repo: `inertia-native-demo`) — parallel after G0

#### D5.1 — Demo Laravel app (React + Vue switchable) · Size L · Depends: G0 (uses adapter via local file dep until published)
**Goal:** One Laravel 12 app, two Vite front ends (`FRONTEND=react|vue` selects entry + root view) sharing identical routes/pages: list→detail (push), create/edit (modal context), settings (replace_root candidate), long scrolling page (restore fidelity), external-links page, bridge playground page, auth (login/logout, a protected page), flash-message flows. Docker-compose + seeded SQLite for CI.
**Acceptance criteria:** (1) Both front ends render every page with shared route names; (2) `docker compose up` → healthy app ≤ 90 s in CI; (3) page inventory maps 1:1 to a checklist of PRD requirements it exercises (committed as `COVERAGE.md`); (4) zero console errors in web mode with the adapter installed (G1 input).
**Test criteria:** Minimal Pest smoke tests (routes 200, both front ends build); CI builds both bundles.

#### D5.2 — Error-injection & version-bump harness · Size M · Depends: D5.1
**Goal:** Endpoints/middleware togglable via header or query: latency injection, 500, 401/419 simulation, 409 (mutable asset version), network-drop simulation guidance (proxy notes for native CI), malformed/non-Inertia HTML response route.
**Acceptance criteria:** (1) Each §8 matrix row is reproducible on demand via a documented toggle; (2) toggles are safe-by-default (require an explicit env flag so the demo can also be deployed publicly).
**Test criteria:** Pest tests per toggle; `ERRORS.md` runbook consumed by I3.7/I3.11 task authors.

#### D5.3 — Bridge playground & toast wiring · Size S · Depends: D5.1, A2.11/A2.12
**Goal:** A page exercising `useBridgeComponent` (React + Vue parity): button, form, share, toast usage with graceful web fallbacks, demonstrating `supported`-flag degradation.
**Acceptance criteria:** Page works on plain web (fallback UI) and is structured so iOS reference components (R6.2) light it up with no page changes.
**Test criteria:** Component tests (both frameworks) for fallback rendering.

---

## 8. Phase 6 — Integration & alpha (cross-repo; tasks live in the named repo)

#### R6.1 — Cross-repo integration pipeline · Size L · Repo: `inertia-native-ios` (workflow home) · Depends: I3.11, D5.2, G3
**Goal:** A scheduled + dispatchable CI workflow: boot demo container (pinned demo ref) → run full XCUITest suite → publish artefacts (videos on failure) → version-matrix job (adapter latest vs ios latest; adapter previous-minor vs ios latest) asserting handshake compatibility rules.
**Acceptance criteria:** (1) Green run linked; (2) failure artefacts demonstrably useful (forced-failure run attached); (3) matrix job fails correctly when an artificial protocol-major bump is injected (negative test).
**Test criteria:** The workflow itself + the forced-failure and negative-matrix evidence runs.

#### R6.2 — Reference bridge components (web + iOS) · Size L · Repo: split — web halves in `inertia-native-adapter` (`packages/components`), native halves in `inertia-native-ios` (`Sources/Components`); **two PRs, one per repo**, coordinated via a tracking issue · Depends: A2.11/A2.12, I3.8, D5.3
**Goal:** PRD BRG-3: `button` (nav-bar button), `form` (native submit + progress), `share` (share sheet), `toast` — each: web component (React + Vue), iOS component, docs page with copy-paste usage.
**Acceptance criteria:** (1) Each component works end-to-end on the demo playground (recording per component); (2) wire format passes the Hotwire-structural-compat check in SPEC.md (BRG-4); (3) graceful degradation on web and on a build with the component unregistered.
**Test criteria:** Web unit tests per component; iOS unit tests per component; one XCUITest per component on the playground page.

#### R6.3 — Rename & publish plumbing · Size M · Repo: each (mechanical) · Depends: OC-6 decision, G1, G2, G3
**Goal:** Execute the final naming decision across packages, set up publishing (npm public, SwiftPM tag conventions, Packagist), Changesets/release-please wiring, LICENSE files (MIT per PRD proposal unless overridden).
**Acceptance criteria:** Dry-run publishes succeed for all three ecosystems; install instructions in each README verified.
**Test criteria:** CI dry-run publish jobs; fresh-project install smoke for each package.

#### R6.4 — Alpha release train · Size M · Repo: each · Depends: R6.1–R6.3
**Goal:** Tag + publish `0.1.0-alpha.1` across protocol/adapter/ios/laravel; compatibility table committed to docs; demo app switched from local file deps to published versions.
**Acceptance criteria:** (1) Clean machine: PRD quick-start (composer require → npm i → artisan install ios → run) completes ≤ 30 min following only the docs (timed walkthrough recorded); (2) handshake compat table accurate.
**Test criteria:** The recorded walkthrough is the test; demo CI green on published versions.

#### R6.5 — Quick-start documentation · Size M · Repo: `docs` · Depends: R6.4
**Goal:** Docs site skeleton + the quick-start, navigation, path configuration, signals, and bridge pages (drawn from spec sections, made tutorial-shaped).
**Acceptance criteria:** Every code sample is extracted from (or CI-verified against) the demo app — no untested snippets; the G4 walkthrough uses only these pages.
**Test criteria:** Snippet-verification CI (samples compile/lint).

---

## 9. Phase 7 — Android port (repo: `inertia-native-android`) — starts after G4

> Tasks mirror Phase 3; deltas only are specified. Every task inherits its iOS twin's ACs translated to Android idioms, plus the deltas below.

#### N7.1 — Repo scaffold · Size M · Depends: G4 — as I3.1 (Gradle, ktlint, JUnit5/Robolectric, Espresso target, fixture vendoring).
#### N7.2 — Session & channel · Size L · Depends: N7.1, ADR-0004 — as I3.2. **Deltas:** channel per ADR-0004 with origin scoping; JS-interface threading marshalled to main; UA per spec; document-start injection via the ADR-chosen mechanism (`WebViewCompat` doc-start script where available, fallback strategy recorded).
#### N7.3 — ScreenFragment, re-parenting, SnapshotCache · Size L · Depends: N7.2 — as I3.3. **Deltas:** snapshot via `PixelCopy`/draw-cache decision recorded in-code ADR comment; swipe-refresh via `SwipeRefreshLayout`; transition animations matching platform defaults.
#### N7.4 — Navigator, back handling, modal model · Size L · Depends: N7.3 — as I3.4. **Deltas:** AndroidX Navigation graph; `OnBackPressedCallback` routing incl. **predictive back** (API 34+) honouring ADR-0003; modal model per §12.6 decision (record final choice as ADR in this repo).
#### N7.5 — PathConfig, policy layer, errors & recovery · Size L · Depends: N7.4 — as I3.5 + I3.6 + I3.7. **Deltas:** Custom Tabs for external links; `onRenderProcessGone` recovery; Robolectric coverage where Espresso is overkill.
#### N7.6 — Bridge registry + reference components · Size L · Depends: N7.2, R6.2 — as I3.8 + native halves of R6.2's four components (Kotlin), playground verified.
#### N7.7 — Conformance + Espresso suite · Size L · Depends: N7.4–N7.6, D5.2 — as I3.9 + I3.11. **Deltas:** emulator matrix API 26/29/33/35 in CI; flake budget < 2% over 20 runs.
#### N7.8 — Scaffolding (android) + beta train · Size M · Repos: `inertia-native-laravel` (command) + release tasks · Depends: N7.7, L4.7 — `artisan inertia-native:install android` mirroring L4.7 ACs (generated Kotlin ≤ 50 lines); `0.2.0-beta.1` across packages; integration pipeline extended with the Android leg; G5 checklist.

---

## 10. Cross-cutting backlog (scheduled opportunistically; not gate-blocking)

| ID | Task | Repo | Notes |
|---|---|---|---|
| X1 | Debug overlay (message inspector) toggle in Harness apps | ios/android | DX-2; S each |
| X2 | `docs` ADR mirror + protocol reference rendering | docs | DX-4 |
| X3 | Security review checklist execution (§9, all repos) | each | pre-G4 recommended; produce findings issues |
| X4 | Performance budget re-measurement vs S0.7 baseline | ios | pre-G4; regression > 20% blocks R6.4 |
| X5 | Upstream Inertia hook PR (if ADR-0001 chose strategy (c)) | external | shepherded by maintainer, tracked as risk |
| X6 | App-store submission guide + demo app store run | docs/demo | PRD M3 input |

---

## 11. Suggested execution order (lanes)

```
Lane 1 (critical path): S0.1 → S0.2 → S0.7(G0) → A2.1..A2.5 → A2.13(G1) → I3.4 → I3.9 → I3.11(G2) → R6.1 → R6.4(G4) → N7.x(G5)
Lane 2 (parallel, native): S0.3, S0.4 → I3.1, I3.2, I3.3, I3.5 (post-G0) → I3.6, I3.7, I3.8, I3.10
Lane 3 (parallel, server): S0.6 → L4.1..L4.6 (G3) → L4.7 (post-G2)
Lane 4 (parallel, web/demo): S0.5 → P1.1..P1.5 → A2.6..A2.12, A2.14 → D5.1..D5.3 → R6.2, R6.5
```

Total: 49 tasks (7 spike, 5 protocol, 14 adapter, 11 iOS, 7 Laravel, 3 demo, 5 integration/release, 8 Android, plus cross-cutting backlog).

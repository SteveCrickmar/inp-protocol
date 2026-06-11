# inp-protocol

> Inertia Native Protocol (INP) — the versioned web↔native contract: spec, JSON Schemas, conformance fixtures, ADRs, and the M0 spike. Coordination point for all Inertia Native repos.

📋 **Part of the [Inertia Native](https://github.com/users/SteveCrickmar/projects/4) project** — an open-source toolkit for shipping native iOS & Android apps powered by an existing Laravel + Inertia.js application, modelled on (but not depending on) Hotwire Native. Track the cross-repo roadmap on the **[project board](https://github.com/users/SteveCrickmar/projects/4)**.

### Repositories in the Inertia Native project
- **inp-protocol** ⬅ _this repo_
- **[inertia-native-adapter](https://github.com/SteveCrickmar/inertia-native-adapter)** — visit interception, screen page-cache & restore, lifecycle reporting, bridge transport
- **[inertia-native-ios](https://github.com/SteveCrickmar/inertia-native-ios)** — Navigator, shared WKWebView + snapshot cache, path configuration, bridge components, native error/auth/recovery. Models Hotwire Native iOS; zero Turbo dependency
- **[inertia-native-android](https://github.com/SteveCrickmar/inertia-native-android)** — single shared WebView across fragment destinations, AndroidX Navigation, path config, bridge components
- **[inertia-native-laravel](https://github.com/SteveCrickmar/inertia-native-laravel)** — native detection & macros, shared props, signal routes & helpers, path-config authoring/serving, scaffolding & test helpers
- **[inertia-native-demo](https://github.com/SteveCrickmar/inertia-native-demo)** — one Laravel 12 app with switchable React & Vue front ends exercising every feature; error-injection harness; doubles as docs example and UI-test target
- **[docs](https://github.com/SteveCrickmar/docs)** — quick start, navigation, path configuration, signals, bridge components, native screens, auth, protocol reference

## Project documents
- [PRD](https://github.com/SteveCrickmar/inp-protocol/blob/main/docs/01-prd-inertia-native.md)
- [Technical Specification](https://github.com/SteveCrickmar/inp-protocol/blob/main/docs/02-technical-spec-inertia-native.md) (normative)
- [Task Breakdown](https://github.com/SteveCrickmar/inp-protocol/blob/main/docs/03-task-breakdown.md)

## Toolchain baseline (OC-8)
Spec & schemas (JSON Schema 2020-12); reference resolver + validator in TypeScript (Node 20). Conformance fixtures are language-neutral (YAML/JSON).

## Working conventions
- **OC-1:** one task → one branch (`task/<ID>-slug`) → one PR; no task touches more than one repo.
- **OC-3:** the [protocol spec](https://github.com/SteveCrickmar/inp-protocol) is law. Conformance fixtures are vendored read-only at the ref in `INP_SPEC_REF`.
- See the [task breakdown](https://github.com/SteveCrickmar/inp-protocol/blob/main/docs/03-task-breakdown.md) §0 for the full operating conventions and Definition of Done (OC-2).

## Tasks tracked in this repo
- **P1.1** — Repo scaffold & governance
- **P1.2** — JSON Schemas for all messages
- **P1.3** — Conformance fixtures: behavioural scenarios
- **P1.4** — Path-configuration schema + matching fixtures
- **P1.5** — `@inertia-native/protocol` types package
- **R6.3** — Rename & publish plumbing
- **R6.4** — Alpha release train
- **S0.1** — Spike harness
- **S0.2** — Restore strategy evaluation
- **S0.3** — Webview re-parenting validation
- **S0.4** — History neutralisation (iOS)
- **S0.5** — Android risk probes
- **S0.6** — Signal detection mechanism
- **S0.7** — Protocol v1 freeze
- **X3** — Security review checklist execution (§9, all repos)
- **X5** — Upstream Inertia hook PR (if ADR-0001 chose strategy (c))

## Status
Pre-alpha. Names are placeholders pending a trademark check (OC-6). Licensed MIT (proposed).

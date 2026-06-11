# ADR-0006 — INP protocol v1 decisions (freeze)

- **Status:** Proposed (release-candidate) — folds ADR-0001…0005; **gated on their measurements**
- **Task:** S0.7 · **Spec refs:** all of §2, §6.2, §7, §8 · **Gate:** G0

> ⚠️ **Authoring caveat.** This ADR records the protocol-level decisions that produce
> `protocol/SPEC.md` and the `v1.0.0-rc.1` tag. The `-rc.1` suffix is deliberate: it is a
> **release candidate**, not the final freeze. Several upstream ADRs carry `PENDING MEASUREMENT`
> sections (restore fidelity, re-parenting, predictive back). G0 may only be **signed off by the
> maintainer once those measurements are filled in**; until then this is a complete, coherent
> draft that lets Phase 1+ pin `INP_SPEC_REF` and start in parallel.

## Context

S0.7 folds every Phase-0 ADR outcome into one normative spec, resolves every §2 TODO, and tags
a release candidate so downstream repos can pin it. This ADR is the decision log behind that
spec — the rejected alternatives live here so SPEC.md stays clean.

## Decisions

**D1 — Envelope (§2.2).** Fixed shape `{inp:int, id:uuid, replyTo:uuid|null, type:string,
payload:object}`. `inp` is the protocol **major**; breaking changes bump it and the handshake
negotiates. *Rejected:* per-message versioning (too fine-grained); a `v` semver string (majors
are the only compatibility axis — spec §11).

**D2 — Forward compatibility (§2.2).** Unknown `type` ⇒ ignore + debug-log; unknown payload
fields ⇒ ignore. This is **behavioural, not schema-enforced**: envelope schemas do **not**
reject unknown `type`, and payload schemas permit `additionalProperties` (a `--strict` CI-only
mode flags them). *Rejected:* strict rejection of unknowns (kills forward compat).

**D3 — Handshake (§2.3).** Native injects `window.__INP__` at document-start with
`{platform, appVersion, protocolVersions:[1], supportedComponents, screenId, settings}`. Adapter
sends `adapter.ready{adapterVersion, inertiaVersion, protocolVersion, page}`; native replies
`session.configure{screenId, settings, debug}`. Absent `adapter.ready` within a timeout ⇒
**degraded plain-web mode** for that screen (§8). *Rejected:* a multi-round capability
negotiation (the single `protocolVersions` array + `supportedComponents` is enough for v1).

**D4 — Protocol-major mismatch.** If native advertises a major the adapter can't speak (or vice
versa), the adapter emits one `log{level:error}` and **hard-disables**; native shows a clear
mismatch error screen (spec §11). Compatibility = shared INP major.

**D5 — Restore (from ADR-0001).** Protocol stays renderer-agnostic: it only ever exchanges
`page.restore{screenId, scroll}` → `page.restored{screenId, url, ok}`. `ok:false` ⇒ native
commands a fresh `visit.execute`. The chosen strategy (client page-swap seam, re-fetch fallback)
is an **adapter implementation detail behind `PageRenderer`** and has **zero protocol surface** —
so a strategy change (or the X5 upstream hook) never bumps `inp`.

**D6 — Re-parenting obligations (from ADR-0002).** `screen.willDetach` semantics are tightened:
on detach the adapter MUST snapshot scroll, pause media, and blur the active element. Snapshot
timing (native side) is "capture on willDetach before swapping in the screenshot" to avoid white
flashes.

**D7 — History (from ADR-0003).** `history.blockedPop{}` is a confirmed diagnostic message.
Commanded visits use replace-style history; popstate is neutralised by re-push. Documented
constant: web `history.length` ≈ 1 per live screen.

**D8 — Android transport (from ADR-0004).** §2.1 finalised to: `WebMessageListener` preferred
(origin-scoped, explicit threading), `@JavascriptInterface InpChannel.postMessage` fallback at
minSdk 26. Wire format is identical for both, so no protocol impact.

**D9 — Signals (from ADR-0005).** Detection is via the reserved shared prop `inp.signal`
`{name, flash, fallbackUrl}`, **not** URL sniffing. `signal{name, flash?, fallbackUrl}` is the
native-facing message; the signal page never paints.

**D10 — Visit proposals (§2.4, §3.3, §3.4).** `visit.propose{proposalId, url, method, action,
options}` carries only whitelisted Inertia options (`preserveScroll`, `preserveState`, `only`,
`native`) — never headers or body. `action` ∈ `advance|replace`; same-URL ⇒ `replace`;
previous-URL ⇒ native treats as pop. The `native` hint wins over path-config rules (precedence:
hint > rule > default).

**D11 — Error kinds (§8).** `visit.failed.kind` ∈ `network | http | version | non_inertia |
cancelled`. 409 ⇒ `kind:version`, emitted **before** the controlled reload so native invalidates
snapshots first.

**D12 — Path-config schema (§7).** Frozen keys: `context (default|modal)`,
`presentation (default|replace|pop|refresh|none|replace_root|clear_all)`, `pull_to_refresh`,
`title`, `animated`, `uri`, `fallback_uri`; rule matching is first-to-last accumulation with
later overrides, regex on path+query with a query opt-out. (Schema authored fully in P1.4.)

## Consequences

- `protocol/SPEC.md` is authored to encode D1–D12 with a worked JSON example per message type
  and updated §2.6/§2.7 sequence diagrams; zero TODO/TBD markers (S0.7 AC1).
- A **measurement report** (`docs/measurements/`) records the §12.7 perf numbers (cold start →
  first interactive; push → live page p50/p95) — `PENDING` until the harness runs on hardware.
- `v1.0.0-rc.1` is tagged as the pin point for `INP_SPEC_REF`; the release notes enumerate the
  still-`PENDING` ADR measurements so G0 sign-off is gated, not implied.
- P1.2's schema validator will validate every SPEC.md inline JSON example (forward note left for
  that task, per S0.7 test criteria).

# ADR-0001 — Page-restore strategy for back-navigation

- **Status:** Proposed (desk analysis) — **empirical sections PENDING MEASUREMENT**
- **Task:** S0.2 · **Spec refs:** §3.2, §12.1, PRD NAV-2
- **Decision driver:** This is the single highest-leverage technical risk in the project
  (spec §12, PRD §11). Failure at acceptable fidelity downgrades NAV-2 for v1.

> ⚠️ **Authoring caveat.** Written on Linux without Xcode/simulator. The fidelity matrix
> below is the *experimental design*; its result cells say `PENDING` and MUST be filled by
> running the `spike/server` + `spike/ios` harness on a simulator before G0. The
> recommendation is desk analysis of Inertia v3's source/public API surface, not yet a
> measured outcome. Do not sign off G0 on this ADR until the matrix is complete.

## Context

On native back-navigation the shared `WKWebView` is re-parented onto an existing screen and
the adapter must re-render that screen's cached Inertia page object **without a network
request** (spec §2.7, §3.2), then restore scroll. Inertia v3 exposes no public
"set current page from an object" API, so the adapter must reach a documented-or-stable seam.

The page-object cache (`Map<screenId, ScreenEntry>`) is straightforward; the risk is the
*render* step. Four strategies were proposed in §3.2, in descending order of preference.

## Options considered

**(a) History-based restore.** Drive Inertia's own history/restore path
(`router` + the browser History API state Inertia persists) so a back movement re-renders the
cached page object the way a real browser Back would. *Upside:* uses Inertia's intended
restore machinery, so scroll/state handling is "for free" and forward-compatible. *Risk:*
native owns history (§3.5) and we deliberately flatten web history to ~1 entry/screen, so
there may be no history entry to restore to; coupling restore to history fights the history
discipline module (ADR-0003).

**(b) Client `setPage` / hydration seam.** Call the same internal page-swap that SSR/hydration
and `router.visit` use to install a `Page` object, but feed it the *cached* object with no
fetch. In Inertia v3 this is the `router`'s page-setting path used after a response resolves.
*Upside:* it is exactly "render this page object, no request" — the precise primitive we need;
decoupled from history. *Risk:* the seam is not part of the documented public API surface, so
it is a contract-test liability (spec §10) and can churn across v3 minors.

**(c) Upstream hook.** Land a small PR in `@inertiajs/core` adding a public
`router.setPage(page, { preserveScroll })` (or equivalent) so (b) becomes supported API.
*Upside:* removes the contract-test risk permanently; benefits the whole ecosystem. *Risk:*
out of our control / timeline; needs maintainer buy-in. Tracked as backlog X5.

**(d) `preserveScroll` re-fetch fallback.** On restore, issue a real `visit.execute` with
`preserveScroll: true`. *Upside:* uses only public API; always correct. *Downside:* costs a
network request on every back (defeats the "instant native back" goal) and shows a flash of
loading; only acceptable as a fallback or for stale entries.

## Decision (provisional)

Adopt **(b) the client page-swap seam as the primary** `PageRenderer` strategy, with
**(d) re-fetch as the always-available fallback** (cache miss, stale entry, or seam failure),
and **open the (c) upstream hook** to convert (b) from "stable internal seam" to "public API"
(backlog X5). Reject **(a)** as the primary because it structurally conflicts with the history
neutralisation in ADR-0003.

`PageRenderer` is a DI interface (spec §3.2; A2.5 AC5) so the strategy can change without any
protocol impact — the protocol only ever sees `page.restore` → `page.restored{ok}`.

**This decision is provisional** until the fidelity matrix confirms (b) renders the cached
object with acceptable fidelity on Inertia v3. If (b) fails to install a page without a fetch,
fall back to (d) as primary and **trigger the PRD NAV-2 downgrade** (wording below).

### The exact Inertia v3 seams (b) touches — to be pinned by a contract test (A2.5)

| Seam | Tag | Notes |
|---|---|---|
| `router` page-set after response resolve | **internal** | the swap that installs a `Page` object into the active adapter |
| `setPage`/`swapComponent` adapter callback (`createInertiaApp` `setup`) | **public-ish** | the framework adapter's render entry; reachable in React/Vue |
| `page.scrollRegions` / `preserveScroll` handling | **public** | documented visit option, reused on restore |

> Fill these with the precise symbol names + file refs observed in `@inertiajs/core@3.x`
> during the harness run; the single contract-test file referenced here (A2.5) must fail
> loudly if any change in a future v3 minor.

## Fidelity matrix — **PENDING MEASUREMENT**

Scenario script (run **per strategy** on the harness): open `/products`, scroll 1500px, tap a
product (push detail), then native-back to the list.

| Fidelity axis | (a) history | (b) setPage seam | (c) upstream hook | (d) re-fetch |
|---|---|---|---|---|
| Scroll position restored | PENDING | PENDING | PENDING | PENDING |
| Form input state preserved | PENDING | PENDING | PENDING | PENDING |
| Component local state | PENDING | PENDING | PENDING | PENDING |
| Mounted-effects re-run (over-fire?) | PENDING | PENDING | PENDING | PENDING |
| Media/iframe state | PENDING | PENDING | PENDING | PENDING |
| Network requests on restore | expect 0 | expect 0 | expect 0 | **1 (by design)** |
| Screen recording link | PENDING | PENDING | PENDING | PENDING |

Expected (hypothesis, to be confirmed): (b) restores scroll + the page object instantly with
**no** network; component local state is **lost** (the component remounts) — this is the known
fidelity ceiling we accept for v1 (PRD §11). (d) is identical in fidelity but costs a request.

## Consequences

- The adapter carries a documented fidelity limit for v1: **"restore = re-render page object +
  scroll restore; component-local state is not preserved across native back"** (PRD §11). This
  must be stated in the quick-start docs (R6.5) and the SPEC.md non-goals.
- A contract test (A2.5) pins the (b) seam; CI in `inertia-native-adapter` fails on upstream
  drift, which is the intended early-warning for v3 minor churn.
- Backlog X5 (upstream hook PR) is shepherded as a risk; if it lands, the contract-test
  liability disappears and (b) is promoted to supported API.
- `staleAfter` (default 5 min) governs stale-while-revalidate: stale entries render via (b)
  instantly **then** issue a background partial reload (spec §3.2).

### NAV-2 downgrade wording (only if (b) and (a) both fail to render without a fetch)

> *"For v1, native back restores the previous screen by re-fetching it with `preserveScroll`
> (strategy d). Scroll position is preserved; the page is re-requested from the server rather
> than restored from cache. Cache-free instant restore is deferred pending an upstream Inertia
> hook (X5)."*

This downgrade is a **go/no-go recorded at G0** (spec §1 gate criteria).

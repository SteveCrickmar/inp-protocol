# Spike performance measurement report (§12.7)

- **Task:** S0.7 · **Spec ref:** §12.7 · **Gate input:** G0
- **Status:** **PENDING MEASUREMENT** — template only.

> ⚠️ These numbers require running the `spike/server` + `spike/ios` (and `spike/android`)
> harness on a real simulator/device. They could not be captured on the Linux authoring box.
> G0 sign-off requires this table filled in (spec §1, §12.7).

## Method (to execute on a Mac + Android device)

1. Build the server harness for production assets: `npm run build` then
   `php artisan serve --host 0.0.0.0 --port 8111` (no Vite HMR — measure realistic loads).
2. iOS: run `spike/ios` on the named simulator; Android: run `spike/android` on a mid-tier
   physical device (per spec §12.7, "mid-tier Android").
3. Instrument with the debug overlay timestamps already emitted by the harness
   (`adapter.ready`, `visit.started`, `visit.completed`) plus platform traces
   (iOS `os_signpost`, Android `systrace`/`Perfetto`).
4. Capture each metric ≥ 20 times; report p50 and p95. Record device/sim model + OS version.

## Metrics

| Metric | Definition | Device/Sim | p50 | p95 |
|---|---|---|---|---|
| Cold start → first interactive | process launch → first screen interactive (first `visit.completed` + scroll responsive) | PENDING | PENDING | PENDING |
| Push → live page | `visit.propose` → `visit.completed` for an advance | PENDING | PENDING | PENDING |
| Back → restored | `page.restore` → `page.restored{ok:true}` (cached, strategy b) | PENDING | PENDING | PENDING |
| Back → restored (fallback) | `page.restore` → fresh fetch render (strategy d) | PENDING | PENDING | PENDING |

## Notes / regression budget

- These become the baseline for backlog **X4** (pre-G4 re-measurement; a >20% regression blocks
  R6.4).
- Record the device matrix actually used (spec §10 suggests iOS latest-2 + 1 physical; Android
  API 26/29/33/35 + 2 OEMs). The spike only needs the named sim + one mid-tier Android.

# INP Spike — Server harness (`spike/server`)

> **Disposable Phase-0 spike code (OC-5).** This is a deliberately minimal Laravel 12 +
> Inertia v3 + React app whose only purpose is to give the iOS/Android harness apps and
> the adapter a *realistic* SPA to drive while we de-risk the protocol. It is **not** the
> production `inertia-native-laravel` package and must never be imported by it. The
> `native` shared prop, the `recede_or_redirect()` helper, and the `/_inp/*` signal routes
> here are hand-rolled stand-ins for what Phase-4 (L4.x) will build properly.

## What it is

- **Laravel 12.62** + **inertia-laravel v3.1** (server) + **@inertiajs/react v3.3** + **React 19** + **Vite 7** (client).
- Five pages, matching the S0.1 brief (list → detail → edit-form(modal-ish) → settings → external-link):
  | Route | Component | Exercises |
  |---|---|---|
  | `/products` | `Products/Index` | NAV-1 list, long scroll for restore (S0.2) |
  | `/products/{id}` | `Products/Show` | push target, modal-edit link (`native={{action:'modal'}}`) |
  | `/products/{id}/edit` | `Products/Edit` | non-GET form + 303 + recede signal (FRM-1/2, S0.6) |
  | `/settings` | `Settings/Index` | bridge echo round-trip (S0.1 AC2), `replace_root` candidate |
  | `/external` | `External` | external links / `mailto:` / `tel:` / `target=_blank` (NAV-5) |
- **Signal routes** `/_inp/{recede,refresh,resume}` render a page carrying the reserved
  `inp.signal` shared prop — the S0.6 / ADR-0005 mechanism under evaluation.
- **Seeded SQLite** (30 products) — no external DB needed.
- The spike INP bridge lives in `resources/js/spike/inp-spike.js` (the web peer the native
  harness talks to; see the comments there). It is observational in a plain browser so the
  five pages stay navigable without a native host.

## Run it

Requirements: PHP 8.2+, Composer, Node 20+.

```bash
cd spike/server
composer install
npm install
cp .env.example .env
php artisan key:generate
touch database/database.sqlite
php artisan migrate --seed
# two terminals:
php artisan serve --host 0.0.0.0 --port 8111   # API on http://localhost:8111
npm run dev                                     # Vite dev server (HMR)
```

For a simulator/emulator to reach it:
- **iOS simulator** uses `http://localhost:8111` directly.
- **Android emulator** uses `http://10.0.2.2:8111` (host-loopback alias).
- A physical device uses your machine's LAN IP (`http://192.168.x.x:8111`); Vite is already
  bound to `0.0.0.0` in `vite.config.js`.

For a production-style build (what the native apps load against a non-HMR server):

```bash
npm run build && php artisan serve --host 0.0.0.0 --port 8111
```

## Verified behaviours (curl, on Linux — no simulator needed)

These were checked during S0.1 and all pass:

```bash
# All five pages return the right Inertia component:
curl -s -H "X-Inertia: true" -H "X-Inertia-Version: v1" http://localhost:8111/products
#   → {"component":"Products/Index", ...}  (also Products/Show, Products/Edit, Settings/Index, External)

# Signal route carries inp.signal (ADR-0005 mechanism):
curl -s -H "X-Inertia: true" -H "X-Inertia-Version: v1" http://localhost:8111/_inp/recede
#   → component "Signal", props.inp.signal.name == "recede"

# 409 asset-version flow (ERR-3 / spec §6.5) — bump APP_ASSET_VERSION to simulate a deploy:
curl -o /dev/null -w "%{http_code}\n" -H "X-Inertia: true" -H "X-Inertia-Version: v0" http://localhost:8111/products   # 409 (stale)
curl -o /dev/null -w "%{http_code}\n" -H "X-Inertia: true" -H "X-Inertia-Version: v1" http://localhost:8111/products   # 200 (fresh)

# Native UA → `native` shared prop populated:
curl -s -A "MyApp/1.0; Inertia Native iOS/0.1; INP/1" -H "X-Inertia: true" -H "X-Inertia-Version: v1" \
     http://localhost:8111/products
#   → props.native == {"enabled":true,"platform":"ios"}
```

## Knobs

- `APP_ASSET_VERSION` (`.env`, default `v1`) — the Inertia asset version. Bump it to force a
  409 on in-flight clients and exercise the version flow (ERR-3, S0.7 measurement input).
- Native detection is a crude UA `str_contains('Inertia Native')` — intentionally simple; the
  real tolerant parser is L4.2.

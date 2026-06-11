<?php

namespace App\Http\Middleware;

use Illuminate\Http\Request;
use Inertia\Middleware;

/**
 * Spike-only Inertia middleware.
 *
 * NOTE (OC-5): this is disposable Phase-0 spike code. It exists to give the
 * adapter / native shells a realistic Inertia v3 app to drive. It is NOT the
 * production `inertia-native-laravel` package and must never be imported by it.
 * The `native` shared prop here is a hand-rolled stand-in for what L4.3 will
 * eventually provide; the `inp.signal` prop is the S0.6 reserved-shared-prop
 * mechanism under evaluation (ADR-0005).
 */
class HandleInertiaRequests extends Middleware
{
    protected $rootView = 'app';

    public function version(Request $request): ?string
    {
        // Drives the Inertia X-Inertia-Version / 409 asset-staleness flow (ERR-3,
        // spec §6.5). Bump APP_ASSET_VERSION in .env to simulate a deploy.
        return (string) config('app.asset_version', parent::version($request));
    }

    public function share(Request $request): array
    {
        return array_merge(parent::share($request), [
            // Hand-rolled stand-in for the future L4.3 `native` shared prop group.
            // Detection here is a crude UA sniff purely so the spike can show the
            // shape; the real package (L4.2) does tolerant parsing.
            'native' => [
                'enabled'  => $this->isNativeClient($request),
                'platform' => $this->nativePlatform($request),
            ],
            // Flash carriage for the signal redirect experiment (S0.6 / ADR-0005).
            'flash' => [
                'message' => fn () => $request->session()->get('message'),
            ],
        ]);
    }

    private function isNativeClient(Request $request): bool
    {
        return str_contains((string) $request->userAgent(), 'Inertia Native');
    }

    private function nativePlatform(Request $request): ?string
    {
        $ua = (string) $request->userAgent();
        if (str_contains($ua, 'Inertia Native iOS')) {
            return 'ios';
        }
        if (str_contains($ua, 'Inertia Native Android')) {
            return 'android';
        }

        return null;
    }
}

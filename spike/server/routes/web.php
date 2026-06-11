<?php

use App\Models\Product;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;
use Inertia\Inertia;

Route::redirect('/', '/products');

// --- Pages ------------------------------------------------------------------

Route::get('/products', function (Request $request) {
    // PROBE S0.3: `?delay=1500` injects latency so there is an in-flight XHR to
    // observe while the live webview is re-parented (does the request survive
    // the move and render on the correct screen?).
    if ($ms = (int) $request->query('delay')) {
        usleep(min($ms, 5000) * 1000);
    }

    return Inertia::render('Products/Index', [
        'products' => Product::orderBy('id')->get(['id', 'name', 'price', 'category']),
    ]);
})->name('products.index');

Route::get('/products/{product}', function (Product $product) {
    return Inertia::render('Products/Show', ['product' => $product]);
})->name('products.show');

Route::get('/products/{product}/edit', function (Product $product) {
    return Inertia::render('Products/Edit', ['product' => $product]);
})->name('products.edit');

Route::put('/products/{product}', function (Request $request, Product $product) {
    $data = $request->validate([
        'name' => 'required|string|max:255',
        'price' => 'required|numeric',
        'description' => 'nullable|string',
    ]);
    $product->update($data);

    // FRM-2 / S0.6: native gets a recede SIGNAL (pop the edit modal); web gets a
    // plain redirect. This is the reserved-shared-prop mechanism under test in
    // ADR-0005 — the signal route renders a page carrying `inp.signal`.
    return recede_or_redirect($request, route('products.show', $product), 'Saved ✓');
})->name('products.update');

Route::get('/settings', fn () => Inertia::render('Settings/Index'))->name('settings');
Route::get('/external', fn () => Inertia::render('External'))->name('external');

// --- Signal routes (S0.6 / ADR-0005 experiment) -----------------------------
// Prefix is `/_inp` per spec §6.2 (default). These render a minimal page whose
// shared props carry `inp.signal`; the adapter recognises it and never paints.

Route::prefix('_inp')->group(function () {
    foreach (['recede', 'refresh', 'resume'] as $signal) {
        Route::get($signal, function (Request $request) use ($signal) {
            return Inertia::render('Signal', [
                'inp' => [
                    'signal' => [
                        'name' => $signal,
                        'flash' => ['message' => $request->session()->get('message')],
                        'fallbackUrl' => $request->session()->get('fallbackUrl', route('products.index')),
                    ],
                ],
            ]);
        })->name("inp.$signal");
    }
});

/**
 * Spike-only stand-in for the future L4.4 `recede_or_redirect()` helper.
 * Native request → 303 to the signal route (flash carried in session).
 * Web request → plain redirect with flash intact.
 */
function recede_or_redirect(Request $request, string $fallbackUrl, ?string $message = null)
{
    if ($message) {
        $request->session()->flash('message', $message);
    }

    if (str_contains((string) $request->userAgent(), 'Inertia Native')) {
        $request->session()->flash('fallbackUrl', $fallbackUrl);

        return redirect()->route('inp.recede');
    }

    return redirect($fallbackUrl);
}

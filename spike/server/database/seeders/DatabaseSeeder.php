<?php

namespace Database\Seeders;

use App\Models\Product;
use Illuminate\Database\Seeder;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        $categories = ['Audio', 'Wearables', 'Cameras', 'Home', 'Accessories'];
        $adjectives = ['Compact', 'Pro', 'Mini', 'Ultra', 'Lite', 'Max', 'Air', 'Studio'];
        $nouns = ['Speaker', 'Watch', 'Lens', 'Lamp', 'Charger', 'Headset', 'Tracker', 'Hub'];

        // 30 rows → a list long enough to scroll for the S0.2 restore test.
        for ($i = 1; $i <= 30; $i++) {
            Product::create([
                'name' => $adjectives[$i % count($adjectives)].' '.$nouns[$i % count($nouns)].' '.$i,
                'price' => number_format(19 + ($i * 7.5), 2, '.', ''),
                'category' => $categories[$i % count($categories)],
                'description' => "Seeded product #$i for the INP spike harness. Exercises list → detail → edit navigation, scroll restore, and the form/signal flow.",
            ]);
        }
    }
}

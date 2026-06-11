import { defineConfig } from 'vite';
import laravel from 'laravel-vite-plugin';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
    plugins: [
        laravel({
            input: ['resources/css/app.css', 'resources/js/app.jsx'],
            refresh: true,
        }),
        react(),
        tailwindcss(),
    ],
    server: {
        // Expose on the LAN so an iOS simulator / Android emulator can reach Vite HMR.
        host: '0.0.0.0',
        watch: {
            ignored: ['**/storage/framework/views/**'],
        },
    },
});

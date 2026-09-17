import { resolve } from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const pages = ['index', 'waypoints', 'confirm', 'hud', 'minimap', 'toasts', 'panel'];

export default defineConfig({
    plugins: [react()],
    base: './',
    build: {
        target: 'es2022',
        outDir: '../build/generated/webResources/assets/glue-showcase/web',
        emptyOutDir: true,
        rolldownOptions: {
            input: Object.fromEntries(pages.map(page => [page, resolve(import.meta.dirname, `${page}.html`)])),
            // CEF serves the library's framework-neutral bridge; it is not bundled into applications.
            external: ['https://glue-web.glue/bridge.js'],
        },
    },
});

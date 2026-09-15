import { build } from 'esbuild';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const output = new URL('./dist/', import.meta.url);
await mkdir(output, { recursive: true });
await build({
    entryPoints: [fileURLToPath(new URL('./src/app.jsx', import.meta.url))],
    outfile: fileURLToPath(new URL('app.js', output)),
    bundle: true, format: 'iife', target: 'es2022', minify: true,
    define: { 'process.env.NODE_ENV': '"production"' }, legalComments: 'linked'
});
const css = await readFile(new URL('./src/style.css', import.meta.url), 'utf8');
await writeFile(new URL('index.html', output), `<!doctype html><html><head><meta charset="utf-8"><title>JCEF React lab</title><style>${css}</style></head><body><div id="root"></div><script src="/app.js"></script></body></html>`);

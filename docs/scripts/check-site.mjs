import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const output = fileURLToPath(new URL('../dist/', import.meta.url));
const origin = 'https://glue-docs.invalid/';

async function filesUnder(directory, prefix = '') {
    const paths = [];
    for (const entry of await readdir(directory, { withFileTypes: true })) {
        const relative = `${prefix}${entry.name}`;
        if (entry.isDirectory()) paths.push(...await filesUnder(join(directory, entry.name), `${relative}/`));
        else paths.push(relative);
    }
    return paths;
}

const files = new Set(await filesUnder(output));
const pages = new Map();
const anchors = new Map();
for (const file of files) {
    if (!file.endsWith('.html')) continue;
    const html = await readFile(join(output, file), 'utf8');
    pages.set(file, html);
    anchors.set(file, new Set([...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1])));
}

function targetFile(pathname) {
    const path = decodeURIComponent(pathname).replace(/^\/+/, '');
    return [path, `${path}.html`, `${path.replace(/\/$/, '')}${path ? '/' : ''}index.html`]
        .find(candidate => files.has(candidate));
}

const errors = new Set();
let references = 0;
for (const [file, html] of pages) {
    const route = file.replace(/index\.html$/, '').replace(/\.html$/, '');
    const pageUrl = new URL(route, origin);
    for (const match of html.matchAll(/\b(?:href|src)="([^"]+)"/g)) {
        const reference = match[1].replaceAll('&amp;', '&');
        const url = new URL(reference, pageUrl);
        if (url.origin !== new URL(origin).origin) continue;
        references++;
        const target = targetFile(url.pathname);
        if (!target) {
            errors.add(`${file}: missing ${url.pathname}`);
            continue;
        }
        const fragment = decodeURIComponent(url.hash.slice(1)).split(':~:text=')[0];
        if (fragment && anchors.has(target) && !anchors.get(target).has(fragment)) {
            errors.add(`${file}: missing #${fragment} in ${target}`);
        }
    }
    if (!/<link\b[^>]*rel="icon"[^>]*href="\/icon\.png"/.test(html)) {
        errors.add(`${file}: missing Glue favicon declaration`);
    }
}

assert.ok(pages.size > 0, 'VitePress must generate HTML pages');
assert.deepEqual([...errors], [], 'Published links, anchors and assets must resolve');
const icon = await readFile(join(output, 'icon.png'));
const libraryIcon = await readFile(new URL('../../glue-core/src/main/resources/assets/glue/icon.png', import.meta.url));
assert.ok(icon.equals(libraryIcon), 'The site must publish the actual library icon');
console.log(`Checked ${pages.size} pages, ${references} local references and the library favicon.`);

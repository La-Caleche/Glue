import { copyFile, mkdir } from 'node:fs/promises';

// Keep the published mod icon as the single source of branding for the site.
const icon = new URL('../../glue-core/src/main/resources/assets/glue/icon.png', import.meta.url);
const publicDirectory = new URL('../public/', import.meta.url);
await mkdir(publicDirectory, { recursive: true });
await copyFile(icon, new URL('icon.png', publicDirectory));

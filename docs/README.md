# Glue Documentation

The Glue documentation is a VitePress project. Published Markdown lives in `src/content/docs/`.

- `deprecated/` contains the previous documentation for migration reference and is not published.
- `development/` contains contributor guidance and is not part of the public site.

## Requirements

- Node.js 22.12 or newer.
- pnpm 11.5.2 or newer.

## Commands

```shell
pnpm install --frozen-lockfile
pnpm dev
pnpm check
pnpm build
```

Run these commands from `docs/`. The production site is written to `docs/dist/`.

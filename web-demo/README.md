# Temporary Glue Web fixture

This directory preserves the React demo used to validate the browser experiment. It is an independent
Node project, not a Glue module, Gradle input, library resource or published artifact.

## Build manually

```powershell
pnpm --dir web-demo install --frozen-lockfile
pnpm --dir web-demo build
```

The script bundles `src/app.jsx` and React into `dist/app.js`, then generates `dist/index.html` with
the CSS from `src/style.css` inline. `dist/` and `node_modules/` are ignored by Git. No watcher/HMR is
configured. Source changes require another manual build; reloading the browser then reads the output.

The existing showcase loopback server serves `../web-demo/dist` relative to Minecraft's game
directory. F6 or `/jcef demo` opens it; `/jcef browser` opens La Calèche without needing this bundle.
The Minecraft JVM property `glue.showcase.webRoot` can override the directory for a nonstandard run
profile. Missing output produces an explanatory HTTP response instead of requiring Node at runtime.

The page sends JSON strings through `window.glueQuery` and reads strings from `glue:web-message`
events. Java parses the demo payloads in showcase; the library has no React or game-command schema.

`PERFORMANCE.md` archives measurements from the old prototype, including its retired CPU/GPU and
pixel-probe comparisons. The original experiment remains in Git history and on its saved branches.

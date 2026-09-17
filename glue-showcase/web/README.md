# Showcase web interfaces

This is application code for Glue's showcase, built with React 19 and Vite 8. The library's page
bridge remains framework-independent and is imported from `https://glue-web.glue/bridge.js` at
runtime. There is no loopback server, CDN React import or frontend dependency in a library jar.

## Layout

- `public/lab.html`, `vanilla.js`, `vanilla.css`: the self-contained, unbundled HTML/JS input lab.
- `src/hub/`: navigation and layer switches.
- `src/waypoints/`: the manager and its independently hosted removal dialog.
- `src/hud/`: vitals and native hotbar slots.
- `src/minimap/`: the compass and pins above Java's terrain slot.
- `src/inventory/`: field notes in an inventory widget.
- `src/toasts/`: bounded, animated notifications with listener/timer cleanup.
- `src/shared/`: visual tokens, action feedback, page lifecycle and `useGameState`.

Each React page has its own component and stylesheet. The small HTML entry points keep the existing
page URLs. Vite shares React between pages and loads only the selected demo's component and CSS.
`useGameState` uses `useSyncExternalStore` and unsubscribes when its component unmounts. React owns
the DOM; Java owns actions, game state and native rendering. Native slots are ordinary JSX elements
with `data-glue-slot` attributes.

## Build and edit

Use Node `^20.19.0 || >=22.12.0` and pnpm 11.5.2. From the repository root:

```shell
pnpm --dir glue-showcase/web install --frozen-lockfile
pnpm --dir glue-showcase/web build
pnpm --dir glue-showcase/web watch
```

`watch` stays running while you edit. Reload a page with F5 in Minecraft after the rebuild. The
output is `glue-showcase/build/generated/webResources/assets/glue-showcase/web/`, which the development
client serves from `https://glue-showcase.glue/`. The vanilla lab is copied unchanged from `public/`
and can also be used on its own, without React or Vite.

Gradle's `:glue-showcase:processResources` depends on `installWeb` and `buildWeb`, so normal showcase
tests, jars and client runs build these assets automatically. `:glue-web:test`, `:glue-web:remapJar`
and `libraryJars` have no frontend task dependency. Generated bundles and `node_modules/` are ignored.

CI builds the frontend in a Node job and passes the generated resource directory to the Java jobs.
Those jobs exclude only `installWeb` and `buildWeb`, using the already-built assets for tests and jars.

`glue-test:web` exercises the React hub, both kinds of pages, native slots, input, actions, events,
screen stacking and cleanup in the live client. Java packaging tests check the generated HTML's
asset references as well as the unbundled lab files.

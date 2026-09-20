# Glue Web

Client-only web interfaces for Minecraft 1.21.8 / Java 21: screens, HUDs, overlays and widgets
built from local pages, connected to Java through a page bridge.

- Artifact: `fr.lacaleche.glue:glue-web:<version>`
- Fabric mod id: `glue-web`
- Public API: `fr.lacaleche.glue.web` (surfaces), `.app`, `.bridge`, `.host`
- [API guide](https://gitlab.lacaleche.cc/loccamy/java/glue-docs/-/blob/main/src/content/docs/web/index.md)
- [Packaging and development](../development/glue-web.md)

```java
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.host.WebScreen;

WebScreen.builder(WebApp.of("mymod").page("menu.html"))   // assets/mymod/web/menu.html
        .bind(new MenuActions())                          // @WebAction methods
        .state("player", PlayerView::capture)             // pushed when it changes
        .open();
```

```js
import { call, state } from 'https://glue-web.glue/bridge.js';

state('player', player => render(player));
await call('menu.save', { name: 'Base' });
```

- `WebApp` serves `assets/<mod>/web/` from `https://<mod>.glue/`, with no server.
- `@WebAction` exposes Java methods; `state` and `emit` push values and events to the page.
- `slot` and `slotBehind` draw native content, such as items or a map, where the page asks.
- `WebScreen`, `WebHud`, `WebOverlay` and `WebWidget` host pages; `WebSurface` is the primitive.

Hosts run on Minecraft's client thread, size pages to one CSS pixel per web-scale pixel and manage
page lifetime. `WebSettings` holds that scale: pages follow Minecraft's GUI scale unless a player
picks a fixed one in Glue's options page, reached from Minecraft's options screen. The native runtime
is preloaded asynchronously at mod startup, with a discreet progress indicator. The native
distribution and browser profile are stored under the game directory's `glue-web/`. The jar ships the
bridge module and Glue's own options page, and no application page. Gradle builds and tests Glue Web
without Node or pnpm. The showcase demonstrates every host with React components and keeps a
standalone HTML/JS input lab; its Vite build belongs exclusively to `glue-showcase/web/`.

JCEF's installer/helpers and Commons IO are relocated privately. Native `org.cef` JNI names must stay
unchanged. This does not promise coexistence with a second independently initialized CEF wrapper.

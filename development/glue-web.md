# Glue Web development

`glue-web` is the published client library for Chromium-based interfaces. Supported contracts are
in the [public guide](https://gitlab.lacaleche.cc/loccamy/java/glue-docs/-/blob/main/src/content/docs/web/index.md). The implementation is under
`fr.lacaleche.glue.web.internal`; no public signature exposes JCEF or installer types.

The web-uis context, kept with the `occa-uis` frontend workspace, records the current bundle runtime and the proposed producer
workspace, publication workflow and Gradle integration.

## Architecture

| Layer | Types | Responsibility |
|---|---|---|
| Hosts | `host.WebScreen`, `WebHud`, `WebOverlay`, `WebWidget`; `internal.host` | Size a page to the GUI, forward input, decide page lifetime. |
| Zoom | `WebBuilder.zoom`; `internal.host.PageZoom`; `internal.options` | Each page's zoom over the GUI scale, its keys, and the file keeping players' choices. |
| Options | `WebBuilder` | Address, trust, actions, state and slots shared by every host. |
| Surface | `WebSurface`; `internal.browser.BrowserSession` | One native browser, its textures, cursor, input and slots. |
| Bridge | `bridge` public types; `internal.bridge`; `bridge.js` | Origin-checked page messages, actions, state and events. |
| Resources | `app.WebApp`; `internal.app` | `https://<mod>.glue/` served from mod resources. |
| Runtime | `internal.browser.CefRuntime`, `RuntimeStartup`, `RuntimeLoadingOverlay` | Background install, one CEF client, shutdown. |

The shared builder is no longer sealed: sealing forced every derived builder into the root package.
When a host is built it receives a private copy of the common surface-builder options. Later changes
to the caller's builder do not affect that host. Hosts open and operate ordinary `WebSurface`s;
they do not access a surface's `BrowserSession`. `hasFrame` and `onCloseRequest` also let custom hosts
implement readiness and page-requested closure through the public API.

### Bridge protocol

`bridge.js` is served from `assets/glue-web/web/`. It sends JSON through the `glueQuery` message
router with a page-chosen id and a type: `hello`, `call`, `slots` or `close`. `Bridge.accept` runs on
CEF's UI thread: it checks the main frame and origin, bounds the message, parses it and acknowledges
it immediately. Handling happens on the client thread.

Replies, state and events travel back through `executeJavaScript`, calling
`window.__glueBridge.receive`. The JCEF query callback is only used for the acknowledgement: its
native reference is cleared after the first response, so persistent queries cannot stream updates,
and replying from the client thread would add an unverified cross-thread native call.

Each main-frame `onLoadStart` increments the view's document number. `hello` records the number it
arrived with; results, state and events are only evaluated while that number is current, and slots
from another document are ignored. State messages carry an increasing revision, which the page uses
to ignore an older snapshot that arrives late.

### Scale

`CefView` reports the surface scale as the device scale factor and its view rectangle in CSS pixels.
Chromium paints `ceil(size * scale)` pixels. Hosts use `internal.host.HostSizing` to fit surfaces through
the public `resize` operation every frame, and
`wasResized` makes CEF read the screen information again when the size or scale changes. Input,
popup bounds and slots stay in CSS pixels.

The web scale is the GUI scale times the page's zoom. Every page follows the game; the zoom says how
the page was designed relative to it. The default of 1 gives one CSS pixel per GUI pixel, which suits
a page drawn in the game's own units such as the showcase's vitals; a page built with a desktop design
system declares less through `WebBuilder.zoom`. A page always covers its host rectangle on screen, so
`HostSizing` turns the difference into CSS pixels — a zoom below 1 buys a denser page, above it
enlarges one — and reduces that density when the page would pass `WebSurface.MAX_DIMENSION`.
`SurfaceInput` and `Bridge.SlotRect` already map between the rectangle and the page, so nothing else
depends on the two being equal.

The zoom is per page, never global: 2.4 had one scale for every page, which made a dense editor
readable only by shrinking every HUD with it. `WebScreen` and `WebWidget` pass Ctrl +, Ctrl - and
Ctrl 0 (Command on macOS) to `PageZoom` before the page sees them, and `internal.options.SettingsFile`
keeps the player's choice per origin, as a browser keeps it per site. Ctrl 0 forgets it. Layers take
no keys, so a HUD or overlay keeps the zoom its builder declared.

### Local resources

`AppResources.register` runs once, after JCEF reports `INITIALIZED`: CEF rejects scheme handler
factories registered earlier with an invalid-version fatal error. It registers one `https` factory
per loaded mod that ships `assets/<mod>/web/`, or per `glue.web.source.<mod>` directory override.
Handlers read whole files on CEF's IO thread and answer `GET` only.

Named, versioned apps use `BundleApp` and a separate `<app>.<mod>.glue` factory, including apps
registered after CEF startup. `BrowserSession` captures an immutable `BundleApp.Page` at opening;
`CefView` exposes only that routing snapshot to resource handlers. Physical paths identify the mount,
root-relative requests use the browser's snapshot, and service-worker script requests are refused.

`BundleManifest` verifies Ed25519 envelopes and selects an exact application contract. `BundleHttp`
bounds asynchronous HTTP bodies. `BundleStore` owns an exclusive cache lock, bounded extraction and
atomic publication. A `BundleApp` serial worker restores, checks, installs and persists selections;
client/CEF threads only observe immutable snapshots and mounted roots. Shutdown stops network work
and releases the cache lock after queued work unwinds. Installed roots remain available until shutdown
so an update cannot remove files used by old surfaces. See the [protocol and API](https://gitlab.lacaleche.cc/loccamy/java/glue-docs/-/blob/main/src/content/docs/web/bundles.md).

### Lifetime

`HostTicker` runs host checks after every client tick. Screens and widgets close pages whose screen
is neither displayed nor a parent of the displayed web screen. HUDs release their page when the world
is left. `OverlayLayers` draws web overlays in the same `GameRenderer` seam as the runtime indicator,
before it.

## Packaging

The module uses the Shadow plugin already provided by Caldle. Fabric-mode Caldle disables the
default Shadow task, so this module explicitly enables it and selects only the `embedded`
configuration. Minecraft, Fabric and other Glue modules must never be shaded.

- The pinned `jcefgithub:146.0.10.1:all-relocated` wrapper and Commons IO 2.20.0 are embedded.
- The installer and its pre-relocated helpers move under `internal.shaded.jcefgithub`.
- Commons IO moves under `internal.shaded.commonsio`, avoiding Minecraft's older version.
- `org.cef` names remain intact because the native JNI library refers to them. This is not isolation
  of multiple independently initialized native CEF runtimes in the same JVM.
- Upstream resources and per-artifact license/notice copies are retained; minimization is disabled.
- Loom remaps the shaded jar. `namedElements` also serves that shaded implementation, so the showcase
  exercises it rather than an accidental unshaded dependency graph.
- The Maven publication and Gradle metadata expose the remapped artifact with Fabric dependencies,
  not the raw JCEF/Commons IO coordinates or an alternative unremapped Shadow variant.
- The jar contains `bridge.js` and no application page.

## Runtime preloading and indicator

`CefRuntime.bootstrap()` starts the shared background future during client-mod initialization.
`RuntimeStartup` publishes immutable progress snapshots across installer/CEF/client threads and
ignores late updates after completion or shutdown. The installer reports percentages in 0..100,
with -1 for unknown progress. Preload failures are observed/logged even if no surface is ever opened.

The HUD-only events do not cover menus or Minecraft's initial loading overlay. One client mixin
captures `GameRenderer`'s final GUI submission seam; web overlays and `RuntimeLoadingOverlay` append
their own GUI state before submission. They do not grab input, play sounds or access GL directly.
The indicator avoids font access before game loading completes and allocates no GuiGraphics once it
has disappeared.

## Showcase

The frontend lives in `glue-showcase/web/`. `public/` contains a standalone vanilla input lab; `src/`
contains seven React pages grouped by demo, with shared bridge hooks and separate styles. React is
an application dependency, not a Glue API dependency. Vite leaves the runtime bridge import external.

Only showcase resource tasks run pnpm/Vite. Generated files enter
`glue-showcase/build/generated/webResources/assets/glue-showcase/web/`. The client source override
serves that output; Vite's build watcher plus F5 handles live editing. Java demo packages mirror the
features: hub, lab, browser, HUDs, inventory, waypoints, toasts and game tests. See the
[frontend guide](../glue-showcase/web/README.md) for commands and the source map.

## Verification

```powershell
.\gradlew.bat :glue-web:test :glue-showcase:test :glue-web:remapJar :glue-showcase:remapJar
.\gradlew.bat :glue-showcase:runClient '-Pglue.gametest=glue-test:web' '-Pglue.showcase.quickplay=New World'
.\gradlew.bat :glue-showcase:runClient '-Pglue.gametest=glue-test:web-bundles' '-Pglue.showcase.quickplay=New World'
node --test glue-web/tools/publish-bundle.test.mjs
```

Unit tests cover frame ownership, file resolution and traversal, action binding and invocation, the
bridge protocol, trust, slot mapping, layer placement, public signatures and shaded packaging.

`glue-test:web` drives the hub, the input lab, both HUD layers, the toast overlay, the inventory
panel, the stacked waypoint dialog and an untrusted page in the browser screen, with real mouse and
keyboard input. `glue-test:web-sites` is the opt-in internet scenario. `glue-test:web-startup`
inspects the runtime indicator. After a run, check the client log for the module-owned surface's
disposal and CEF `TERMINATED`, not just Gradle's exit code. Native validation has used Windows.

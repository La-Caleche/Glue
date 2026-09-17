---
title: Web Interfaces
description: Build Minecraft screens, HUDs, overlays and widgets from local web pages, with Java actions, live state and native slots.
artifact: glue-web
modId: glue-web
environment: client
---

# Web Interfaces

Glue Web renders Chromium pages inside Minecraft. A mod ships its pages as resources, hosts them as a
screen, a HUD, an overlay or a widget, and connects them to Java through a small page bridge. JCEF and
its installer are implementation details; applications use the types in `fr.lacaleche.glue.web`.

## API Packages

| Package | Use |
|---|---|
| `fr.lacaleche.glue.web` | Raw `WebSurface`, common builder, input values and delivery metrics. |
| `fr.lacaleche.glue.web.app` | `WebApp` and local page addresses. |
| `fr.lacaleche.glue.web.bridge` | `WebAction`, `WebSlot` and `WebSlotRenderer`. |
| `fr.lacaleche.glue.web.host` | `WebScreen`, `WebHud`, `WebOverlay`, `WebWidget` and `WebAnchor`. |

Packages under `internal` are implementation details, grouped by browser, bridge, app resources and
host support.

## Install

```kotlin
dependencies {
    modImplementation("fr.lacaleche.glue:glue-web:<glue-version>")
}
```

Declare `glue-web` in the client mod's `fabric.mod.json` dependencies. It has no direct dependency on
another Glue module. Minecraft, Java 21 and Fabric API are required. Keep this dependency in a
client-only companion mod when the application also supports dedicated servers.

Gradle builds the library without Node or pnpm. Pages can be plain HTML and ES modules, or the output
of any frontend build the application chooses.

## Quick Start

Put a page in the mod's resources:

```html [src/main/resources/assets/mymod/web/waypoints.html]
<!doctype html>
<meta charset="utf-8">
<button id="add">Add a waypoint</button>
<ul id="list"></ul>
<script type="module">
    import { call, state } from 'https://glue-web.glue/bridge.js';

    state('waypoints', waypoints => {
        document.getElementById('list').replaceChildren(...waypoints.map(waypoint => {
            const item = document.createElement('li');
            item.textContent = waypoint.name;
            return item;
        }));
    });

    document.getElementById('add').onclick = () => call('waypoints.create', { name: 'Base' });
</script>
```

Expose Java methods and open the page from the client thread:

```java
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.bridge.WebAction;
import fr.lacaleche.glue.web.host.WebScreen;

public final class WaypointActions {

    @WebAction("waypoints.create")
    Waypoint create(NewWaypoint request) {
        return Waypoints.add(request.name());
    }

    record NewWaypoint(String name) {
    }
}

WebApp app = WebApp.of("mymod");
WebScreen.builder(app.page("waypoints.html"))
        .bind(new WaypointActions())
        .state("waypoints", Waypoints::all)
        .open();
```

No server, port or origin configuration is involved. The screen forwards input, owns the cursor and
closes its page when it stops being displayed.

## Local Pages

Every loaded mod with an `assets/<mod>/web/` directory is served from `https://<mod>.glue/` inside
Glue surfaces. Requests never reach the network. `WebApp.of(modId)` checks that the mod and its web
directory exist, and `page(path)` returns an address inside that origin. A path ending with `/`, or
naming a directory, serves its `index.html`.

- Each mod has its own origin, so its storage, cookies and bridge trust are separate.
- Underscores in mod ids become a double hyphen: `my_mod` is served from `https://my--mod.glue/`.
- Only `GET` is served. Files are read whole, so range requests and streaming are not provided; host
  large media elsewhere.
- Responses are uncached and allow cross-origin reads, which lets any trusted page import the bridge.
- Resource packs do not override web files.

During development, `-Dglue.web.source.<mod>=<directory>` serves a directory instead of the packaged
files. With it, editing a page only needs a page reload; the showcase sets it for its run
configuration and reloads pages with F5. Its React pages must be rebuilt first; the Vite watcher
described in the showcase frontend README does that while editing.

## Page Bridge

Pages import the bridge from Glue Web's own origin:

```js
import { call, close, on, ready, snapshot, state } from 'https://glue-web.glue/bridge.js';
```

The bridge connects once per document. The host accepts it only from its trusted origin and only in
the main frame. It then publishes its state and accepts calls.

| Export | Behavior |
|---|---|
| `ready` | Promise that resolves after the host accepted the document and sent its initial state. It rejects outside a Glue surface or for an untrusted page. |
| `call(name, payload)` | Calls a Java action with a JSON payload and resolves with its JSON result. |
| `state(key, listener)` | Listens to a state key. A known value is delivered first. Returns an unsubscribe function. |
| `snapshot(key)` | Latest value of a state key, or `undefined`. |
| `on(event, listener)` | Listens to events sent by Java. Returns an unsubscribe function. |
| `close()` | Asks the host to close. Screens and widgets support it. |

Failed calls reject with an `Error` whose `name` is `GlueBridgeError` and whose `code` identifies
the reason:

| Code | Reason |
|---|---|
| 1 | The page's origin or frame is not trusted. |
| 2 | The message is malformed. |
| 3 | Too many messages are waiting for the client thread. |
| 4 | No action has that name. |
| 5 | The action threw; the message is the exception's message. |
| 6 | The host does not support the request, such as `close()` on a HUD. |
| 7 | The surface is closed. |
| 8 | A message or result exceeds its size limit. |

Page messages are limited to 65,536 characters, results and pushed values to 1 MiB of JSON, and 256
messages may wait for the client thread at once. A page that navigates away loses its connection;
the new document connects again if it imports the bridge.

### React Applications

The same bridge works with React. Keep it external in the application's bundler and subscribe to its
state as an external store:

```jsx
import { useCallback, useSyncExternalStore } from 'react';
import { snapshot, state } from 'https://glue-web.glue/bridge.js';

function useGameState(key) {
    const subscribe = useCallback(listener => state(key, listener), [key]);
    const read = useCallback(() => snapshot(key), [key]);
    return useSyncExternalStore(subscribe, read);
}

function Health() {
    const player = useGameState('player');
    return <span>{player ? `${player.health} HP` : 'Connecting…'}</span>;
}
```

React disposes the subscription when the component unmounts. Subscribe to events in an effect and
return `on(...)`'s unsubscribe function; clear any application timers on unmount as well. Native slots
work directly in JSX, for example `<div data-glue-slot="item:0" />`. React and its build tooling belong
to the application. The showcase pairs an unbundled vanilla lab with React examples of every host.

## Actions

`@WebAction` exposes a method. Bind the object that declares it on any host builder with
`bind(handler)`:

```java
final class ShopActions {

    @WebAction("shop.buy")
    Receipt buy(Purchase purchase, WebSurface surface) {
        if (purchase.quantity() < 1) throw new IllegalArgumentException("Buy at least one item");
        return Shop.buy(purchase);
    }

    @WebAction
    CompletableFuture<List<Offer>> offers() {
        return Shop.fetchOffers();
    }
}
```

- The name defaults to the method name. Names start with a letter and may contain letters, digits,
  `.`, `_`, `:` and `-`.
- Methods run on Minecraft's client thread and may be private or static.
- A method takes at most one payload parameter, decoded with Gson; records work. It may also take a
  `WebSurface` parameter that receives the calling surface.
- The return value is encoded with Gson. `void` and `null` become JSON `null`. A `CompletionStage`
  settles the page promise when it completes.
- A thrown exception rejects the promise with code 5 and the exception's message. Argument and state
  exceptions are treated as expected refusals; other exceptions are also logged.
- Binding validates signatures and rejects duplicate names immediately.

## State and Events

`state(key, supplier)` publishes a value. Glue samples the supplier after every client tick while a
page is connected, encodes it as JSON and sends it only when it changed. The first sample is part of
the connection, so a page never waits a tick for its initial values. Sampling failures are logged
once per key until the key succeeds again.

Keep state values small and round noisy numbers: every change is serialized and sent. Use events for
one-off notifications:

```java
boolean delivered = screen.emit("quest.completed", new QuestView(quest));
```

Every host has `emit(event, data)`. It returns false, without queuing, while no page is connected.
Invalid names and values above 1 MiB of JSON throw `IllegalArgumentException`.

## Native Slots

A page reserves space for native content by marking elements with `data-glue-slot`. The value is a
slot name, optionally followed by `:` and an argument:

```html
<div class="tile" data-glue-slot="item:3"></div>
<div class="map" data-glue-slot="terrain"></div>
```

The host registers a renderer per name. `slot` draws above the page; `slotBehind` draws beneath it,
so the page must leave that element transparent and can decorate around it:

```java
WebHud.builder(id("hotbar"), app.page("hud.html"))
        .slot("item", (graphics, slot) -> {
            ItemStack stack = player().getInventory().getItem(Integer.parseInt(slot.argument()));
            graphics.renderItem(stack, slot.x() + (slot.width() - 16) / 2, slot.y() + (slot.height() - 16) / 2);
        })
        .slotBehind("terrain", (graphics, slot) -> Minimap.draw(graphics, slot.x(), slot.y(), slot.width(), slot.height()))
        .register();
```

The bridge measures visible marked elements on every animation frame and reports changes. Renderers
receive `WebSlot` rectangles in GUI coordinates, clipped to the surface, with pose changes restored.
`surface.slots()` returns the slots of the latest draw for hit testing, such as item tooltips. Slot
positions follow CSS animations with up to one frame of delay. Elements whose name has no renderer
are ignored.

## Hosts

All host builders share the options above: `frameRate`, `transparent`, `trust`, `withoutBridge`,
`bind`, `state`, `slot` and `slotBehind`. Hosts size their page to their GUI rectangle and use the
GUI scale as the page's device pixel ratio, so one CSS pixel is one GUI pixel and text stays sharp.
The page follows window and GUI scale changes. A page larger than 4096 browser pixels in either
direction is rendered at a lower ratio.

### WebScreen

A full-window screen. It forwards pointer, wheel, keyboard and text input, and owns the cursor while
displayed.

```java
WebScreen.builder(app.page("menu.html"))
        .title(Component.literal("Menu"))
        .pausesGame(false)
        .closeOnEscape(true)
        .background(false)
        .open();
```

`open()` shows the screen over the current screen, which becomes its parent; `build()` and
`build(parent)` create it without showing it. Closing returns to the parent. Escape closes the screen
unless the page shows a native popup such as a select list, or `closeOnEscape(false)` is set.

A screen keeps its page while it is displayed or is the parent of the displayed web screen. Web
screens therefore stack: a dialog opened from a page returns to that page unchanged. A transparent
screen draws its parent web screens beneath itself. Once a screen is neither displayed nor such a
parent, its page closes after the next client tick; showing the screen again opens a new page.

### WebHud

A passive page in the gameplay HUD. It never takes input and opens on the first HUD frame in a world.
It closes when the world is left.

```java
WebHud hud = WebHud.builder(id("vitals"), app.page("hud.html"))
        .replaces(VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
                VanillaHudElements.ARMOR_BAR, VanillaHudElements.AIR_BAR)
        .when(() -> !player().isSpectator())
        .state("vitals", Vitals::capture)
        .register();
```

- `replaces(...)` hosts the page in the first element and hides the others. The vanilla elements keep
  rendering until the page has painted and, when the HUD declares actions, state or slots, connected
  its bridge. They also return while the HUD is disabled, while its condition is false, or after its
  page failed.
- `after(element)` or `before(element)` attaches the page next to a vanilla element and inherits its
  render condition. Without a placement, the page is drawn last and hidden with the HUD.
- `anchor(anchor, width, height)` sizes the page instead of covering the GUI; `offset(x, y)` moves it
  inward from its edges.
- `when(condition)` hides the page while the condition is false, without closing it.
- `setEnabled(false)` closes the page and restores replaced elements. `isShowing()` reports whether
  the page currently replaces them.

Register HUDs during client initialization. Status bars that other mods position against vanilla
heights may need a `HudStatusBarHeightRegistry` provider when a HUD replaces them.

### WebOverlay

A passive page above every screen, menu and loading overlay, in and out of worlds. It opens on the
first frame after the game has loaded and stays open until it is disabled or the client stops.

```java
WebOverlay toasts = WebOverlay.builder(app.page("toasts.html"))
        .anchor(WebAnchor.BOTTOM_RIGHT, 188, 150)
        .offset(4, 4)
        .register();

toasts.emit("toast", new Toast("Saved", "Your base is on the map."));
```

It supports `anchor`, `offset`, `when`, `setEnabled`, `isShowing` and `emit`. An idle transparent
page repaints nothing, so a toast overlay costs little between notifications. Register overlays
during client initialization.

### WebWidget

A page inside any screen, as an ordinary widget:

```java
private final WebWidget notes = WebWidget.builder(app.page("notes.html"))
        .title(Component.literal("Notes"))
        .bind(new NotesActions())
        .build(8, 32, 140, 180);

@Override
protected void init() {
    this.addRenderableWidget(this.notes);
}
```

The widget takes keyboard input while focused and pointer input over its rectangle. Escape stays with
the screen. Its page opens when it is first rendered and belongs to the screen displayed at that
moment. It closes after the next client tick once that screen is neither displayed nor the parent of
the displayed web screen, or after its displayed screen has not rendered it for five seconds. Rendering the widget again
opens a new page, except after the page closed by itself, for example after a runtime failure.
Create the widget once and add it again in `init()`. The page's `close()` closes the owning screen.

## Trust

The bridge is available to one origin per host:

- Addresses from `WebApp` are trusted by default.
- Any other address has no bridge until `trust(origin)` names an HTTP(S) origin. Origin comparison
  normalizes scheme and host case and default ports; paths do not restrict access, and different ports
  or subdomains do not match.
- `withoutBridge()` refuses the bridge to every page, including app pages. Hosts that let users open
  arbitrary addresses use it. It cannot be combined with actions, state or slots.

Only the main frame may use the bridge. A document that navigates to another origin loses access
immediately. Results, state and events are delivered only to the document that connected.

## Raw Surfaces

`WebSurface` is the primitive every host uses. Applications can draw it in their own renderers:

```java
WebSurface surface = WebSurface.builder(URI.create("https://example.org/"))
        .size(512, 384)
        .scale(2)
        .frameRate(60)
        .transparent(true)
        .open();

surface.draw(graphics, 20, 40, 512, 384);
```

The builder defaults to an 800x600 page, a scale of 1, 60 FPS and transparency. Sizes are CSS pixels
in `1..4096`, the scaled size must stay within 4096 browser pixels, and the frame cap is in `1..120`.
Builder options are copied at `open()`; reusing a builder opens distinct surfaces.

All instance methods belong to the client thread. Futures may complete elsewhere; marshal
continuations that touch Minecraft through `thenAcceptAsync(..., Minecraft.getInstance())`.
`ready()` completes when the native browser exists, not when the document has loaded; use
`isLoading()` as well. `close()` is idempotent, releases GPU and cursor resources immediately and
requests native disposal; `stopped()` acknowledges it. Operations on closed surfaces are rejected,
while status and metrics stay readable. Fabric closes outstanding surfaces before shutting down CEF.

Custom hosts can use `hasFrame()` to wait for a complete paint before replacing existing content,
and `onCloseRequest(handler)` to handle the trusted page's `close()` on the client thread. Without a
handler, the page's close request is refused. These operations do not expose the native session.

Input uses CSS pixels. Convert GUI coordinates with the drawn rectangle:

```java
int pageX = (int) ((mouseX - 20) * surface.width() / 512);
int pageY = (int) ((mouseY - 40) * surface.height() / 384);
surface.mouse(WebPointerEvent.MOVED, pageX, pageY, -1, modifiers, 1);
```

- `WebPointerEvent` has `MOVED`, `EXITED`, `PRESSED`, `RELEASED` and `DRAGGED`. Buttons are GLFW
  left, right and middle (`0/1/2`); movement and exit use `-1`. Modifiers use GLFW bits, and click
  counts are positive.
- `setFocused`, `keyPressed`, `keyReleased` and `character` handle keyboard focus, GLFW keys and
  committed text. `wheel` uses Chromium's 120-unit notches.
- `resize(width, height)` keeps the scale; `resize(width, height, scale)` changes both.
- `setCursorActive(active)` claims the window cursor. Call it on every render with the host's hover
  or drag ownership. `cursor()` reports the page's request and `appliedCursor()` the shape this
  surface currently owns. Inactive windows, loading overlays and a grabbed mouse always release it.
  Standard shapes are supported; others fall back to the arrow.
- `navigate`, `back`, `forward`, `reload` and `stopLoading` control the page; `url`, `title`,
  `canBack`, `canForward`, `hasPopup` and `error` expose its state. New-window requests load in the
  same surface; native select popups are composited by the surface.
- `evaluate(script)` returns JSON-encoded by-value output; JavaScript failures complete it
  exceptionally. `screenshot()` returns a browser-resolution image, including the popup.
- `metrics()` returns a one-second sample of callback and upload rates, CPU copy durations, dirty area
  and cumulative frames. `frameAgeMs()` measures the age of the last uploaded paint. These are not GPU
  timings or input-to-photon latency.

## Runtime and Packaging

Mod initialization downloads, installs and starts the pinned native runtime in the background,
without waiting for the first surface or blocking Minecraft startup. It is stored under
`<game directory>/glue-web/146.0.10.1/<platform>/`; the Chromium profile is separate at
`<game directory>/glue-web/profile/`. Surfaces opened while it starts share the same startup.
`WebSurface.runtimeStatus()` and `surface.error()` expose progress and failures.

A noninteractive indicator appears at the bottom right while checking, downloading, extracting,
installing or starting the runtime. It shows the phase and a percentage when the installer provides
one. It renders above menus, inventories, overlays and web overlays, including when the HUD is hidden.
During Minecraft's initial loading screen, only the bar is drawn. English and French labels follow the
selected language. Success fades out within two seconds; a failure is logged and shown for eight
seconds without blocking the game.

The pinned implementation is JCEF `146.0.10.1` / Chromium `146.0.7680.179`. Its Java installer, its
helpers and Commons IO are privately shaded in the Glue jar. Native `org.cef` class names are fixed by
JNI and remain intact. Running another independently initialized JCEF wrapper in the same JVM is not
a supported arrangement.

The transport uses CPU paint buffers, accumulated damage and partial Blaze3D uploads. The shader
swaps BGRA channels and blends premultiplied alpha; this is not a shared-texture transport. Each
surface keeps a full CPU image and a GPU texture at browser resolution, and Chromium may run a
renderer process per surface: prefer one page with several regions over many small surfaces of the
same mod.

Windows is the native-tested platform. Other native distributions and keyboard layouts still need
validation. Custom-image and hidden CSS cursors, IME composition, OS drag-and-drop, browser context
menus, downloads and pointer lock are not provided.

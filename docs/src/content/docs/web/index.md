---
title: Browser Surfaces
description: Embed Chromium through a client-owned Glue surface, with native input and explicit page messaging.
artifact: glue-web
modId: glue-web
environment: client
---

# Browser Surfaces

Glue Web renders Chromium pages into GUI rectangles. Each `WebSurface` owns its browser, image
mailboxes, GPU textures and cursor handles. JCEF and its installer are implementation details;
applications use the types in `fr.lacaleche.glue.web`.

## Install

```kotlin
dependencies {
    modImplementation("fr.lacaleche.glue:glue-web:<glue-version>")
}
```

Declare `glue-web` in the client mod's `fabric.mod.json` dependencies. It has no direct dependency on
another Glue module. Minecraft, Java 21 and Fabric API are required. Keep this dependency in a
client-only companion mod when the application also supports dedicated servers.

Gradle builds the library without Node or pnpm. Application pages can be built/hosted independently;
the library neither compiles a frontend nor starts an HTTP server.

## Open and Own a Surface

From Minecraft's client thread:

```java
import fr.lacaleche.glue.web.WebSurface;
import java.net.URI;

WebSurface surface = WebSurface.builder(URI.create("https://example.org/"))
        .size(1024, 768)
        .frameRate(60)
        .transparent(true)
        .open();
```

The builder defaults to 800x600 pixels, 60 FPS and transparency enabled. Each dimension must be in
`1..4096`, and the frame cap in `1..120`. Addresses must be absolute URIs. Builder changes are copied
at `open()`; reusing a builder opens distinct surfaces.

Mod initialization starts downloading/installing and initializing the pinned native runtime in the
background, without waiting for the first surface or blocking Minecraft startup. It is stored under
`<game directory>/glue-web/146.0.10.1/<platform>/`. The Chromium profile is separate at
`<game directory>/glue-web/profile/`. Later surfaces reuse the process runtime. Use
`WebSurface.runtimeStatus()` and `surface.error()` to display startup progress or failure.

A noninteractive indicator appears at the bottom right while checking, downloading, extracting,
installing or starting the runtime. It includes the current phase and a percentage when the native
installer provides one; otherwise the bar is animated. It renders in the global GUI pass, above
menus, inventory and loading overlays, and also works when the normal gameplay HUD is hidden.
During the initial Minecraft loading screen, only the bar is drawn until game fonts are ready.
English and French labels follow Minecraft's selected language. Success fades out within two seconds;
a startup failure is logged and shown briefly for eight seconds, without blocking normal gameplay.

Surfaces opened while preloading share the same pending startup. Once it completes, opening a HUD or
screen only needs its browser instance and page; page/network loading remains asynchronous.

`ready()` is a defensive future for native creation/configuration, completed on the client thread.
It does not mean that the document has loaded: use `isLoading()` as well. Observe futures without
blocking the client thread. All instance methods, including status and metrics access, belong to the
client thread. Futures may be observed elsewhere; marshal continuations that touch Minecraft through
`thenAcceptAsync(..., Minecraft.getInstance())` rather than assuming their completion thread.
The host normally keeps the surface in a field and calls `close()` when
its screen/widget is removed:

```java
surface.close();
```

Close is idempotent, immediately releases GPU/cursor resources and requests native disposal.
`stopped()` acknowledges native disposal or cancellation before acquisition. Operations on closed
surfaces are rejected; read-only status/metrics remain available and deactivating the cursor is safe.
Fabric automatically registers rendering and closes outstanding surfaces before shutting down CEF.
Applications should not initialize or dispose JCEF themselves.

## Draw and Forward Input

Browser resolution and GUI destination are independent. For a 1024x768 browser, draw at half size:

```java
surface.draw(graphics, 20, 40, 512, 384);
```

Convert GUI mouse coordinates into browser pixels using the same destination:

```java
int browserX = (int) ((mouseX - 20) * 1024 / 512);
int browserY = (int) ((mouseY - 40) * 768 / 384);
surface.mouse(WebPointerEvent.MOVED, browserX, browserY, -1, modifiers, 1);
```

`WebPointerEvent` has `MOVED`, `EXITED`, `PRESSED`, `RELEASED` and `DRAGGED`. Buttons are GLFW
left/right/middle (`0/1/2`); movement and exit use `-1`. Modifiers use GLFW bits, and click count is
positive. The surface tracks held buttons. Keep routing release/drag events after the pointer leaves
the rectangle if the host captured that interaction.

- `setFocused(true/false)` selects keyboard focus; false also releases tracked buttons and cursor ownership.
- `keyPressed(key, scanCode, modifiers)` and `keyReleased(key, scanCode, modifiers)` accept GLFW key events.
- `character(character, modifiers)` supplies committed UTF-16 text separately.
- `wheel(x, y, modifiers, deltaX, deltaY)` uses Chromium's 120-unit wheel notches.
- `resize(width, height)` changes browser resolution; `setFpsLimit(fps)` changes its cap.

Return handled input from the enclosing Minecraft screen so the game does not also consume it.
Keyboard, pointer, resize, draw and lifecycle mutations are client-thread operations. Native paint
callbacks only capture owned pixels and metadata; they never touch Minecraft or GL.

## Cursor Ownership

Call `setCursorActive(hoveredOrDragging)` on every host render, including when the pointer is stationary.
Chromium can change CSS cursors asynchronously. `cursor()` reports its requested `WebCursor` and
`appliedCursor()` reports the shape this surface currently owns.

The host must release ownership when another screen/control owns input. The library also releases
for inactive windows, Minecraft loading overlays or a grabbed mouse. Closing an inactive surface
does not reset another surface's cursor. Standard shapes include arrow, hand, text, crosshair,
directional resize and move; unavailable shapes fall back to the arrow.

## Page Messaging

Messaging is disabled by default. Enable a specific HTTP(S) origin and a client-thread string handler:

```java
WebSurface surface = WebSurface.builder(URI.create("http://127.0.0.1:8080/index.html"))
        .onMessage(URI.create("http://127.0.0.1:8080"), message -> handlePageMessage(message))
        .open();
```

The page sends a string with:

```js
window.glueQuery({
  request: JSON.stringify({ action: 'submit', value: 42 }),
  onSuccess() { /* accepted into the Java delivery queue */ },
  onFailure(code, message) { console.error(message); }
});
```

This is one-way delivery, not an RPC result: acknowledgement means the message was accepted into the
queue. Java handlers run on the client tick. Handler exceptions are logged, and other messages can
continue. Close stops delivery and clears native message state.

Java sends strings back with `surface.postMessage(message)`. The page listens for:

```js
window.addEventListener('glue:web-message', event => {
  const message = JSON.parse(event.detail); // JSON is an application choice.
});
```

`postMessage` returns false while the browser/document is not ready or its current URL is outside
the configured origin. It does not retain/replay messages across navigation. Both directions limit
strings to 65,536 UTF-16 code units; the incoming queue holds 128 messages, drained at most 128 per
tick. Persistent CEF queries are rejected. Origin comparison normalizes scheme/host case and default
ports; paths do not restrict origin access, and different ports or subdomains do not match.

## Navigation, Evaluation and Captures

`navigate(URI)`, `back()`, `forward()`, `reload()` and `stopLoading()` control the page. Navigation
requires native readiness. `url()`, `title()`, `canBack()`, `canForward()` and `error()` expose state.
New-window requests load in the current surface; native select popups are captured/composited by the
surface and reported by `hasPopup()`.

`evaluate(script)` returns a future containing JSON-encoded by-value output. JavaScript failures
complete it exceptionally; undefined output is `"null"`. `screenshot()` returns an owned
`BufferedImage` at native browser resolution, including the popup. It fails until a paint exists.
Neither operation is the ordinary input or frame-delivery path.

`metrics()` returns a one-second `WebMetrics` sample of callback/upload rates, capture/staging/upload
CPU durations, dirty area and cumulative coalesced/uploaded frames. `frameAgeMs()` measures the age of
the last uploaded paint. These values are not GPU timings or physical input-to-photon latency.

## Runtime and Packaging Boundaries

The pinned implementation is JCEF `146.0.10.1` / Chromium `146.0.7680.179`. Its Java installer/helpers
and Commons IO are privately shaded in the Glue jar. Native `org.cef` class names are fixed by JNI
and remain intact. Running another independently initialized JCEF wrapper in the same JVM is not a
supported isolation arrangement.

The transport uses CPU `onPaint` buffers, accumulated damage and partial Blaze3D uploads. The shader
swaps BGRA channels and blends premultiplied alpha; this is not shared-texture/zero-copy transport.
Direct-buffer reclamation is JVM-managed after owned references are released.

Windows is the native-tested platform. Other native distributions and keyboard layouts still need
validation. Custom-image/hidden CSS cursors, IME composition, OS drag-and-drop, browser context menus,
downloads and pointer lock are not provided by this host. No browser chrome, test page, benchmark
probe or HTTP fixture server is included in the library.

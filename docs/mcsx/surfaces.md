# External GPU Surfaces & Cursors

## External surfaces

An **external surface** embeds an externally-rendered GPU texture — a 3D scene, a particle preview, a
VFX viewport — as a regular ModernUI View inside your UI. The UI composites *over* it, so dialogs and
overlays paint on top while your scene shows through.

The pieces:

- **`SurfaceSource`** — your render callback; produces the texture each frame.
- **`ExternalSurfaceView`** — the View that reserves the region and routes gestures.
- **`ExternalSurfaceHost`** — the render-thread compositor that blits your texture below the UI.
- **`SurfaceGestureListener`** — optional pointer callbacks in view-local pixels.

### `SurfaceSource`

```java
@FunctionalInterface
public interface SurfaceSource {
    GpuTextureView render(int widthPx, int heightPx, float deltaTick);
}
```

Called **on the render thread**, once per frame, just before the blit. Sizes are in framebuffer
pixels; `deltaTick` is the Minecraft partial-tick delta. Return `null` to skip the blit this frame.

### Wiring one up

A surface is a [native component](native-components.md) — register it under any tag you like and
return an `ExternalSurfaceView`:

```java
ComponentRegistry registry = StandardComponents.create()
        .register("surface", (context, element, binder) -> {
            DemoSurfaceSource source = new DemoSurfaceSource();
            ExternalSurfaceView view = new ExternalSurfaceView(context, source);
            view.setGestureListener(source);
            return view;
        });
```

```xml
<div class="relative grow h-0 rounded-md bg-surface-3">
    <surface class="absolute inset-0"/>
    <div class="absolute left-3 bottom-3">
        <text class="text-xs text-muted">LMB Orbit · scroll Zoom</text>
    </div>
</div>
```

`ExternalSurfaceView` self-registers with the host on attach, punches a transparent hole in the UI
(via `BlendMode.CLEAR`) so the texture shows through, publishes its window bounds, and shows the
`move` cursor over itself. Position it with `absolute inset-0` inside a `relative` container and layer
UI chrome on top with more `absolute` children.

### `SurfaceGestureListener`

All methods are `default` (implement only what you need); coordinates are view-local pixels:

```java
default boolean onSurfaceScroll(float amount, float x, float y); // return true if handled
default boolean onSurfaceDown(float x, float y, int button);     // return true to begin drag tracking
default void    onSurfaceMove(float x, float y);
default void    onSurfaceUp();
default void    onSurfaceHover(float x, float y);                // passive motion, no button
default void    onSurfaceHoverExit();
```

### Reference: `DemoSurfaceSource`

The testmod's `DemoSurfaceSource` implements **both** `SurfaceSource` and `SurfaceGestureListener`. It
lazily allocates a `TextureTarget` sized to the view (resizing on change), clears it to a
time-cycling color, and returns its color texture view. Drag shifts hue; scroll adjusts brightness.

The pattern it demonstrates — follow it:

- **All GPU work lives in `render`** (render thread). The **constructor allocates nothing**, so the
  component is safe to build headlessly (e.g. under the linter) or off-thread.
- **Lazily create and resize** your GPU target inside `render`, keyed on the passed width/height.
- **Cross-thread fields are `volatile`** — gesture callbacks arrive on the UI thread; `render` reads
  on the render thread.

### `ExternalSurfaceHost`

A render-thread singleton. Views register/unregister and publish bounds (UI thread). Once per frame,
before the UI layer composites, it calls each source's `render(w, h, delta)` and blits any non-null
texture into the view's window rect — scaled by `1/guiScale`, with UVs V-flipped (offscreen targets
are lower-left origin, the GUI is upper-left). You don't call it directly.

## Cursors

Minecraft 1.21.8 has no cursor API and ModernUI exposes only ARROW/HAND/TEXT, so MCSX adds custom
GLFW cursors via `Cursors`:

```java
Cursors.hand();
Cursors.resizeEW();    // ↔
Cursors.resizeNS();    // ↕
Cursors.resizeNWSE();  // ⤡
Cursors.resizeNESW();  // ⤢
Cursors.move();        // all-directions move (used by ExternalSurfaceView)
```

Each returns a ModernUI `PointerIcon`, created lazily and cached on the UI thread. If reflection or
GLFW is unavailable, they degrade gracefully to the default cursor — so they're safe to call from view
code without guarding. `handleFor(int type, long fallback)` is a host hook that returns the raw GLFW
handle for a resolved pointer type.

## Gotchas

- **`SurfaceSource.render` is render-thread; do all GPU work there.** Never allocate GPU resources in a
  native component's constructor — allocate lazily on first `render`.
- **Publish cross-thread state safely** (`volatile` or equivalent) — gestures are UI-thread, render is
  render-thread.
- **Layer UI over a surface with `absolute`** — put the `<surface>` at `absolute inset-0` and stack
  chrome above it; the UI composites over the texture automatically.
- **Cursors are lazy/UI-thread and self-degrading** — no guard needed.

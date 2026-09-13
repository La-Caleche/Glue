---
title: Interaction
description: Handle Dockspace gestures, focus, theming, viewport integration, and teardown as separate tasks.
artifact: glue-mcsx-dock
modId: glue-mcsx-dock
environment: client
---

# Interaction

Once Scene, Inspector, and Log mount and restore correctly, add interaction one responsibility at a
time. Gestures, focus, visual theme, embedded gameplay, and closure have different owners.

## Outcome

Users can rearrange and focus the workspace, recognize valid drop targets, hand input to the running
game inside Scene, and return or close without leaked viewport or cursor ownership.

## Move Tabs and Resize

Click a tab to activate its retained content. Drag it within one strip to reorder, into another strip
to insert, onto a guide to split, or away from valid targets to create an in-stage floating window.
Drag a splitter to resize its two visually adjacent children.

<DocImage title="Tab drag and focus" description="Capture a tab drag from Log toward Inspector with the insertion caret and valid drop guide visible, followed by Inspector focused with its editable field active; annotate which input stays with Dockspace." />

The drag ghost, insertion caret, guide, and placement preview show the operation that will commit on
release. A completed gesture updates `layout()` and schedules persistence. During pointer movement,
the semantic callback does not fire on every frame.

## Focus a Pane

Use runtime focus for application navigation rather than reaching into internal Views:

```java
workspace.focusPane("inspector");
workspace.maximizePane("scene");

if (workspace.maximizedPane().isPresent()) {
    workspace.restoreMaximizedPane();
}
```

`focusPane` activates and focuses an open pane; it does not reopen a closed one. Maximize reparents
the same content and trailing-header Views into a stage-sized overlay. It is transient presentation
state, is not serialized, and does not call `onLayoutChanged`.

With an ungrabbed cursor and no Minecraft screen, Dockspace owns pointer input and sees keyboard
events first. ModernUI controls keep consumed keys. Terminally unhandled press, repeat, and release
events continue through Minecraft's real keyboard path, preserving normal and rebound keybindings.
Printable keys remain with a focused `EditText`.

For a Scene pane that should hand control to vanilla gameplay, call:

```java
GameFocus.request(); // Client-thread handoff is scheduled; vanilla grabs input.
GameFocus.release(); // Return pointer and keyboard ownership to Dockspace.
```

Both calls are safe from any thread and are scoped to the workspace mounted when called. Do not
emulate movement or invoke keybindings from the pane; after `request()`, vanilla owns its normal input
pipeline.

## Theme the Workspace

Apply a normal MCSX theme value to the Dockspace builder:

```java
private static final ResourceLocation INTERFACE =
        ResourceLocation.fromNamespaceAndPath("lightworkshop", "interface");

Dockspace workspace = Dockspace.builder(INTERFACE)
        .theme(Themes.resource(INTERFACE))
        // panes, layout, callbacks...
        .build();
```

The builder already uses `Themes.mcsx()`. Add a resource theme only when the application needs a
semantic override; this neutral example preserves the default borderless dock grammar:

```json [assets/lightworkshop/mcsx/themes/interface.json]
{
  "values": {
    "dock-background": "#0b0c0d",
    "dock-pane-background": "@surface",
    "dock-header-background": "@surface-header",
    "dock-active-tab-background": "@surface-hover",
    "dock-drag-ghost-background": {"color": "@surface-raised", "alpha": 232},
    "dock-drop-highlight": {"color": "@control-on", "alpha": 92}
  }
}
```

The drag ghost and drop highlight are independent color tokens; changing the active-tab token does
not recompute them. Resource references make that relationship explicit.

## Embed the Game Viewport

An embedded live Scene needs both Dockspace and `glue-render`. Add the render artifact and its Fabric
dependency separately; it is not brought by `glue-mcsx-dock`.

```kotlin [build.gradle.kts]
dependencies {
    modImplementation("fr.lacaleche.glue:glue-mcsx-dock:<glue-version>")
    modImplementation("fr.lacaleche.glue:glue-render:<glue-version>")
}
```

Also merge the directly required client mod ID into the client-only descriptor:

```json [fabric.mod.json]
{
  "depends": {
    "glue-render": "<glue-version>"
  }
}
```

Four pieces must agree:

1. `DockPane.transparent(true)` leaves the active pane content unpainted.
2. Transparent `DOCK_BACKGROUND` lets the world show through the host.
3. `GameViewport` confines world, HUD, and Minecraft screens to the pane rectangle.
4. `GameSurface` gives an open Minecraft screen pointer ownership inside that rectangle only.

<DocImage title="Embedded Scene viewport" description="Capture the Light Workshop with the live world, HUD, or an open Minecraft screen confined to the Scene pane; show opaque Inspector and Log chrome outside it and indicate the pointer ownership boundary." />

Use one claim owner so delayed View detach cannot clear a newer publisher:

```java [ViewportClaims.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.client.viewport.GameViewport;
import fr.lacaleche.glue.mcsx.client.GameSurface;

import java.util.Objects;

public final class ViewportClaims {

    private Object owner;

    public synchronized void publish(Object owner, int x, int y, int width, int height) {
        this.owner = Objects.requireNonNull(owner, "owner");
        GameViewport.set(new GameViewport.Bounds(x, y, width, height));
        GameSurface.set(new GameSurface.Bounds(x, y, width, height));
    }

    public synchronized void clear(Object owner) {
        if (this.owner != owner) return;
        this.clearAll();
    }

    public synchronized void clearAll() {
        this.owner = null;
        GameViewport.clear();
        GameSurface.clear();
    }
}
```

The custom View is complete. It publishes during pre-draw, clears when hidden, defers detach cleanup
across Dockspace reparenting, and requests normal game focus when clicked:

```java [WorkshopViewportView.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.mcsx.client.Cursors;
import fr.lacaleche.glue.mcsx.client.GameFocus;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewTreeObserver;

public final class WorkshopViewportView extends View {

    private final ViewportClaims claims;
    private final ViewTreeObserver.OnPreDrawListener publisher = () -> {
        this.publishBounds();
        return true;
    };

    public WorkshopViewportView(Context context, ViewportClaims claims) {
        super(context);
        this.claims = claims;
        this.setFocusable(true);
        this.setOnClickListener(view -> {
            this.requestFocus();
            GameFocus.request();
        });
        Cursors.set(this, Cursors.crosshair());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.getViewTreeObserver().addOnPreDrawListener(this.publisher);
    }

    @Override
    protected void onDetachedFromWindow() {
        this.getViewTreeObserver().removeOnPreDrawListener(this.publisher);
        super.onDetachedFromWindow();
        Core.postOnUiThread(() -> {
            if (!this.isAttachedToWindow()) this.claims.clear(this);
        });
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) this.claims.clear(this);
    }

    private void publishBounds() {
        int width = this.getWidth();
        int height = this.getHeight();
        if (width <= 0 || height <= 0 || !this.isShown()) {
            this.claims.clear(this);
            return;
        }

        int[] location = new int[2];
        this.getLocationInWindow(location);
        this.claims.publish(this, location[0], location[1], width, height);
    }
}
```

Wire the View, transparent pane, release control, transparent host token, and synchronous unmount:

```java
private final ViewportClaims viewportClaims = new ViewportClaims();

DockPane scene = DockPane.builderLiteral(
                "scene",
                "Scene",
                ui -> new WorkshopViewportView(ui.context(), this.viewportClaims)
        )
        .closable(false)
        .transparent(true)
        .trailingHeaderUi(ui -> ui.row(
                ui.literalSecondaryButton("Release", GameFocus::release)
        ))
        .build();

Dockspace workspace = Dockspace.builder(INTERFACE)
        .pane(scene)
        .theme(Themes.resource(INTERFACE))
        .onUnmount(this.viewportClaims::clearAll)
        .build();
```

```json [assets/lightworkshop/mcsx/themes/interface.json]
{
  "values": {
    "dock-background": {"color": "#0b0c0d", "alpha": 0}
  }
}
```

ModernUI and both viewport APIs use framebuffer pixels with a top-left origin. `onUnmount` clears the
claims on the client thread in the same close operation; View detach remains the idempotent UI-thread
fallback.

When a vanilla screen opens from gameplay, `GameViewport` lays it out and renders it in Scene while
`GameSurface` keeps pointer ownership inside the same rectangle. Without a published `GameSurface`,
an open Minecraft screen owns the pointer across the whole window and the surrounding workspace is
visible but inert.

## Close the Workspace

Escape is not the Dockspace close command. Keep closure in the application owner from the overview:

```java
public void close() {
    Dockspace current = this.active;
    if (current != null) current.close();
}
```

Use `onUnmount` for synchronous claims and `onClose` to release the application reference. Native pane,
header, and footer Views are destroyed later on the UI thread, where `DockContent.dispose(View)` runs.

::: details Gesture rules and floating windows
Valid tab destinations are center merge, four leaf edges, four stage edges, positional strip insertion,
and an empty stage. Small leaves omit guides that do not fit. A sole tab cannot split against the same
leaf if removing it would erase the destination. Releasing over no target creates a floating window.

Floating windows are layered inside the host stage. Drag unused header space to move; drag edges or
corners to resize. Tabs, close controls, trailing tools, and maximize take priority over the move area.
Touching raises a window. A single-tab-group window can merge into another strip; a split-rooted window
can dock only as a whole subtree at an edge. Releasing over no guide repositions without docking.

Same-axis drops flatten into the existing split while preserving effective proportions. Presentation
clamps frames to keep chrome reachable after stage or GUI-scale changes; the semantic frame remains
the persistence source.
:::

::: details Input and Escape dispatch ordering
Escape is layered in this order:

1. If the game holds focus, Escape releases it to Dockspace.
2. If a Minecraft screen is open, vanilla handles Escape for that screen.
3. A focused text editor loses focus.
4. An active dock drag is canceled or a maximized pane is restored.
5. Otherwise Escape reaches Minecraft and opens the pause screen.

Escape never closes Dockspace. Focusing another pane, toggling or closing the maximized pane, resetting
or replacing layout, or switching workspaces restores maximize state first. Focus inside retained
content is preserved where possible.
:::

::: details Dock token and metrics reference
Dockspace reads `DOCK_BACKGROUND`, `DOCK_PANE_BACKGROUND`, `DOCK_HEADER_BACKGROUND`, `DOCK_BORDER`,
`DOCK_ACTIVE_TAB_BACKGROUND`, `DOCK_DRAG_GHOST_BACKGROUND`, `DOCK_DROP_HIGHLIGHT`, and
`DOCK_METRICS`. `ThemeTokens.withAlpha(color, alpha)` replaces packed ARGB alpha and accepts `0..255`.

A JSON `dock-metrics` object requires all 11 exact non-negative integer fields:

```json
{
  "dock-metrics": {
    "gutter": 12,
    "splitter-size": 12,
    "header-height": 36,
    "corner-radius": 4,
    "border-width": 0,
    "tab-text-size": 11,
    "control-text-size": 10,
    "tab-padding": 14,
    "icon-gap": 8,
    "tab-close-width": 32,
    "control-width": 32
  }
}
```

Zero is valid; the default uses a zero border with a 12px splitter hit target. Unknown or missing
fields are rejected. Neither the object nor one field accepts a token reference. Omit the complete
object to inherit it. It is not an MCSS scalar.
:::

::: details Stable test tags and final lifecycle edges
`DockTags` exposes stable tags for `HOST`, `SPLITTER`, `WINDOW`, `MOVE_AREA`, and `MAXIMIZE`. Each tab
uses its pane id, so `findViewWithTag("scene")` locates the Scene tab after mount without depending on
internal View classes.

`open()` reports success after acquiring the overlay slot, before root creation. `close()` is
idempotent, any-thread, and ownership-specific. `onUnmount` runs before `onClose`. Work queued for a
mount is discarded after close begins. Partial mount failure disposes every successfully created View
once and releases the slot.
:::

## Next Steps

- [Return to persistence](./persistence.md) to verify interaction survives a reopen.
- [Style pane content with MCSS](../mcsx/themes-and-styles.md) in addition to dock chrome.
- [Review host choices](../mcsx/overlays.md#choose-the-host) before combining a HUD with the workspace.
- [Finish the interface milestone](../workshop/interface.md) with Scene, Inspector, and Log in one owner.

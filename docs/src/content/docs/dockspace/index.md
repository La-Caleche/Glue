---
title: Dockspace
description: Open a complete two-pane Light Workshop with explicit application ownership.
artifact: glue-mcsx-dock
modId: glue-mcsx-dock
environment: client
---

# Dockspace

Dockspace turns MCSX Views into a retained editor workspace. This first task places Scene and
Inspector side by side over the running game and gives one application object complete open/close
ownership.

## Outcome

The Light Workshop opens as an overlay, not a Minecraft `Screen`. The world keeps ticking and
rendering underneath two resizable panes. A second trigger closes only the workspace that this owner
opened.

<DocImage title="Two-pane Light Workshop" description="Capture the first Dockspace result with Scene on the left and Inspector on the right, including both tab headers, the center splitter, and the full workspace surface over the running game." />

## Add Dockspace

The dock artifact is client-only and brings `glue-mcsx` as a Maven dependency:

```kotlin [build.gradle.kts]
dependencies {
    modImplementation("fr.lacaleche.glue:glue-mcsx-dock:<glue-version>")
}
```

Fabric still needs the corresponding mod dependency:

```json [fabric.mod.json]
{
  "depends": {
    "glue-mcsx-dock": "<glue-version>"
  }
}
```

Keep all Dockspace references in client code. `glue-mcsx-dock` and its MCSX dependency do not load
on a dedicated server.

## Build the Two-Pane Workspace

This owner is complete and single-purpose. Call `toggle()` from an existing client-thread keybinding,
client command, or other client callback.

```java [LightWorkshopWorkspace.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.mcsx.client.UiOverlay;
import fr.lacaleche.glue.mcsx.client.dock.DockPane;
import fr.lacaleche.glue.mcsx.client.dock.Dockspace;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import net.minecraft.resources.ResourceLocation;

public final class LightWorkshopWorkspace {

    private static final ResourceLocation WORKSPACE =
            ResourceLocation.fromNamespaceAndPath("lightworkshop", "interface");

    private final Signal<String> probeName = Signal.of("Key light");
    private final Signal<Boolean> probeEnabled = Signal.of(true);
    private volatile Dockspace active;

    public void toggle() {
        Dockspace current = this.active;
        if (current != null) {
            current.close();
            return;
        }
        if (UiOverlay.isOccupied()) return;

        Dockspace next = this.createWorkspace();
        this.active = next;
        try {
            next.open();
        } catch (RuntimeException failure) {
            if (this.active == next) this.active = null;
            throw failure;
        }
    }

    public void close() {
        Dockspace current = this.active;
        if (current != null) current.close();
    }

    private Dockspace createWorkspace() {
        DockPane scene = DockPane.builderLiteral(
                        "scene",
                        "Scene",
                        ui -> ui.screen(
                                ui.card(
                                        ui.literalHeading("Scene"),
                                        ui.literalCopy("The workshop world remains live beneath this pane.")
                                )
                        )
                )
                .closable(false)
                .build();

        DockPane inspector = DockPane.literal(
                "inspector",
                "Inspector",
                ui -> ui.screen(
                        ui.card(
                                ui.literalHeading("Probe Inspector"),
                                ui.literalField("Probe name").text(this.probeName),
                                ui.literalCheckbox("Enable probe").checked(this.probeEnabled),
                                ui.actions(ui.literalButton("Apply", () ->
                                        this.probeName.set(this.probeName.get().trim())))
                        )
                )
        );

        return Dockspace.builder(WORKSPACE)
                .pane(scene)
                .pane(inspector)
                .defaultLayout(DockLayouts.layout(DockLayouts.row(
                        DockLayouts.tabs("scene"),
                        DockLayouts.tabs("inspector")
                )))
                .persistence(false)
                .onClose(() -> this.active = null)
                .build();
    }
}
```

### Expected result

`row(...)` gives Scene and Inspector equal horizontal shares. Drag the splitter to resize them. The
pane ids are stable layout keys, while their literal titles are presentation. Each content lambda
receives a scoped `Ui`, so the Inspector uses the same MCSX bindings as the screen version.

This first sample disables persistence so the two-pane result is reproducible on every run. The
[Persistence guide](./persistence.md) removes that line, adds defaults and reset, and explains when a
saved user layout takes precedence.

## Why the Owner Matters

MCSX has one overlay slot shared by Dockspace, direct interactive overlays, and managed HUDs.
`UiOverlay.isOccupied()` tells the trigger whether a new mount would be rejected. The owner keeps the
exact `Dockspace` instance it opened and closes only that instance; it never dismisses a foreign
overlay.

`open()` runs on Minecraft's client thread. A successfully mounted instance is single-use, so
`onClose` releases the old reference and the next trigger builds a new workspace. `close()` is
idempotent and safe from any thread. The `active` field is volatile because UI-thread actions read it
while client-thread lifecycle callbacks replace or clear it.

## Lifecycle and Ownership

Pane content is created lazily and retained for one mounted workspace. Activating another tab,
moving, floating, maximizing, or reopening a pane reparents the same View rather than rebuilding it.
Reactive component bindings therefore detach and reattach while preserving the pane's UI state.

Application lifecycle callbacks and View disposal have different owners:

| Callback | Thread and purpose |
| --- | --- |
| `onMount` | Client thread, after the overlay slot is acquired; initialize session ownership |
| `onUnmount` | Client thread during close; synchronously release world, viewport, or input claims |
| `onClose` | Client thread after `onUnmount`; finish application ownership before another mount |
| `DockContent.dispose(View)` | ModernUI UI thread later; dispose resources owned by a created View |

::: details Mount and failure edge cases
`isOpen()` becomes true when `open()` acquires the slot, before ModernUI necessarily creates the root,
and false as soon as close begins. If an occupied slot rejects `open()`, that not-yet-mounted instance
can be retried. Once mounting succeeds, the instance cannot be opened again.

A close requested inside `onMount` waits until that callback returns. Runtime pane and layout work is
discarded once close begins. If pane creation fails later on the UI thread, Dockspace disposes every
View already created for that mount, logs the failure, and releases the overlay slot. Use callbacks,
not return from `open()`, as the application lifecycle boundary.
:::

## Next Steps

- [Add a Log pane and weighted layout](./layout-and-panes.md) with visual/code pairs.
- [Enable persistence and provide Reset layout](./persistence.md) without making disk recovery the starting point.
- [Learn tab gestures, focus, themes, and viewport integration](./interaction.md) as separate tasks.
- [Assemble the final Light Workshop milestone](../workshop/interface.md) in one self-contained owner.

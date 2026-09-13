---
title: Overlays and HUDs
description: Choose an MCSX host and mount a complete non-interactive Probe HUD safely.
artifact: glue-mcsx
modId: glue-mcsx
environment: client
---

# Overlays and HUDs

MCSX has one exclusive overlay slot shared by interactive fragments, managed HUDs, and Dockspace.
This task first chooses the right host, then mounts a real Probe HUD with an explicit owner.

## Outcome

The Probe status appears as a small read-only card over gameplay. Its owner can show and hide it
without a render callback or any risk of closing another overlay.

## Choose the Host

| Host | Input | World underneath | Close owner | Best fit |
| --- | --- | --- | --- | --- |
| `UiScreen` | Screen owns input | May keep ticking, but screen replaces game input | Screen lifecycle | Forms and modal inspection |
| `UiOverlay.mount(...)` | Interactive ModernUI | Keeps ticking and rendering | Returned `Mount` | One custom in-world tool |
| `UiOverlay.hud(...)` | No pointer or keyboard input | Keeps ticking and rendering | Returned `Hud` host | Status and read-only telemetry |
| `Dockspace` | Interactive workspace with game-focus handoff | Keeps ticking and rendering | `Dockspace` instance | Multi-pane editor tools |

<DocImage title="MCSX host choice" description="Create a four-way diagram comparing Screen, interactive Overlay, non-interactive HUD, and Dockspace; show input ownership, whether the world remains visible, and who closes each host." />

Use a screen for the Probe Inspector form. Use the HUD below when only current probe status should
remain visible. Move to [Dockspace](../dockspace/index.md) when Scene, Inspector, and Log need tabs,
splits, and persistence.

## Build a Minimal Probe HUD

The Fragment is complete: it creates its `Ui`, root, theme, and stylesheet.

```java [ProbeHudFragment.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import net.minecraft.resources.ResourceLocation;

public final class ProbeHudFragment extends Fragment {

    private static final ResourceLocation HUD =
            ResourceLocation.fromNamespaceAndPath("lightworkshop", "probe-hud");

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            DataSet savedInstanceState
    ) {
        Ui ui = Ui.with(this.requireContext());
        var root = ui.column(
                ui.column(
                        ui.literalHeading("Probe"),
                        ui.literalText("Ready at spawn")
                ).classes("probe-hud-card")
        ).classes("probe-hud-root");
        root.theme(Themes.mcsx());
        root.stylesheet(Stylesheets.resource(HUD));
        return root;
    }
}
```

Make the HUD root transparent so it does not cover the game:

```css [assets/lightworkshop/mcsx/styles/probe-hud.mcss]
.probe-hud-root {
    width: 100%;
    padding: 16px;
    gap: 0px;
    align-items: start;
    justify-content: start;
    background: transparent;
}

.probe-hud-card {
    padding: 12px;
    gap: 4px;
    background: @hud-surface;
    corner-radius: @shell-radius;
}
```

Retain one managed host in the client-side owner:

```java [ProbeHud.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.mcsx.client.UiOverlay;

public final class ProbeHud {

    private final UiOverlay.Hud<ProbeHudFragment> hud =
            UiOverlay.hud(ProbeHudFragment::new);

    public void show() {
        if (!UiOverlay.isOccupied()) this.hud.mount();
    }

    public void hide() {
        this.hud.unmount();
    }

    public boolean isShown() {
        return this.hud.isMounted();
    }
}
```

Call `show()` and `hide()` on Minecraft's client thread.

### Expected result

The small card renders automatically after Minecraft's HUD and before any open screen. Gameplay
continues, and the card receives no input. No render callback is required.

## Lifecycle and Ownership

`UiOverlay.hud(factory)` creates a host but mounts nothing. Each successful `mount()` creates a fresh,
non-null Fragment. Repeated mount while that host is active is a no-op. `unmount()` is idempotent and
can release only that host's mount. Mounting after unmount creates another Fragment.

Because the slot is exclusive, compose several HUD elements into one Fragment rather than creating
several HUD hosts. `fragment()` throws before the first mount and retains the most recently created
Fragment after unmount for inspection.

## Mount an Interactive Fragment

When the same kind of Fragment needs input, mount it directly and keep the returned ownership handle:

```java
private UiOverlay.Mount probeOverlay;

public void openProbeOverlay(Fragment fragment) {
    if (UiOverlay.isOccupied()) return;

    this.probeOverlay = UiOverlay.mount(fragment, () -> this.probeOverlay = null);
    this.probeOverlay.setEscapeHandler(() -> {
        this.closeProbeOverlay();
        return true;
    });
}

public void closeProbeOverlay() {
    UiOverlay.Mount current = this.probeOverlay;
    if (current != null) current.close();
}
```

`UiOverlay.mount(...)` is client-thread-only. Its `onUnmount` callback runs on the client thread during
teardown, before cursor restoration, making it the synchronous place to release world, viewport, or
input claims. The Escape handler runs on ModernUI's UI thread and returns whether it consumed an
otherwise idle Escape.

::: details Exclusive-slot and teardown edge cases
`UiOverlay.isOccupied()` reports exactly whether another interactive mount, managed HUD, Dockspace,
or direct compatible ModernUI overlay currently blocks this slot.

`Mount.close()` is idempotent and safe from any thread. It closes only the mount represented by that
handle, never an overlay mounted later. `acceptsWork()` becomes false as soon as closing begins or
when the mount is no longer active. Root sizing, composition, and resize handling are automatic.

Managed `Hud` operations are client-thread-only. An occupied slot rejects `Hud.mount()` rather than
replacing its owner. A managed HUD receives no pointer or keyboard events. Direct mounts are
interactive and follow the base overlay input path.
:::

## Next Steps

- [Bind live probe status](./reactivity.md) inside the HUD Fragment.
- [Reuse the Probe Inspector theme](./themes-and-styles.md) while preserving a transparent HUD root.
- [Build a complete Dockspace owner](../dockspace/index.md) for the Scene, Inspector, and Log workspace.
- [Finish the self-contained Light Workshop interface](../workshop/interface.md).

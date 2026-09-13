---
title: Rendering Events
description: Add HUD, post-world, debug, selection, and particle callbacks at known client render stages.
artifact: glue-render
modId: glue-render
environment: client
---

# Rendering Events

This task produces a small **Light Workshop: render ready** label at the bottom of the HUD. Use a
Glue rendering event when code needs a stable client render stage that Fabric API does not expose.

## Add the Status Label

Register the listener once from the Light Workshop client entrypoint. If that class already contains
the Core keybinding milestone, merge this registration into its existing `onInitializeClient()`
instead of replacing the class, its fields, or its listeners.

```java [src/client/java/dev/example/lightworkshop/LightWorkshopClient.java]
package dev.example.lightworkshop;

import fr.lacaleche.glue.client.events.RenderEvents;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

public final class LightWorkshopClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RenderEvents.MAIN_RENDER.register((graphics, tickDelta, width, height) ->
                graphics.drawString(
                        Minecraft.getInstance().font,
                        "Light Workshop: render ready",
                        8,
                        height - 16,
                        0xFFFFA447));
    }
}
```

Join a world. The orange status line appears after the vanilla in-world HUD and before screens,
toasts, and other later overlays. It uses GUI-scaled coordinates, so `height - 16` remains close to
the bottom at every GUI scale.

<DocImage title="Light Workshop HUD status" description="Minecraft in a workshop area with the orange text Light Workshop: render ready aligned eight pixels from the lower-left edge." />

Replace this placeholder with a 1280x720 in-game screenshot. Keep the full HUD visible and add one
callout pointing to the orange status line with the label `MAIN_RENDER`.

## Place Work on the Timeline

`MAIN_RENDER` is the simplest late-HUD hook. Geometry and post-processing belong earlier in the
frame:

<DocImage title="Glue render timeline" description="A horizontal timeline showing world render, POST_WORLD_RENDER capture-lighting-default phases, hand and screen effects, vanilla HUD, RENDER_HUD, MAIN_RENDER, then screens and toasts." />

Replace this placeholder with a 1400x360 timeline. Mark `POST_WORLD_RENDER` before the hand and HUD,
`RENDER_HUD` before F3 and later HUD layers, and `MAIN_RENDER` at the end of `Gui.render`.

| Task | Event |
| --- | --- |
| Late in-world status or overlay | `RenderEvents.MAIN_RENDER` |
| Overlay that must precede F3 and later HUD layers | `RenderEvents.RENDER_HUD` |
| World-color post-processing before the hand and HUD | `RenderEvents.POST_WORLD_RENDER` |

All three callbacks run synchronously on the client render thread. Keep them short and move asset
loading, networking, and other blocking work elsewhere.

## Use Post-world Rendering Safely

Ordinary consumer post effects register in the default phase:

```java
RenderEvents.POST_WORLD_RENDER.register(() -> {
    // The completed world color and depth are available here.
});
```

Glue orders the post-world phases as capture teardown, Lumos deferred lighting, then the default
consumer phase. Register into `PHASE_CAPTURE` or `PHASE_LIGHTING` only when implementing the matching
integration, not as general-purpose priority levels.

Glue binds the main framebuffer, selects its viewport, and clears the active GL program before
dispatch. It rebinds the main framebuffer afterward, but it does not restore every state a listener
can change. Raw GL listeners must restore all touched state and account for Minecraft's cached GL
state. Prefer [managed post effects](./post-effects.md) when possible.

::: details Full event timing reference

| Event | Callback | Exact placement |
| --- | --- | --- |
| `RENDER_HUD` | `Consumer<GuiGraphics>` | At the start of vanilla's debug-overlay stage. It runs even when F3 is closed, while the in-world GUI is visible, before F3 text, scoreboard, titles, chat, player list, and subtitles. |
| `MAIN_RENDER` | `QuadConsumer<GuiGraphics, Float, Integer, Integer>` | At the return of `Gui.render`, after the vanilla in-world HUD. It supplies game-time tick delta and GUI-scaled width and height. Screens, overlays, saving status, and toasts render later. |
| `POST_WORLD_RENDER` | `Runnable` | After level rendering and Glue's deferred composite, before depth clear, first-person hand, screen effects, vanilla post processing, and HUD. |

Within `POST_WORLD_RENDER`, Glue guarantees
`PHASE_CAPTURE` -> `PHASE_LIGHTING` -> Fabric's default phase. Listeners in one phase run in
registration order.
:::

::: details Debug, selection, and particle events

`DebugEvents` provides observational hooks:

| Event | Behavior |
| --- | --- |
| `BLOCK_OUTLINE` | Runs immediately before Glue draws an eligible `GlueBlock` outline. |
| `F3_SCREEN_LEFT` / `F3_SCREEN_RIGHT` | Appends to the mutable F3 text lists after vanilla builds them. |
| `GUI_DEBUG_LAYERS` | Runs immediately after `MAIN_RENDER`; it is not conditional on F3 being open. |
| `WORLD_DEBUG` | Runs at the end of the normal world-debug pass, before translucent world rendering. |
| `PARTICLE_SPAWN` | Runs after `ParticleEngine.createParticle` creates and queues a non-null particle; it is not conditional on F3. |

For example, append one F3 line:

```java
DebugEvents.F3_SCREEN_LEFT.register((client, lines) ->
        lines.add("[Light Workshop] Renderer ready"));
```

`DrawSelectionEvents.BLOCK` is a result event. Returning `true` stops later listeners and cancels the
current vanilla block-outline draw; returning `false` continues. Minecraft may request more than one
outline pass per frame, including high-contrast outlines.

`ParticleManagerEvents.BLOCK_BREAK` asks listeners in registration order for a `VoxelShape`. The
first non-null result wins. If every listener returns `null`, Glue uses `BlockState.getShape`.
Returning an empty shape emits no break-particle boxes.
:::

## Respect Callback Ownership

Listeners remain registered for the client process. Glue does not catch listener exceptions, so a
failure stops later listeners and propagates through the current render or particle operation.

`GuiGraphics`, pose stacks, buffers, particles, levels, and mutable lists belong to Minecraft. Use
them during the callback only. Particle callbacks run on the thread that called the corresponding
`ParticleEngine` method; Glue does not move them to the render thread.

## Next Steps

- Give the pedestal visible selection feedback in [Block Outlines](./block-outlines.md).
- Combine both beginner tasks in the [Rendering Milestone](../workshop/rendering.md).
- Use [Transform Stack](./transforms.md) when a render callback needs balanced model transforms.

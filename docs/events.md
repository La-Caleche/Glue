# Events

Glue provides two event interfaces for hooking into rendering and debug systems.

## RenderEvents

`fr.lacaleche.glue.client.events.RenderEvents`

| Event | Type | Fired When |
|---|---|---|
| `RENDER_HUD` | `Consumer<GuiGraphics>` | During HUD rendering |
| `MAIN_RENDER` | `QuadConsumer<GuiGraphics, Float, Integer, Integer>` | During main render cycle (graphics, tickDelta, screenWidth, screenHeight) |
| `POST_WORLD_RENDER` | `Runnable` | After world rendering + Glue blit, before HUD. Correct place for post-processing effects. |

### Usage

```java
RenderEvents.RENDER_HUD.register(guiGraphics -> {
    // Draw HUD overlay
});

RenderEvents.POST_WORLD_RENDER.register(() -> {
    // Apply post-processing effects
    Minecraft mc = Minecraft.getInstance();
    RenderTarget target = mc.getMainRenderTarget();
    MY_POST_SHADER.apply(target, resourcePool);
    resourcePool.endFrame();
});
```

### POST_WORLD_RENDER Timing

This event fires at different points depending on whether Iris is active:

- **Iris:** After Iris composite pass + Glue capture blit, before MC clears the depth texture. Injected via `GluePostCompositeMixin`.
- **Vanilla:** After `WorldRenderEvents.LAST`.

This ensures post-process effects see both vanilla and custom shader content.

Listeners run in **phase order**, not registration order, so a post effect sees the final lit frame no
matter which mod initialized first: `PHASE_CAPTURE` (material-capture teardown) → `PHASE_LIGHTING`
(Lumos deferred lighting) → default phase. Register a post-processing effect on the default phase (the
plain `register(listener)`) and it runs after Lumos has composited. Use `PHASE_CAPTURE` / `PHASE_LIGHTING`
only to slot work into those earlier stages.

## DebugEvents

`fr.lacaleche.glue.client.events.DebugEvents`

| Event | Type | Fired When |
|---|---|---|
| `BLOCK_OUTLINE` | `BlockOutline` | When rendering a Glue block outline |
| `F3_SCREEN_LEFT` | `F3Screen` | F3 debug screen left column |
| `F3_SCREEN_RIGHT` | `F3Screen` | F3 debug screen right column |
| `GUI_DEBUG_LAYERS` | `GuiDebugLayers` | GUI debug layer rendering |
| `WORLD_DEBUG` | `WorldDebug` | World-space debug rendering |
| `PARTICLE_SPAWN` | `ParticleSpawn` | When a particle is spawned |

### Usage

```java
// Add info to F3 screen
DebugEvents.F3_SCREEN_LEFT.register((client, list) -> {
    list.add("[MyMod] Active effects: " + effectCount);
});

// Debug world rendering
DebugEvents.WORLD_DEBUG.register((matrices, vertexConsumers, cameraX, cameraY, cameraZ) -> {
    // Render debug wireframes in world space
});

// Monitor particle spawns
DebugEvents.PARTICLE_SPAWN.register((particle, level, x, y, z, xSpeed, ySpeed, zSpeed) -> {
    // Track or modify particles
});
```

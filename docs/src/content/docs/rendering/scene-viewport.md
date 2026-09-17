---
title: 3D Scene Viewport
description: Render the Light Workshop region into an orbit-controlled screen with explicit resource cleanup.
artifact: glue-render
modId: glue-render
environment: client
---

# 3D Scene Viewport

This task produces a full-screen orbit preview centered on the Light Workshop pedestal. Use a scene
viewport for model viewers, editor previews, and transform tools that should render client-side
geometry without moving the real player or changing the world.

`AbstractViewportScreen` is an isolated custom scene. `GameViewport` instead confines the live game
world, HUD, and open screens to a rectangle; see [Constrain the Live Game](#constrain-the-live-game).

## Run the Showcase Examples

The showcase includes three native scene screens under `fr.lacaleche.glue.testmod.scene`. Join a
world and open them from the **3D scenes** section of the F6 hub, or use the client commands:

| Command | Example |
|---|---|
| `/showcase scene orbit` | `BlockSceneTestScreen`: nearby blocks, orbit/pan/zoom and adjustable region bounds. |
| `/showcase scene fps` | `FpsViewportTestScreen`: independent free-flight camera, pointer capture and nearby entities. |
| `/showcase scene gizmo` | `GizmoTestScreen`: selection, translate/rotate/scale, snapping and preview-only undo/redo. |

All three use `AbstractViewportScreen` and clean up their owned renderer on removal. Escape returns
to the opening screen; in the FPS example it first releases the pointer. The world and the real
player are not edited by these previews. `glue-test:scenes` exercises their hub entry points,
rendering, camera controls, preview history and cleanup in the live client.

## Build the Orbit Preview

This smallest screen reads a fixed region from the current client world, renders it to an owned
texture, and cleans that texture up when the screen closes:

```java [src/client/java/dev/example/lightworkshop/screen/PedestalPreviewScreen.java]
package dev.example.lightworkshop.screen;

import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.client.viewport.AbstractViewportScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

public final class PedestalPreviewScreen
        extends AbstractViewportScreen<OrbitCameraController> {
    private final BlockSceneRenderer scene = new BlockSceneRenderer();

    public PedestalPreviewScreen(BlockPos pedestalPos) {
        super(Component.literal("Pedestal preview"),
                new OrbitCameraController(new Vector3f(0.0F, 0.0F, 0.0F)));

        scene.setCenterPos(pedestalPos);
        scene.setHalfExtentX(2);
        scene.setHalfExtentZ(2);
        scene.setMinY(-1);
        scene.setMaxY(2);
        cameraController.setZoom(6.0F);
    }

    @Override
    protected int renderSceneToTexture(float width, float height,
                                       Minecraft client, float tickDelta) {
        scene.setFov(cameraController.getFov());
        scene.setViewMatrix(cameraController.buildViewMatrix());
        scene.setScale(1.0F);
        return scene.renderToTexture((int) width, (int) height, client);
    }

    @Override
    public void removed() {
        super.removed();
        scene.cleanup();
    }
}
```

Open it from an existing client-thread button, key, or command callback:

```java
Minecraft.getInstance().setScreen(new PedestalPreviewScreen(pedestalPos));
```

The preview shows a 5x4x5 region around `pedestalPos`. Left-drag orbits, right-drag pans, the wheel
zooms, and Escape closes the screen. The real camera and world remain unchanged.

## Match the Camera and Renderer

The screen receives framebuffer-pixel dimensions in `renderSceneToTexture`; pass those dimensions to
`renderToTexture`. The scene renderer defaults to a 60-degree FOV while camera controllers default to
70 degrees. The example synchronizes them every frame so framing and later picking agree.

`BlockSceneRenderer` defaults to relative coordinates. It fetches blocks around the world-space
`centerPos`, then places that center at scene offset `(0, 0, 0)`. The orbit pivot in the example is in
that same scene coordinate space.

## Clean Up the Owned Resources

`AbstractSceneRenderer` owns its render target and projection buffer. It creates and resizes them on
demand, then releases both in `cleanup()`. Always call that method from the screen's `removed()`
override after `super.removed()`.

`AbstractViewportScreen.removed()` releases an active cursor capture, cleans up that camera state,
and unregisters its borrowed scene-texture wrapper. It cannot know which scene renderer your
subclass owns. A target returned by `getFramebuffer()` remains borrowed from the renderer; never
destroy it separately.

Create, render, resize, and clean scene resources on the client render thread. Custom scene hooks
must also restore any additional render state and balance every pose they change.

::: details Block region and custom geometry reference

The default inclusive bounds are X `-5..5`, Z `-5..5`, and Y `-2..3`. Configure them with
`setHalfExtentX`, `setHalfExtentZ`, `setMinY`, and `setMaxY`.

`shouldRenderBlock(worldPos, state)` includes non-air model-rendered blocks by default. Override it
to filter the region. `renderBlock(...)` is a positioning hook: the base class pushes the pose,
invokes the hook, draws the block once, and pops. Do not draw the block a second time there.

`renderExtras(...)` runs after the block batch was flushed. If it adds buffered geometry, call
`bufferSource.endBatch()` again. `renderGrid(PoseStack)` is a separate final pass and does nothing by
default.

For non-block previews, extend `AbstractSceneRenderer` and implement `renderScene`,
`buildViewMatrix`, and `getScale`; the base still owns target creation, projection, viewport, clear,
and cleanup.

`setClearColor(float[])` requires at least RGB. The target clears opaque and ignores a fourth array
component.
:::

::: details Relative, absolute, and overlay matrices

`CoordinateMode.RELATIVE` places each fetched block at its integer offset from `centerPos`.
`ABSOLUTE` places models at their world block coordinates and requires a world-space camera and
pivot. Do not combine a relative camera with large absolute world positions.

The base scene model-view applies the configured scale and then translates by
`(-0.5, -0.5, -0.5)`. Picking and gizmo matrices must include the same transform:

```java
Matrix4f overlayView = cameraController.buildViewMatrix();
overlayView.scale(renderScale);
overlayView.translate(-0.5F, -0.5F, -0.5F);
```

The base projection uses near plane `0.1F` and far plane `1000.0F`. A camera controller's
`buildProjectionMatrix` uses near plane `0.05F` and configurable `depthFar`, so it is not an exact
overlay match for `AbstractSceneRenderer`.
:::

::: details Camera and input reference

The one-argument `OrbitCameraController` starts at pitch 30 degrees, yaw 45 degrees, and zoom 5.
Pitch clamps to `-90..90`. `setMinZoom`, `setMaxZoom`, `setInvertPanY`, `setOrbitRotation`,
`setZoom`, and `fitToBounds` configure it.

`OrbitCameraController(Vector3f, Entity)` derives rotation and distance from an entity.
`OrbitCameraController(Minecraft, BlockPos)` creates a world-space pivot at block center; pair those
world-space constructors with a world-aligned scene.

`FpsCameraController` supports captured mouse look, WASD, Space, left Shift, and a left-Control fast
multiplier. Movement uses elapsed frame time and normalizes combined axes, so speed does not change
with frame rate or diagonal input. Scroll changes movement speed. Escape releases capture before it
closes the screen. For a relative block scene, seed the FPS camera in scene coordinates and keep the
renderer's world `centerPos` fixed while the camera moves. Orbit panning derives its scale from the
controller FOV, zoom, and viewport height.

`AbstractViewportScreen` does not call `cameraController.tick()` automatically. It treats a drag
under five GUI pixels as `onViewportClick`, calls `onRenderOverlay` and then `renderHud`, and returns
`false` from `isPauseScreen`. Its base wheel handler always reaches the camera; override it when an
overlay must consume scrolling.
:::

## Add Picking, Gizmos, and History Later

These are supported capabilities, but they share matrix and ownership contracts that should be
added only after the basic preview is correct.

::: details Picking recipe

`OrbitCameraController.createRay(mouseX, mouseY, width, height)` returns a scene-space `PickRay`.
Pass coordinates and dimensions from the same viewport space. If scene scale and the base half-block
translation are active, transform the ray into block-local coordinates before intersection tests.
`RaycastUtils` uses the vanilla game camera and is not a substitute for custom-scene picking.
:::

::: details Gizmo frame contract

`GlfwGizmoController` supports translate, rotate, and scale handles:

1. Construct it and call `setWindowHandle(client.getWindow().getWindow())`.
2. Before `super.render`, call `updateMousePosition(mouseX, mouseY)` and `updateFrame()`.
3. In `onRenderOverlay`, call `manipulate` with the exact scene view, matching projection, and
   GUI-space viewport rectangle, followed by `inputEnabled`. Pass `false` while another overlay owns
   input; this blocks a new interaction while allowing an existing drag to finish.
4. After `super.render`, call `gizmo.getBackend().render(guiGraphics)` to flush handles.
5. Return `gizmo.isHovered() || gizmo.isDragging()` from `isOverlayCapturingInput()`.

Select `GizmoOperation.TRANSLATE`, `ROTATE`, or `SCALE`, and `GizmoSpace.LOCAL` or `WORLD`.
Persistent snap uses `setUseSnap(true)`; left Shift temporarily snaps, and left Control scales all
axes during a scale drag. Snap values store translation, rotation degrees, and scale at indices 0,
1, and 2.

The translation, rotations, and scale getters return mutable objects. Copy them into a saved
`TransformationComponent` instead of retaining live references.
:::

::: details Undo and redo ownership

`Abstract3DController` stores the supplied gizmo and creates a `HistoryManager`. The screen still
drives the gizmo every frame. The controller observes drag transitions through
`updateGizmoInteraction`; a subclass snapshots before state at drag start and records final state at
drag end. Apply the gizmo continuously while dragging for live feedback.

`HistoryManager` is a common-side Glue Core API; see the [module table](../modules.md#choose-by-goal).
The default capacity is 128. `history.execute(command)` executes and records a command while
discarding the redo future. `undo()` calls `Command.undo()` and `redo()` calls `Command.execute()`
again. Capture independent before/after snapshots, and make re-execution safe because the final
state is usually already visible when a drag ends.

There is no history change callback or cleanup method. The controller or screen owns UI refreshes
after execute, undo, redo, and clear. Keep a history containing client scene commands on the client
thread.
:::

## Constrain the Live Game

`GameViewport` renders the real world into an offscreen target, then presents it in the requested
window rectangle. The HUD and open Minecraft screens use that rectangle too. It uses the real player
camera rather than an independent `BlockSceneRenderer` camera.

Set positive bounds in physical framebuffer pixels with a top-left origin:

```java
GameViewport.set(new GameViewport.Bounds(40, 40, 640, 360));
```

The host owns this process-wide setting. Update it when its destination changes, and release it when
the host closes:

```java
GameViewport.clear();
```

`clear()` is idempotent. `isActive()` reports whether bounds are set and `bounds()` returns those
bounds, or `null`. Bounds are published atomically and sampled once per rendered frame.

While the world target is active, the window reports its dimensions so camera aspect and downstream
renderers agree. Derive new bounds from the host's physical destination rather than repeatedly
shrinking those virtualized window dimensions. Input is mapped into the same destination rectangle;
the host remains responsible for choosing when gameplay should capture the pointer.

## Next Steps

- Add model movement inside the preview with [Transform Stack](./transforms.md).
- Choose editor assets with [File Dialogs](./file-dialogs.md).
- Use [Rendering Pipelines](./pipelines.md) for preview geometry that needs a custom core shader.

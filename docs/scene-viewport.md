# 3D Scene Viewport

Glue provides a full stack for embedding interactive 3D previews inside Minecraft `Screen`s:
a scene renderer that draws Minecraft blocks into an FBO, camera controllers, a Minecraft `Screen`
base class that wires them together, an optional 3D gizmo for transform editing, and an undo/redo
history manager.

---

## Scene Rendering

### AbstractSceneRenderer

Base class for anything that renders geometry into a framebuffer object and returns its texture ID.

```java
public class MyRenderer extends AbstractSceneRenderer {

    private Matrix4f viewMatrix = new Matrix4f();
    private float scale = 1.0f;

    public void setViewMatrix(Matrix4f m) { this.viewMatrix = m; }
    public void setScale(float s)         { this.scale = s; }

    @Override protected Matrix4f buildViewMatrix() { return viewMatrix; }
    @Override protected float    getScale()        { return scale; }

    @Override
    protected void renderScene(Minecraft client, PoseStack matrices) {
        // render geometry using matrices + RenderSystem model-view stack
    }
}
```

Calling `renderToTexture(width, height, client)` returns an OpenGL texture ID, or `-1` on failure.
GL state (projection, model-view stack, render-target overrides) is always restored via a `finally`
block even if `renderScene` throws.

**Key settings:**

```java
renderer.setFov(60f);                          // perspective FOV in degrees
renderer.setClearColor(new float[]{0,0,0,1}); // RGBA clear colour (≥3 components)
renderer.cleanup();                            // destroy FBO + projection buffer
```

**Scale:** `1.0f = 1× identity`. Blocks appear at their natural size. Values above `1.0` scale them up.

**Override points:**

| Method | Default | Purpose |
|---|---|---|
| `shouldRenderBlock(pos, state)` | non-air MODEL | Filter which blocks render |
| `renderBlock(matrices, ...)` | `translate(relX, relY, relZ)` | Per-block matrix setup |
| `renderExtras(client, matrices, center, buffers)` | no-op | Entities, particles, overlays |
| `renderGrid(matrices)` | no-op | Grid overlay pass |

### BlockSceneRenderer

Renders a rectangular region of Minecraft blocks from the live world into the FBO.

```java
BlockSceneRenderer renderer = new BlockSceneRenderer();

// Region settings (defaults shown)
renderer.setHalfExtentX(5);   // ±5 blocks east/west
renderer.setHalfExtentZ(5);   // ±5 blocks north/south
renderer.setMinY(-2);         // floor offset relative to center
renderer.setMaxY(3);          // ceiling offset relative to center

// Centre in world coordinates. null = player's foot block each frame.
renderer.setCenterPos(BlockPos.containing(player.position()));

renderer.setViewMatrix(cam.buildViewMatrix());
renderer.setScale(1.0f);

int textureId = renderer.renderToTexture(width, height, client);
renderer.cleanup(); // call on screen close
```

Blocks that are air or not `RenderShape.MODEL` are skipped by default. Override
`shouldRenderBlock` to include or exclude additional states:

```java
BlockSceneRenderer renderer = new BlockSceneRenderer() {
    @Override
    protected boolean shouldRenderBlock(BlockPos pos, BlockState state) {
        return super.shouldRenderBlock(pos, state) && !state.is(Blocks.BEDROCK);
    }
};
```

Add entities or other geometry via `renderExtras`:

```java
BlockSceneRenderer renderer = new BlockSceneRenderer() {
    @Override
    protected void renderExtras(Minecraft client, PoseStack matrices,
                                BlockPos center, MultiBufferSource.BufferSource buffers) {
        if (client.level == null) return;
        var dispatcher = client.getEntityRenderDispatcher();
        for (Entity e : client.level.entitiesForRendering()) {
            double rx = e.getX() - center.getX();
            double ry = e.getY() - center.getY();
            double rz = e.getZ() - center.getZ();
            dispatcher.render(e, rx, ry, rz, e.getYRot(), matrices, buffers, LightTexture.FULL_BRIGHT);
        }
        buffers.endBatch();
    }
};
```

---

## Cameras

All cameras extend `AbstractCameraController` which extends Minecraft's `Camera`.

### OrbitCameraController

Orbits around a fixed world-space pivot. Left-drag = rotate, right-drag = pan, scroll = zoom.

```java
// Pivot at block centre, matching player's look
OrbitCameraController cam = new OrbitCameraController(
        new Vector3f(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f),
        client.getCameraEntity());

// Or with a shorthand
OrbitCameraController cam = new OrbitCameraController(client, pos);

cam.setMinZoom(0.5f);
cam.setMaxZoom(50f);
cam.setOrbitRotation(30f, 45f); // pitch, yaw
cam.setZoom(8f);
cam.reset();                    // zoom=5, rotation=30/45

// Frame any AABB automatically
cam.fitToBounds(minX, minY, minZ, maxX, maxY, maxZ);

Matrix4f view = cam.buildViewMatrix();
```

Ray-picking for click interaction:

```java
OrbitCameraController.PickRay ray = cam.createRay(mouseX, mouseY, screenW, screenH);
// ray.origin() and ray.dir() are in scene space
```

### FpsCameraController

First-person fly camera. Click-to-capture; WASD/Space/Shift moves in scene space; scroll adjusts speed.

**Coordinate model:** the camera position is in *scene space*, not world space.
Scene space has `(0, 0, 0)` at the scene's reference point (the block fetched at `centerPos`).
Start the camera at eye height in scene space and keep `centerPos` fixed — do not update it
during flight.

```java
// Good: scene-space start position, player look direction
FpsCameraController cam = new FpsCameraController(
        new Vec3(0.5, 1.7, 0.5),  // eye height above centre block
        player.getYRot(),
        player.getXRot());

// Fix the block region at the player's real position — never change this during flight
renderer.setCenterPos(player.getOnPos());
```

```java
// Controls (called automatically by AbstractViewportScreen)
cam.processCapturedInput(windowHandle); // WASD/look when mouse is captured
cam.adjustSpeed(scrollDelta);           // scroll changes fly speed
cam.cleanup();                          // resets first-frame flag on capture release
```

### Common AbstractCameraController API

```java
cam.startDrag(mouseX, mouseY, button);
cam.updateDrag(mouseX, mouseY, viewportHeight); // viewportHeight used by OrbitCamera for pan scale
cam.stopDrag();
cam.handleScroll(delta);
cam.buildViewMatrix();        // Matrix4f view matrix in scene space
cam.getViewMatrixArray(arr);  // fills float[16]
cam.buildProjectionMatrix(w, h); // perspective Matrix4f
cam.getFov() / cam.setFov(f);
cam.isDragging();
cam.capturesMouseOnClick();   // true for FPS camera
```

---

## Viewport Screen

`AbstractViewportScreen` is a Minecraft `Screen` that renders a 3D scene into an FBO, blits it
as a fullscreen quad, and handles camera interaction (drag, scroll, FPS capture).

```java
public class MyViewportScreen extends AbstractViewportScreen {

    private final BlockSceneRenderer renderer;
    private final OrbitCameraController cam;

    public MyViewportScreen() {
        super(Component.literal("Preview"),
              new OrbitCameraController(new Vector3f(0, 0, 0)));
        this.cam = (OrbitCameraController) cameraController;
        this.renderer = new BlockSceneRenderer();
    }

    @Override
    protected int renderSceneToTexture(float width, float height,
                                       Minecraft client, float tickDelta) {
        renderer.setViewMatrix(cam.buildViewMatrix());
        renderer.setScale(1.0f);
        return renderer.renderToTexture((int) width, (int) height, client);
    }

    @Override
    public void removed() {
        super.removed();
        renderer.cleanup(); // always clean up the FBO
    }
}
```

**Override points:**

| Method | When to override |
|---|---|
| `renderSceneToTexture(w, h, client, dt)` | Required — render to FBO, return texture ID |
| `onRenderOverlay(x, y, w, h)` | Draw overlays (gizmos, outlines) on top of scene |
| `onViewportClick(localX, localY, w, h, button)` | Handle clicks that did not drag |
| `isOverlayCapturingInput()` | Return true when a gizmo/overlay owns input |
| `renderHud(graphics, mx, my, dt)` | Draw HUD text over everything |

`isPauseScreen()` returns `false` — the game continues ticking.

Mouse drag smaller than 5 pixels is treated as a click → `onViewportClick` fires.
ESC releases FPS capture before closing.
`removed()` auto-calls `stopCapture` if the screen closes while capturing.

---

## Gizmo

The gizmo system provides an interactive translate/rotate/scale handle rendered as a 2D overlay.

### Setup

```java
GlfwGizmoController gizmo = new GlfwGizmoController();
gizmo.setWindowHandle(client.getWindow().getWindow());

// Each frame (before manipulate):
gizmo.updateMousePosition(mouseX, mouseY);
gizmo.updateFrame(); // edge-detect left-click
```

### Manipulate

Call from `onRenderOverlay`. The gizmo draws itself into the render backend; flush with
`gizmo.getBackend().render(guiGraphics)` after `super.render()`.

```java
@Override
protected void onRenderOverlay(float x, float y, float w, float h) {
    float[] view = new float[16];
    float[] proj = new float[16];

    // Build view matching how the scene was rendered
    Matrix4f gizmoView = cam.buildViewMatrix();
    gizmoView.scale(RENDER_SCALE);
    gizmoView.translate(-0.5f, -0.5f, -0.5f);
    gizmoView.get(view);

    new Matrix4f().setPerspective(
            (float) Math.toRadians(cam.getFov()), w / h, 0.1f, 1000f)
            .get(proj);

    gizmo.manipulate(view, proj, x, y, w, h, inputEnabled);
}

// In render(), after super.render():
gizmo.getBackend().render(guiGraphics);
```

### Operations and space

```java
gizmo.setOperation(GizmoOperation.TRANSLATE); // TRANSLATE, ROTATE, SCALE
gizmo.setMode(GizmoSpace.LOCAL);              // LOCAL, WORLD

gizmo.setUseSnap(true);
gizmo.updateSnapValues(0.5f); // same value for translate/rotate/scale snap
gizmo.getSnapValues()[0] = 0.25f; // translate snap
gizmo.getSnapValues()[1] = 15f;   // rotate snap (degrees)
gizmo.getSnapValues()[2] = 0.1f;  // scale snap
```

Holding Shift during drag also activates snap.
Holding Ctrl during scale drag applies uniform scale on all axes.

### Reading the transform

```java
// Sync gizmo to a TransformationComponent
gizmo.recomposeMatrix(transform);

// Read back after drag
Vector3f    t  = gizmo.getTranslation();
Quaternionf lr = gizmo.getLeftRotation();
Vector3f    s  = gizmo.getScale();
Quaternionf rr = gizmo.getRightRotation();

// Or build the full matrix
Matrix4f mat = gizmo.buildMatrix();

// State
gizmo.isDragging();
gizmo.isHovered();
gizmo.isUsingSnap();
gizmo.getCurrentOperation(); // GizmoOperation
gizmo.getCurrentMode();      // GizmoSpace
```

### Abstract3DController

Helper base class that bridges the gizmo with a scene item and a `HistoryManager`.
Detects drag-start and drag-end transitions, calls overridable hooks, and delegates undo/redo.

```java
public class MyController extends Abstract3DController {

    private TransformationComponent savedAtDragStart;

    public MyController(AbstractGizmoController gizmo) {
        super(gizmo);
    }

    @Override
    protected void onGizmoDragStart() {
        savedAtDragStart = getCurrentTransform(); // snapshot before drag
    }

    @Override
    protected void onGizmoDragEnd() {
        TransformationComponent after = getCurrentTransform();
        if (!after.equals(savedAtDragStart)) {
            historyManager.push(new MyUpdateCommand(this, savedAtDragStart, after));
        }
    }

    @Override
    public void applyGizmoTransform() {
        // Called every frame while dragging — apply gizmo state to your data
        myItem.setTransform(new TransformationComponent(
                new Vector3f(gizmoController.getTranslation()),
                new Quaternionf(gizmoController.getLeftRotation()),
                new Vector3f(gizmoController.getScale()),
                new Quaternionf(gizmoController.getRightRotation())));
    }
}

// Each frame:
controller.updateGizmoInteraction(); // detects drag start/end
if (gizmo.isDragging()) {
    controller.applyGizmoTransform();
}
```

---

## Undo / Redo

`HistoryManager` maintains a bounded stack of `Command` objects.

### Command

```java
public class MoveBlockCommand implements Command {
    private final MyController ctrl;
    private final TransformationComponent before, after;

    public MoveBlockCommand(MyController ctrl,
                            TransformationComponent before, TransformationComponent after) {
        this.ctrl = ctrl; this.before = before; this.after = after;
    }

    @Override public void undo() { ctrl.applyTransform(before); }
    @Override public void redo() { ctrl.applyTransform(after);  }
}
```

### HistoryManager

```java
HistoryManager history = new HistoryManager(); // cap = 50

history.push(new MoveBlockCommand(ctrl, before, after));
history.undo();
history.redo();

history.canUndo(); // false when stack is empty
history.canRedo();

// React to stack changes (e.g. to update a UI button's enabled state)
history.setOnChange(() -> updateUndoButton());
```

`push` clears the redo stack. The oldest entry is dropped when the cap is exceeded (O(1)).

---

## Raycasting

`RaycastUtils` casts rays from screen-space coordinates using the vanilla game camera.

### Setup

Call once during client init (e.g. in `onInitializeClient`). Glue does this automatically — you
do not need to call it yourself:

```java
RaycastUtils.register(); // registers a AFTER_SETUP WorldRenderEvent listener
```

### Usage

```java
// Full hit result (blocks + entities)
HitResult hit = RaycastUtils.raycastViewport(
        mouseX, mouseY,          // screen coordinates
        200f,                    // max distance
        e -> !(e instanceof ItemEntity), // entity filter
        false);                  // include fluids?

if (hit instanceof BlockHitResult bhr) {
    BlockPos pos = bhr.getBlockPos();
} else if (hit instanceof EntityHitResult ehr) {
    Entity entity = ehr.getEntity();
}

// World-space point under a screen pixel at a given depth
Vec3 worldPoint = RaycastUtils.projectViewportGlobal(mouseX, mouseY, width, height, 10f);

// Camera-relative point (no world offset)
Vector3f local = RaycastUtils.projectViewport(mouseX, mouseY, width, height, 10f, new Vector3f());
```

> **Note:** `RaycastUtils` uses the vanilla game camera matrix captured from the last rendered
> frame. For picking inside a custom viewport (orbit or FPS camera), use
> `OrbitCameraController.createRay()` instead, which computes the ray from the correct view/
> projection matrices.

```java
// Custom-camera pick ray
OrbitCameraController.PickRay ray = cam.createRay(mouseX, mouseY, viewportW, viewportH);
// ray.origin() and ray.dir() are in scene space
float t = GizmoMath.intersectRayAABB(ray.origin(), ray.dir(), boxMin, boxMax);
```

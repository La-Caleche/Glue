# World Rendering (ShaderRenderer)

`ShaderRenderer` is a fluent builder for rendering colored or textured quads in world space using raw OpenGL. It bypasses MC's pipeline system and all Iris/Sodium hooks.

## How It Works

1. **ShaderRenderer** builds vertex/color arrays and computes the full MVP matrix
2. **DeferredDrawQueue** dispatches the draw command:
   - **Iris active** → deferred to `WorldRenderEvents.LAST` (after all world passes)
   - **Vanilla** → drawn immediately
3. **GlDirectRenderer** executes raw GL calls with its own compiled shader program

## Basic Usage

```java
@Override
public void render(MyBlockEntity entity, float partialTick, PoseStack poseStack,
                   MultiBufferSource bufferSource, int light, int overlay, Vec3 cameraPos) {
    if (RenderCompat.isRenderingShadowPass()) return;

    poseStack.pushPose();
    poseStack.translate(0.5, 1.5, 0.5);

    ShaderRenderer.world()
            .matrix(poseStack.last().pose())
            .position(-0.5f, -0.5f, 0f)
            .size(1f, 1f)
            .color(0f, 1f, 0.5f, 0.8f)
            .draw(bufferSource);

    poseStack.popPose();
}
```

## Per-Corner Gradient

```java
ShaderRenderer.world()
        .matrix(poseStack.last().pose())
        .position(-0.5f, -0.5f, 0f)
        .size(1f, 1f)
        .cornerColors(
                1f, 0f, 0f, 0.85f,  // top-left (red)
                0f, 1f, 0f, 0.85f,  // top-right (green)
                0f, 0f, 1f, 0.85f,  // bottom-right (blue)
                1f, 1f, 0f, 0.85f   // bottom-left (yellow)
        )
        .draw();
```

## Textured Quad (FBO Capture)

Render MC content into an offscreen FBO and display it as a textured quad:

```java
ShaderRenderer.world()
        .matrix(poseStack.last().pose())
        .position(-0.5f, -0.5f, 0f)
        .size(1f, 1f)
        .capture(256, 256, (captureStack, captureSource) -> {
            // Render into the capture FBO
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    light, overlay, captureStack, captureSource, level, 0);
        })
        .color(1f, 1f, 1f, 1f)
        .draw();
```

The `capture()` method:
1. Creates/resizes a temporary FBO
2. Binds it and renders the provided content
3. Restores the previous FBO
4. Stores the capture texture ID for use in `draw()`

## Deferred Execution

For custom GL rendering that should be deferred when Iris is active:

```java
ShaderRenderer.defer(() -> {
    // Raw GL code that runs after world compositing
    // Silently dropped during Iris shadow passes
});
```

## Centered Quad

```java
ShaderRenderer.world()
        .matrix(poseStack.last().pose())
        .position(0f, 0f, 0f)
        .size(1f, 1f)
        .centered(true)
        .color(1f, 1f, 1f, 0.5f)
        .draw();
```

## API

| Method | Description |
|---|---|
| `world()` | Creates a new builder |
| `matrix(Matrix4f)` | Sets the model matrix (from PoseStack) |
| `position(x, y, z)` | Quad origin |
| `size(w, h)` | Quad dimensions |
| `centered(bool)` | Center around position |
| `color(r, g, b, a)` | Uniform color (all corners) |
| `cornerColors(...)` | Per-corner gradient (TL, TR, BR, BL) |
| `capture(w, h, renderer)` | Render MC content into a capture FBO and use as texture |
| `draw()` | Submit to draw queue |
| `draw(MultiBufferSource)` | Same as `draw()` — param ignored, for BER API compat |
| `defer(Runnable)` | Queue a raw GL action through the deferred draw system |

## Important Notes

- Always check `RenderCompat.isRenderingShadowPass()` before drawing — raw GL in shadow passes creates ghost quads
- The `draw(MultiBufferSource)` overload ignores the buffer source — it exists for convenient use in `BlockEntityRenderer.render()`
- `DeferredDrawQueue.init()` must be called once during client init (Glue does this automatically)
- `capture()` uses a shared static FBO — don't call it from multiple threads

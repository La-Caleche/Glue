# World Rendering (ShaderRenderer)

`ShaderRenderer` is a fluent builder for rendering colored quads in world space using raw OpenGL. It completely bypasses MC's pipeline system and all Iris/Sodium hooks.

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

## Centered Quad

```java
ShaderRenderer.world()
        .matrix(poseStack.last().pose())
        .position(0f, 0f, 0f)
        .size(1f, 1f)
        .centered(true)  // Centers around position
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
| `draw()` | Submit to draw queue |
| `draw(MultiBufferSource)` | Same as `draw()` — param ignored, for BER API compat |

## Important Notes

- Always check `RenderCompat.isRenderingShadowPass()` before drawing — raw GL in shadow passes creates ghost quads
- The `draw(MultiBufferSource)` overload ignores the buffer source — it exists for convenient use in `BlockEntityRenderer.render()`
- `DeferredDrawQueue.init()` must be called once during client init (Glue does this automatically)

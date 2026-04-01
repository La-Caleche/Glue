# Transform Stack

Glue provides a Flywheel-inspired fluent `GlueTransformStack` that wraps MC's `PoseStack` with chainable transformation methods.

## Usage

```java
GlueTransformStack.of(poseStack)
        .pushPose()
        .rotateCentered(angle, Direction.UP)
        .translate(0.5, 1.5, 0.5)
        .scale(0.5f)
        .then(() -> {
            // Execute rendering code within this transform
            renderer.render(entity, poseStack, bufferSource);
        })
        .popPose();
```

## Available Transforms

From the `Transform<T>` interface:

| Method | Description |
|---|---|
| `translate(x, y, z)` | Translate |
| `scale(factor)` | Uniform scale |
| `scale(x, y, z)` | Non-uniform scale |
| `rotate(angle, axis)` | Rotate around axis |
| `rotateCentered(angle, Direction)` | Rotate around center (0.5, 0.5, 0.5) |
| `multiply(Quaternionf)` | Apply quaternion rotation |

From the `TransformStack<T>` interface:

| Method | Description |
|---|---|
| `pushPose()` | Push matrix |
| `popPose()` | Pop matrix |

From `GlueTransformStack`:

| Method | Description |
|---|---|
| `then(Runnable)` | Execute code within the current transform |
| `uncenterAxis(Axis)` | Translate -0.5 along an axis |
| `mirror(Axis)` | Translate -1 along an axis |

## Getting a Transform Stack

```java
GlueTransformStack stack = GlueTransformStack.of(poseStack);
```

This is backed by a mixin (`PoseStackMixin`) that attaches a `PoseStackTransform` to every `PoseStack` instance.

## Example: Spinning Item Renderer

```java
public void render(MyBlockEntity entity, float tickDelta, PoseStack matrices, ...) {
    float time = entity.getTicks() + tickDelta;

    GlueTransformStack.of(matrices)
            .pushPose()
            .rotateCentered((float) Math.toRadians(time * 2), Direction.UP)
            .rotateCentered((float) Math.toRadians(15), Direction.EAST)
            .translate(0.8, 1.75, -0.5)
            .translate(0, Math.sin(time * 0.025) * 0.1, 0)
            .then(() -> {
                itemRenderer.renderStatic(DISPLAY_ITEM,
                        ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                        light, overlay, matrices, vertexConsumers,
                        entity.getLevel(), 0);
            })
            .popPose();
}
```

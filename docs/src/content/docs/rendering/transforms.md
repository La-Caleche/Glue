---
title: Transform Stack
description: Rotate a pedestal display with fluent, balanced transforms over a callback-owned PoseStack.
artifact: glue-render
modId: glue-render
environment: client
---

# Transform Stack

This task centers and rotates an item above the Light Workshop pedestal. Use `GlueTransformStack`
when several `PoseStack` operations form one readable model transform; direct `PoseStack` calls are
still appropriate for a single operation.

## Rotate the Pedestal Display

The adapter mutates the supplied `PoseStack` immediately. This block-entity renderer excerpt keeps
the item centered over the block, rotates it around its own Y axis, and restores the incoming pose
even if item rendering fails:

```java [src/client/java/dev/example/lightworkshop/render/PedestalBlockEntityRenderer.java]
private static final ItemStack DISPLAY_ITEM = new ItemStack(Items.COPPER_BULB);

@Override
public void render(LumenPedestalBlockEntity entity, float tickDelta, PoseStack poseStack,
                   MultiBufferSource buffers, int packedLight, int packedOverlay,
                   Vec3 cameraPos) {
    float angle = (entity.getLevel().getGameTime() + tickDelta) * 2.0F;
    GlueTransformStack transform = GlueTransformStack.of(poseStack);

    transform.pushPose();
    try {
        transform.translate(0.5, 1.15, 0.5)
                .rotateYDegrees(angle)
                .scale(0.75F);

        itemRenderer.renderStatic(
                DISPLAY_ITEM,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                buffers,
                entity.getLevel(),
                (int) entity.getBlockPos().asLong());
    } finally {
        transform.popPose();
    }
}
```

The item appears above the center of the pedestal and turns smoothly. The renderer that owns
`itemRenderer` and the block entity registration are ordinary Minecraft setup; Glue only adapts the
pose passed to this callback.

## Read Transform Order Literally

Calls apply in the same order as calls on `PoseStack`. In the example, translation establishes the
item's center, rotation changes its orientation there, and scale changes its local size.

For geometry already expressed in unit-block coordinates, Glue also provides a direct centered
rotation around `(0.5, 0.5, 0.5)`:

```java
transform.rotateYCenteredDegrees(angle);
```

Use that form when the model occupies the unit block. Translate to an explicit pivot when it does
not.

## Balance Every Pose

`pushPose()` and `popPose()` affect the wrapped stack; the adapter has no separate matrix storage.
The explicit `try`/`finally` pattern is the safe default. `then(Runnable)` can make a small chain
compact, but it is not a scope guard:

```java
transform.pushPose()
        .translate(0.5, 1.15, 0.5)
        .rotateYDegrees(angle)
        .then(() -> drawDisplay(poseStack))
        .popPose();
```

If `drawDisplay` throws, `popPose()` is skipped. Reserve this style for calls whose failure policy is
already controlled by the owner.

::: details Operation reference

All operations return the same fluent adapter.

| Group | Common methods | Contract |
| --- | --- | --- |
| Translation | `translate`, `translateX/Y/Z`, `translateBack` | Three-component forms accept `float` or `double`; vector overloads accept supported Minecraft and JOML vectors. |
| Unit-block positioning | `center`, `uncenter`, `nudge` | Center moves by `(0.5, 0.5, 0.5)`; `nudge(seed)` applies a small deterministic offset. |
| Rotation | `rotate`, `rotateDegrees`, `rotateX/Y/Z`, degree variants | Names without `Degrees` take radians. Supported overloads accept Mojang axes, directions, direction axes, or JOML vectors. |
| Pivot rotation | `rotateAround`, `rotateCentered`, degree variants | Centered forms use unit-block center `(0.5, 0.5, 0.5)`. |
| Orientation | `rotateToFace`, `rotateTo` | `rotateTo` computes the quaternion from one direction vector to another. |
| Scale | `scale`, `scaleX/Y/Z` | Uniform and component forms delegate to the wrapped pose stack. |
| Matrices | `mulPose`, `mulNormal`, `transform` | Use `transform(pose, normal)` when an arbitrary pose matrix also changes normals. |
| Stack | `pushPose`, `popPose` | These are the wrapped stack's normal save and restore operations. |

Quaternion overloads accept `Quaternionfc`, so a `Quaternionf` does not need conversion.
:::

::: details Matrix and legacy edge cases

`mulPose(Matrix4fc)` changes only the top pose matrix. `mulNormal(Matrix3fc)` changes only its normal
matrix. Use `transform(pose, normal)`, `transform(PoseStack.Pose)`, or `transform(PoseStack)` when
both matrices belong together.

Despite its name, `mirror(Direction.Axis)` does not reflect geometry. It only translates one block:
axis `X` moves Z by `-1`, `Y` moves Y by `-1`, and `Z` moves X by `-1`.
`uncenterAxis(Direction.Axis)` uses the same unusual axis mapping at half distance. Prefer explicit
translations unless that mapping is exactly required.
:::

## Respect Pose Ownership

Call `GlueTransformStack.of(poseStack)` only while the pose owner permits. Do not retain a stack from
a block-entity renderer, [render event](./events.md), scene hook, or other callback for another frame.
There is no adapter cleanup method; balanced stack operations are the lifecycle.

`GlueTransformStack.of(...)` is the supported entrypoint. Constructing `PoseStackTransform`
directly is internal API.

## Next Steps

- Draw the rotating item through a custom shader in [Rendering Pipelines](./pipelines.md).
- Apply transforms inside an isolated preview in [3D Scene Viewport](./scene-viewport.md).
- Revisit [Rendering Events](./events.md) before transforming geometry from a world-debug hook.

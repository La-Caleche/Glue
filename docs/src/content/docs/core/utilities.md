---
title: Core Utilities
description: Rotate block shapes, solve common vector and color tasks, derive stable seeds, and add bounded undo history.
artifact: glue-core; glue-render
modId: glue; glue-render
environment: client and server; framebuffer work is client only
---

# Core Utilities

Use these helpers when the Light Workshop grows beyond registration: rotate one pedestal shape,
aim a probe ray, derive repeatable visual variation, or make an editing action undoable.

## Rotate a Block Shape

Define one north-facing shape and ask `VoxelShaper` for the horizontal variants.

```java [PedestalShapes.java]
private static final VoxelShape NORTH =
        Block.box(2.0, 0.0, 4.0, 14.0, 12.0, 16.0);
private static final VoxelShaper HORIZONTAL =
        VoxelShaper.forHorizontal(NORTH, Direction.NORTH);

public static VoxelShape forFacing(Direction facing) {
    VoxelShape shape = HORIZONTAL.get(facing);
    if (shape == null) {
        throw new IllegalArgumentException("Not horizontal: " + facing);
    }
    return shape;
}
```

**Expected result:** north returns the source shape; east, south, and west return rotated copies
around the block center. Up and down return null because this shaper contains only horizontal keys.

::: details Shape factories and mutation
| Factory | Populated keys |
| --- | --- |
| `forHorizontal(shape, facing)` | Four horizontal directions |
| `forHorizontalAxis(shape, axis)` | X and Z axes |
| `forDirectional(shape, facing)` | All six directions |
| `forAxis(shape, axis)` | X, Y, and Z axes |

`get(Direction)` and `get(Direction.Axis)` are nullable when that key was not populated.
`withShape(shape, facing)` adds or replaces one direction. `withVerticalShapes(upShape)` stores the
given up shape and a rotated down variant. `rotate(shape, from, to)` handles a one-off rotation.

`GlueVoxelShape` preserves a source shape's discrete voxel data while returning a
`CubePointRange` for each axis. Use it when a consumer specifically needs uniform voxel coordinate
lists rather than the source shape's custom coordinates.
:::

## Aim and Bound Probe Math

`VecHelper` covers common Minecraft `Vec3` operations. This example caps a ray length and finds its
nearest forward intersection with a spherical probe target.

```java
Vec3 direction = VecHelper.clamp(rawDirection, 8.0F);
Vec3 hit = VecHelper.intersectSphere(
        origin,
        direction,
        targetCenter,
        0.75D);

if (hit != null && hit.distanceToSqr(origin) <= direction.lengthSqr()) {
    spawnMarker(hit);
}
```

`intersectSphere` normalizes a nonzero direction and returns the nearest forward hit, the forward
exit when the origin is inside the sphere, or null for a miss, a zero direction, or intersections
only behind the origin. `clamp` leaves short vectors unchanged and limits longer ones to the given
length. Because `intersectSphere` normalizes its direction, the distance check is what enforces that
limit on the returned hit.

::: details Other focused VecHelper operations
The class also provides axis rotation, centered rotation and mirroring, `lookAt`, linear and
spherical interpolation, component-wise min/max and clamping, projection, cubic Bezier position and
derivative, planar line intersection, NBT/buffer Vec3 serialization, voxel-space conversion, and
axis-alignment tests. Prefer the correctly spelled `axisAlignedPlaneOf`; the
`axisAlingedPlaneOf` overloads are deprecated compatibility aliases.
:::

## Build a Packed Probe Color

`Color` is an immutable packed-ARGB value. Use an RGB factory for an opaque probe accent and read
the packed value where Minecraft expects an integer.

```java
Color amber = Color.ofRGB(255, 160, 48);
Color highlighted = amber.brighter(1.25D);

int packedArgb = highlighted.getColor();
int alpha = highlighted.getAlpha();
```

**Expected result:** `alpha` is `255`; brightening preserves it. Float factories use conventional
`0.0F` to `1.0F` components. Integer factories keep each component's low eight bits.

::: details Color factories and bounds
`ofOpaque(rgb)` forces alpha to 255; `ofTransparent(argb)` preserves all packed bits. `ofRGB` and
`ofRGBA` have float and integer forms, and `ofHSB` creates an opaque value. `getRed`, `getGreen`,
`getBlue`, and `getAlpha` return 8-bit channels. `brighter(factor)` and `darker(factor)` preserve
alpha. Pass a finite factor greater than `1.0`; the implementation rejects values less than or equal
to `1.0`, but callers must also avoid `NaN`.
:::

## Derive Stable Visual Variation

`SeedUtil.timePosSeed` combines a time bucket and quantized position. Use the overload with an
explicit time in tests.

```java
int liveSeed = SeedUtil.timePosSeed(500L, x, y, z, 1.0D);
int testSeed = SeedUtil.timePosSeed(
        10_000L,
        500L,
        x, y, z,
        1.0D);
```

Calls in the same 500 ms window and one-block position cell produce the same seed. `windowMs` and
`posQuant` must both be greater than zero. The implementation rejects non-positive values; callers
must also supply a finite, non-NaN `posQuant`.

## Make an Action Undoable

`HistoryManager` executes commands and keeps a linear undo/redo cursor. A new command after an undo
discards the redo future.

```java
final class ProbeSettings {
    int range = 8;
}

HistoryManager history = new HistoryManager(32);
ProbeSettings settings = new ProbeSettings();

history.execute(new Command() {
    @Override
    public String getLabel() {
        return "Increase probe range";
    }

    @Override
    public void execute() {
        settings.range = 12;
    }

    @Override
    public void undo() {
        settings.range = 8;
    }
});
```

**Expected result:** execution sets the range to 12, `history.undo()` restores 8, and
`history.redo()` executes the command again. `undo()` and `redo()` are no-ops when unavailable.

::: details History ownership and limits
The no-argument manager retains at most 128 commands; the integer constructor selects another
limit. Pass a positive limit; a non-positive limit still executes commands but retains no undo
history. `execute(command)` calls the command before storing it. Commands must be self-contained:
`undo()` must perfectly reverse `execute()`, and `execute()` must work again after undo.

Use `canUndo()`, `canRedo()`, and `getCursor()` for controls. `getCommands()` is unmodifiable.
`clear()` removes all commands and resets the cursor.
:::

::: details Smaller helpers and the client framebuffer exception
`BlockPosPayload` is a `CustomPacketPayload` record with `blockPos()`, public `ID`, and
`PACKET_CODEC`. Its ID is fixed to `glue:block_pos`. Core does not register it with Fabric
networking. Menu opening can pass its codec directly to `ExtendedScreenHandlerType` without a
standalone channel registration. For standalone messages, define and register a payload in your own
namespace instead of sharing this fixed global ID with other consumers.

`QuadConsumer<K, V, S, U>` is a functional interface whose only method is
`accept(K, V, S, U)`.

`FramebufferHelper` is not a Core class; it belongs to client-only `glue-render`. Its
`resizeOrCreate` creates or resizes an owned `RenderTarget`, and `clear` clears color and depth.
The texture/FBO ID accessors return `-1` when Glue's expected OpenGL backend objects are not
available. Run these operations on the render thread and destroy buffers for targets you own.
:::

## Next Steps

- [Apply the shape to the optional pedestal](./blocks.md).
- [Bind undo or redo from a client key](./keybindings.md).
- [Use render events and render-thread contracts](../rendering/events.md).

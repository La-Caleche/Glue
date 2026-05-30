# Utilities

## Color

`fr.lacaleche.glue.math.Color` — immutable color value with multiple creation methods.

```java
// From packed int
Color c1 = Color.ofOpaque(0xFF0000);       // Red, full alpha
Color c2 = Color.ofTransparent(0x80FF0000); // Red, 50% alpha

// From float components
Color c3 = Color.ofRGB(1f, 0.5f, 0f);
Color c4 = Color.ofRGBA(1f, 0.5f, 0f, 0.8f);

// From int components
Color c5 = Color.ofRGB(255, 128, 0);
Color c6 = Color.ofRGBA(255, 128, 0, 204);

// From HSB
Color c7 = Color.ofHSB(0.6f, 0.8f, 1f);

// Accessors
int packed = c1.getColor();
int r = c1.getRed();
int g = c1.getGreen();
int b = c1.getBlue();
int a = c1.getAlpha();

// Manipulation
Color brighter = c1.brighter(1.5);
Color darker = c1.darker(1.5);
```

## SeedUtil

`fr.lacaleche.glue.math.SeedUtil` — deterministic `int` seeds from a time window and a
quantized position (FNV-1a hash). The seed is stable within each `windowMs` time bucket and
for each `posQuant`-sized region, so it changes over time but stays consistent across calls in
the same window/cell:

```java
// Re-rolls every 500ms, quantized to whole-block positions
int seed = SeedUtil.timePosSeed(500L, x, y, z, 1.0);

// Deterministic overload — pass an explicit timestamp (e.g. for tests)
int seed = SeedUtil.timePosSeed(nowMs, 500L, x, y, z, 1.0);
```

`windowMs` and `posQuant` must both be `> 0` (throws `IllegalArgumentException` otherwise).

## FramebufferHelper

`fr.lacaleche.glue.client.utils.FramebufferHelper` — render target utilities:

```java
// Create or resize a framebuffer
RenderTarget fb = FramebufferHelper.resizeOrCreate(null, width, height);
fb = FramebufferHelper.resizeOrCreate(fb, newWidth, newHeight);

// Clear with a color
FramebufferHelper.clear(fb, 0f, 0f, 0f, 1f);

// Get GL texture ID
int texId = FramebufferHelper.getColorTextureId(fb);

// Get GL FBO ID (Iris-compatible)
int fboId = FramebufferHelper.getFramebufferId(fb);
```

## VoxelShape Utilities

### GlueVoxelShape

Wraps a `VoxelShape` and overrides its per-axis coordinates with a single `CubePointRange`,
so the shape renders as one clean box outline rather than being subdivided along the source
shape's edges:

```java
VoxelShape outline = new GlueVoxelShape(Block.box(3, 0, 3, 13, 16, 13));
```

### VoxelShaper

Create directional variants of VoxelShapes:

```java
VoxelShaper shaper = VoxelShaper.forDirectional(baseShape, Direction.UP);
VoxelShape north = shaper.get(Direction.NORTH);
```

## BlockPosPayload

`fr.lacaleche.glue.packets.BlockPosPayload` — network payload for sending block positions.

## QuadConsumer

`fr.lacaleche.glue.consumer.QuadConsumer` — functional interface accepting 4 arguments.

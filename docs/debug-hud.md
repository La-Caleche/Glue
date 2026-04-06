# FBO Debug HUD

Glue includes a built-in framebuffer debug HUD that can display all active framebuffer objects — both vanilla and Iris render targets.

## Activation

Press **F8** (default keybind, configurable in MC settings under "Glue" category).

## Controls

| Key | Action |
|---|---|
| Left/Right or Scroll | Change page |
| Up/Down | Move sidebar cursor |
| Enter | Toggle buffer visibility |
| -/+ | Adjust grid size (1×1 to 4×4) |
| [ / ] | Cycle filter mode (All / Color / Depth) |
| \` (backtick) | Toggle alt texture visibility |

## Features

- Automatic detection of Iris vs Vanilla mode
- Displays all Iris render target color + alt textures
- Real-time depth buffer linearization (downsampled to 256×256 grayscale)
- Paginated grid view with configurable grid size
- Sidebar with scrollable buffer list and toggle controls
- Filter by buffer type (Color / Depth / All)
- Hide Iris alt textures to reduce clutter

## Architecture

`FboDebugHud.INSTANCE` is a singleton that:

1. **On toggle:** Captures all Iris render targets via `RenderCompat.getIrisRenderTargetArray()` / `RenderCompat.getIrisTargetTextures()`, or falls back to vanilla framebuffer capture
2. **Each frame:** Captures depth via `captureDepthNow()` (called from a mixin after world rendering)
3. **On render:** Displays the captured textures in a paginated grid using `ExternalTexture` wrappers registered with MC's `TextureManager`

## API

The HUD is automatically registered by `GlueClient`. To interact with it programmatically:

```java
FboDebugHud.INSTANCE.toggle();
FboDebugHud.INSTANCE.isActive();
FboDebugHud.INSTANCE.handleScroll(delta);
```

## Iris Integration

When Iris is active, the HUD uses `RenderCompat` to query Iris's render pipeline:

- `RenderCompat.getIrisRenderTargetArray()` — gets all render target objects
- `RenderCompat.getIrisTargetTextures(target, name)` — extracts main + alt texture IDs with dimensions
- `RenderCompat.getIrisSceneDepthGlId()` — gets the scene depth texture for linearization

All reflection is centralized in `IrisProxy` and safe to call without Iris installed.

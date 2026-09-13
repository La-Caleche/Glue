---
title: Framebuffer Debug HUD
description: Inspect Glue color, material, Iris, and depth previews quickly with the built-in F8 overlay.
artifact: glue-render
modId: glue-render
environment: client
---

# Framebuffer Debug HUD

This task produces an annotated grid of the render textures Glue can inspect at the end of world
rendering. Use it for focused pipeline, post-effect, material, and depth diagnosis, not for normal
play or performance measurement.

## Run the F8 Check

1. Join a world and close every game screen.
2. Press **F8**. The binding can be rebound; until Glue ships language entries, Controls displays
   `key.glue.fbo_debug_hud` under `key.categories.glue`.
3. Use Left and Right to page through previews. Use Up and Down to select a sidebar entry, then Enter
   to hide or show it.
4. Press **F8** again to deactivate the HUD before measuring frame time.

The overlay does not require F3. Glue draws it at `RENDER_HUD`, before F3 text and later HUD layers.
Key processing pauses while a game screen is open.

<DocImage title="Annotated framebuffer debug HUD" description="The F8 overlay in vanilla mode with Main Color, Main Depth, GBuffer Albedo+N, MaterialID, and MaterialProps tiles, plus callouts for mode, page, filter, grid size, texture dimensions, and sidebar visibility." />

Replace this placeholder with a 1440x900 capture from a Light Workshop scene after the material
targets have been allocated. Annotate the header mode, one color tile, the contrast depth tile, one
material tile, texture dimensions, and the sidebar visibility marker.

## Diagnose One Question at a Time

- If a [pipeline](./pipelines.md) disappears, confirm main color is present and compare relevant
  material/capture attachments.
- If a [post effect](./post-effects.md) has no input, inspect Main Color before changing shader code.
- If depth-based work halos or drifts, confirm a depth preview exists, but do not read metric distance
  from its grayscale values.
- If an Iris pack was changed while the HUD was open, deactivate and reactivate before trusting the
  Iris target list.

The HUD is a curated viewer. It does not enumerate every framebuffer in Minecraft or every installed
mod.

::: details Keyboard controls

| Input | Action |
| --- | --- |
| Left / Right | Previous or next texture page. |
| Up / Down | Move the sidebar selection. |
| Enter / keypad Enter | Hide or show the selected texture. |
| `-` / keypad subtract | Reduce the grid to a minimum of 1x1. |
| `=` / keypad add | Increase the grid to a maximum of 4x4. |
| `[` / `]` | Cycle backward or forward through All, Color, and Depth filters. |
| Grave accent | Hide or show Iris alternate textures. |

The initial grid is 2x2. Grid size remains for the client process. Activating resets page, sidebar,
hidden entries, filter, and Iris alternates. Mouse-wheel paging is not connected to an input event;
use Left and Right even though the in-overlay help mentions scrolling.
:::

::: details Vanilla and Iris texture sets

The HUD chooses a mode at activation. If it reflects at least one target from the active Iris
pipeline, it uses Iris mode; otherwise it uses vanilla mode.

| Mode | Visible sources |
| --- | --- |
| Vanilla | Main color snapshot, generated main-depth preview when captured, live Glue material G-buffer attachments when allocated, and consumer-registered full-screen textures. |
| Iris | Readable Iris targets labeled `C0`, `C1`, and so on, their `_alt` textures, and generated depth when captured. |

Glue's built-in material entries are `GBuffer Albedo+N`, `GBuffer MaterialID`, and
`GBuffer MaterialProps`. Suppliers returning a non-positive ID are omitted. Those entries and
consumer extras appear only in vanilla mode.

Iris target IDs and dimensions are collected once at activation. Reactivate after changing a shader
pack or reallocating Iris resources. Vanilla main color and Glue's owned depth-copy target respond to
main-target resize.
:::

## Treat Depth as a Contrast Preview

Glue prefers Iris's no-hand scene depth. Otherwise it copies depth from Minecraft's main target.
Despite the displayed `(Linear)` label, the current image is not reconstructed eye-space depth.

The HUD synchronously reads the full source texture to CPU memory, samples it to 256x256, finds the
range below clear depth, and maps that range to inverted grayscale. Clear samples are black. This is
useful for occupancy and edge inspection only; values are not metric and cannot be compared across
frames.

::: danger Performance cost
Depth capture performs a full-resolution synchronous GPU readback and CPU conversion every active
frame. It can stall rendering and allocate substantial temporary memory. Disable the HUD before
profiling.
:::

## Register One Consumer Texture

`FboDebugHud.registerTexture` is the supported extension point for a full-screen 2D color texture:

```java [src/client/java/dev/example/lightworkshop/LightWorkshopClient.java]
private int previewTextureId = -1;

@Override
public void onInitializeClient() {
    FboDebugHud.registerTexture(
            "Light Workshop Preview",
            () -> previewTextureId);
}
```

Register once during client initialization. The supplier runs on the render thread in every active
vanilla-mode HUD frame. Return a current ID greater than zero, or a non-positive value while absent.

The API has no independent dimensions, duplicate check, or unregister operation. Supply only a
full-screen texture matching the main target, use a unique stable name, retain ownership, and return
a non-positive ID after releasing it. The HUD borrows and never deletes the texture.

::: details Cleanup and implementation limits

Deactivation deletes the HUD-owned main-color snapshot and grayscale depth texture and destroys its
depth-copy target. Reallocation releases old owned targets. Iris IDs and consumer IDs remain
borrowed. Texture-manager wrappers are not removed on deactivation.

The client-stopping callback does not separately deactivate an active HUD; final GL context teardown
releases process resources. Contributors still perform their own normal renderer cleanup.

Iris targets use numeric `C#` labels because reflected target objects expose no supported public
names. Color previews preserve values but scale to the grid cell. The Depth filter shows only the
generated grayscale image, not raw depth attachments. Hidden state is keyed by display name.

Activation, ticking, rendering, and depth capture are wired by Glue. `INSTANCE.render`, `tick`,
`captureDepthNow`, and raw target inspection through `RenderCompat` are implementation lifecycle,
not extension APIs.
:::

## Next Steps

- Return to [Rendering Pipelines](./pipelines.md#troubleshoot-the-result) with the observed attachment.
- Return to [Post-Processing Effects](./post-effects.md#troubleshoot-the-effect) with the main-color result.
- Use the supported boundaries in [Optional-mod Compatibility](./compatibility.md#do-not-build-on-glue-s-iris-internals).

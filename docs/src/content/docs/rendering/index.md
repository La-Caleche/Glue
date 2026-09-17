---
title: Rendering
description: Build client rendering features in a safe order, from a HUD label to shaders and interactive previews.
artifact: glue-render
modId: glue-render
environment: client
---

# Rendering

This guide track produces a visible rendering feature at every step: a HUD status, an orange block
outline, animated geometry, a custom shader, a post effect, and an orbiting preview. Use it when a
client feature needs a render hook or GPU resource that Fabric API does not already provide.

The examples extend **Light Workshop**, whose mod ID is `lightworkshop` and whose Java package is
`dev.example.lightworkshop`. Add `glue-render` and its Fabric mod dependency first; the
[getting started guide](../getting-started.md) covers the dependency setup.

::: warning Client boundary
Everything in this section belongs to the client-only `glue-render` artifact unless a page calls out
a shared interface such as `GlueBlock`. Keep imports under `fr.lacaleche.glue.client` out of common
and dedicated-server entrypoints.
:::

## Follow the Learning Path

1. [Rendering Events](./events.md) adds a small status line without owning any GPU resource.
2. [Block Outlines](./block-outlines.md) gives the pedestal an orange selected-block outline.
3. [Transform Stack](./transforms.md) rotates a display above the pedestal without leaking a pose.
4. [Rendering Pipelines](./pipelines.md) replaces that display's core shader.
5. [Post-Processing Effects](./post-effects.md) applies a complete grayscale world effect.
6. [3D Scene Viewport](./scene-viewport.md) presents the workshop region through an orbit camera.

These guides can be used independently. For a short item-to-light tutorial, follow the
[Light Workshop](../workshop/index.md). For existing runnable renderers, use the
[showcase](../showcase.md#rendering-and-lighting).

## Choose the Smallest API

| You need to... | Start with... |
| --- | --- |
| Draw text or a 2D overlay | [`RenderEvents.MAIN_RENDER`](./events.md#add-the-status-label) |
| Change a selected Glue block's line color | [A JSON outline](./block-outlines.md#create-the-orange-outline) |
| Reposition callback-owned geometry | [`GlueTransformStack`](./transforms.md#rotate-the-pedestal-display) |
| Change how submitted vertices are shaded | [`GluePipeline`](./pipelines.md#build-the-smallest-complete-pipeline) |
| Process the completed world color | [`GluePostEffectRenderer`](./post-effects.md#build-the-grayscale-effect) |
| Render an isolated interactive scene | [`AbstractViewportScreen`](./scene-viewport.md#build-the-orbit-preview) |
| Inspect color, material, or depth attachments | [Framebuffer Debug HUD](./debug-hud.md) |

Prefer a normal render event over a new mixin. Prefer Glue's pipeline and post-effect wrappers over
raw OpenGL when they fit, because the wrappers already handle the active Iris framebuffer and the
state they touch.

## Keep Ownership Visible

Three rules prevent most rendering failures:

- Register permanent event listeners once during client initialization. Glue events do not provide
  an unregister operation.
- Use callback-owned `GuiGraphics`, `PoseStack`, buffers, and world objects only during that
  callback. Do not retain them for another frame.
- Close or clean up every object that explicitly owns native resources. In this section that means
  `ShadedBufferSource.close()` and `AbstractSceneRenderer.cleanup()`.

Raw GL work must restore every framebuffer, draw buffer, viewport, texture, blend mode, and other
state it changes. Minecraft caches GL state, so restoring only the visible framebuffer is not enough.

## Tools Around the Path

- [Optional-mod Compatibility](./compatibility.md) explains when consumer code needs an Iris guard
  and when Glue already handles compatibility.
- [Framebuffer Debug HUD](./debug-hud.md) provides the fast F8 inspection workflow.
- [File Dialogs](./file-dialogs.md) handles native file selection for editors and preview tools.

## Next Steps

- Start with [Rendering Events](./events.md) for the first visible result.
- Follow the [Rendering Milestone](../workshop/rendering.md) to add a HUD to the probe.
- Read [Modules](../modules.md) before moving a rendering type across a source-set boundary.

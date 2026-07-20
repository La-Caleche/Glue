# Glue Wiki

**Glue** is a Fabric utility library for Minecraft 1.21.8 that simplifies mod development by providing typed registry wrappers, rendering utilities, shader systems, and Iris/Oculus compatibility.

The repository publishes `glue-core`, `glue-render`, `glue-lumos`, and `glue-lumos-client`.
Choose the narrowest module that provides the required feature; each module declares its own
dependencies, including the Glue modules it is built on. `glue-core` and `glue-lumos` load on both
client and dedicated server; `glue-render` and `glue-lumos-client` are client-only.

## Table of Contents

1. [Getting Started](getting-started.md)
2. [Registry System](registries.md)
3. [Blocks & Block Entities](blocks.md)
4. [Items & Data Components](items.md)
5. [Keybindings](keybindings.md)
6. [Core Shaders & Render Pipelines](core-shaders.md)
7. [Entity Shader Pipelines (GluePipeline)](entity-pipelines.md)
8. [Post-Processing Shaders](post-shaders.md)
9. [Dynamic Lights (Deferred)](lights.md)
10. [Block Outlines](block-outlines.md)
11. [Transform Stack](transform-stack.md)
12. [Iris / Oculus Compatibility](iris-compat.md)
13. [Events](events.md)
14. [FBO Debug HUD](debug-hud.md)
15. [3D Scene Viewport](scene-viewport.md)
16. [Utilities](utilities.md)
17. [File Dialogs](file-dialogs.md)
18. [Mod Compatibility (Reflection)](mod-compat.md)
19. [Sodium Material Adapter](sodium-material-adapter.md)

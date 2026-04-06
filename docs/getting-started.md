# Getting Started

## What is Glue?

Glue is a **Fabric 1.21.8** library mod that eliminates boilerplate when building Minecraft mods. It provides:

- **Typed registry wrappers** for blocks, items, block entities, particles, keybindings, shaders, etc.
- **Entity shader pipeline** (`GluePipeline`) for applying custom shaders to block entity rendering with Iris compatibility
- **Post-processing shader system** with dynamic uniform updates and Iris compatibility
- **Raw GL rendering** that bypasses MC's pipeline system and Iris hooks
- **Transform stack** (Flywheel-inspired) for fluent PoseStack manipulation
- **Custom block outline rendering** system
- **FBO debug HUD** for inspecting framebuffers and Iris render targets
- **Event system** for render hooks and debug callbacks
- **Data component** registration helpers
- **Reflection-based mod compat** for optional dependencies

## Project Structure

```
glue/
├── src/main/
│   ├── java/fr/lacaleche/glue/
│   │   ├── Glue.java                    # Common entry point
│   │   ├── block/                       # GlueBlock interface
│   │   ├── client/                      # Client-side systems
│   │   │   ├── GlueClient.java          # Client entry point
│   │   │   ├── shader/                  # GluePipeline, ShaderRenderer, PostShaderHandle, etc.
│   │   │   ├── render/                  # Block outlines, BlockRenderer
│   │   │   ├── transform/              # Transform stack + Flywheel stubs
│   │   │   ├── debug/                   # FBO debug HUD
│   │   │   ├── events/                  # RenderEvents, DebugEvents
│   │   │   ├── mixin/                   # Accessor + injection mixins
│   │   │   ├── extension/               # PoseStack extension interface
│   │   │   └── utils/                   # FramebufferHelper
│   │   ├── compat/                      # RenderCompat, IrisProxy, ModCompatManager
│   │   ├── registries/                  # All registry wrappers
│   │   ├── math/                        # Color, SeedUtil
│   │   ├── consumer/                    # QuadConsumer
│   │   ├── data/                        # TransformationComponent
│   │   ├── packets/                     # BlockPosPayload
│   │   └── shaper/                      # VoxelShaper, VecHelper
│   └── resources/
│       ├── fabric.mod.json
│       ├── glue.mixins.json
│       └── glue.client.mixins.json
└── src/testmod/                         # Example mod demonstrating all features
```

## Adding Glue as a Dependency

In your `build.gradle.kts`:

```kotlin
dependencies {
    modImplementation("fr.lacaleche:glue:${glueVersion}")
}
```

## Mod Initialization

Glue provides two entry points:

- `Glue` (common) — registers data component types and internal registries
- `GlueClient` (client) — registers outline renderers, deferred draw queue, FBO debug HUD, block selection events

Your mod should depend on `glue` in `fabric.mod.json`:

```json
{
    "depends": {
        "glue": "*"
    }
}
```

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
├── glue-core/                            # Registries, data, math, shapes, history, file dialogs
├── glue-render/                          # Pipelines, post effects, scenes, cameras, outlines
├── glue-lumos/                           # Deferred colored lights and shadow rendering
└── glue-showcase/                        # Runnable feature demonstrations
```

## Adding Glue as a Dependency

In your `build.gradle.kts`:

```kotlin
dependencies {
    modImplementation("fr.lacaleche.glue:glue-core:${glueVersion}")

    // Add only when the corresponding features are used.
    modImplementation("fr.lacaleche.glue:glue-render:${glueVersion}")
    modImplementation("fr.lacaleche.glue:glue-lumos:${glueVersion}")
}
```

`glue-lumos` depends on `glue-render`, which depends on `glue-core`. Rendering consumers should depend
on `glue-render`; dynamic-light consumers should depend on `glue-lumos`.

## Mod Initialization

The modules provide three entry points:

- `Glue` (common) — registers data component types and internal registries
- `GlueClient` (client) — registers outline renderers, deferred draw queue, FBO debug HUD, block selection events
- `GlueLumosClient` (client) — registers the deferred-light pass and owns its GL cleanup

Declare the modules used by your mod in `fabric.mod.json`:

```json
{
    "depends": {
        "glue": "*",
        "glue-render": "*",
        "glue-lumos": "*"
    }
}
```

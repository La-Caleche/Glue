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
├── glue-core/                            # Registries, data, math, shapes, history (both sides)
├── glue-render/                          # Pipelines, post effects, scenes, outlines, file dialogs (client)
├── glue-lumos/                           # Light model, persistence and sync (both sides)
├── glue-lumos-client/                    # Deferred colored-light renderer (client)
└── glue-showcase/                        # Runnable feature demonstrations
```

## Adding Glue as a Dependency

Glue is published to the private La Calèche Reposilite. Add the repository with read credentials
(from `~/.gradle/gradle.properties` or CI variables):

```kotlin
repositories {
    maven {
        name = "La Calèche Private"
        url = uri("https://reposilite.lacaleche.cc/private")
        credentials {
            username = providers.gradleProperty("lc.reposilite.readonly.name").get()
            password = providers.gradleProperty("lc.reposilite.readonly.token").get()
        }
    }
}
```

Then in your `build.gradle.kts`:

```kotlin
dependencies {
    modImplementation("fr.lacaleche.glue:glue-core:${glueVersion}")

    // Add only when the corresponding features are used.
    modImplementation("fr.lacaleche.glue:glue-render:${glueVersion}")
    modImplementation("fr.lacaleche.glue:glue-lumos-client:${glueVersion}")
}
```

Each module pulls its own dependencies transitively. Rendering consumers depend on `glue-render`;
to render dynamic lights depend on `glue-lumos-client` (it pulls `glue-lumos` + `glue-render`).
Server-side code that only describes lights depends on the model module `glue-lumos` alone.
`glue-core` underlies all of them.

## Mod Initialization

The modules provide four entry points, all registered automatically by Fabric — your mod never
calls them:

- `Glue` (common) — registers data component types and internal registries
- `GlueLumos` (common) — registers the light payload types and the server-side persistence
- `GlueClient` (client) — registers outline renderers, deferred draw queue, FBO debug HUD, block selection events
- `GlueLumosClient` (client) — registers the deferred-light pass and owns its GL cleanup

Declare the modules used by your mod in `fabric.mod.json`:

```json
{
    "depends": {
        "glue": "*",
        "glue-render": "*",
        "glue-lumos-client": "*"
    }
}
```

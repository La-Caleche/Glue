# Getting Started

## What is Glue?

Glue is a **Fabric 1.21.8** library mod that eliminates boilerplate when building Minecraft mods. It provides:

- **Typed registry wrappers** for blocks, items, block entities, particles, keybindings, shaders, etc.
- **Post-processing shader system** with dynamic uniform updates and Iris compatibility
- **Raw GL rendering** that bypasses MC's pipeline and Iris hooks
- **Transform stack** (Flywheel-inspired) for fluent PoseStack manipulation
- **Custom block outline rendering** system
- **Data component** registration helpers
- **Reflection-based mod compat** for optional dependencies

## Project Structure

```
glue/
├── src/main/           # Library code
│   ├── java/fr/lacaleche/glue/
│   │   ├── Glue.java                    # Common entry point
│   │   ├── client/                      # Client-side systems
│   │   │   ├── GlueClient.java          # Client entry point
│   │   │   ├── shader/                  # Shader rendering
│   │   │   ├── render/                  # Block outlines
│   │   │   ├── transform/              # Transform stack
│   │   │   ├── mixin/                   # Accessor mixins
│   │   │   └── utils/                   # Framebuffer helpers
│   │   ├── compat/                      # Iris/mod compat
│   │   ├── registries/                  # All registry wrappers
│   │   ├── math/                        # Color utility
│   │   ├── data/                        # Data components
│   │   └── shaper/                      # VoxelShape utilities
│   └── resources/
│       ├── fabric.mod.json
│       ├── glue.mixins.json
│       └── glue.client.mixins.json
└── src/testmod/          # Example mod demonstrating all features
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
- `GlueClient` (client) — registers outline renderers, deferred draw queue, block selection events

Your mod should depend on `glue` in `fabric.mod.json`:

```json
{
    "depends": {
        "glue": "*"
    }
}
```

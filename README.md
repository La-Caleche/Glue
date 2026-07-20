# Glue

A Fabric library for Minecraft **1.21.8** (Java 21, official Mojang mappings) that removes mod
boilerplate: typed registries, rendering pipelines and post effects, a deferred colored-light engine
(Lumos), world-save persistence, and Iris/Sodium compatibility. Full feature documentation lives in
[`docs/`](docs/README.md).

## Modules

| Module | Sides | Provides |
|---|---|---|
| `glue-core` | both | Typed registries, data components, math/shapes, utilities |
| `glue-render` | client | Shader pipelines, post effects, scenes, outlines, file dialogs, Iris compat |
| `glue-lumos` | both | Light model, persistence and sync (`Lumos` entry point) |
| `glue-lumos-client` | client | The deferred colored-light renderer |
| `glue-showcase` | dev only | Runnable demos of every feature — never published as a library |

Each module is its own Fabric mod; consumers depend on the narrowest set they need.

## Building

Requires **JDK 21** and read access to the private maven (either `REPOSILITE_TOKEN_NAME` /
`REPOSILITE_TOKEN_SECRET` in the environment, or `lc.reposilite.readonly.name` /
`lc.reposilite.readonly.token` in `~/.gradle/gradle.properties`).

```
./gradlew libraryJars           # the four library jars, into build/libs/
./gradlew remapJar              # same, plus the showcase jar
./gradlew compileJava test      # compile + tests
```

> `build`/`check` currently fail resolving a PMD snapshot in the `caldle` plugin — use
> `compileJava` + `test` instead.

## Running the showcase

```
./gradlew :glue-showcase:runClient    # demo client, run directory run/
./gradlew :glue-showcase:runServer    # dedicated server, run directory run-server/
```

The first server launch stops at the EULA: set `eula=true` in `run-server/eula.txt`. To join it
with the dev client, set `online-mode=false` in `run-server/server.properties` (not needed if the
client run is authenticated via the `lc.fabric.*` gradle properties).

Optional runtime toggles in `gradle.properties`: `glue.showcase.sodium`, `glue.showcase.iris`.

## Using Glue in your mod

In `settings.gradle.kts` (or a `repositories` block Loom can see):

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

In `build.gradle.kts`:

```kotlin
dependencies {
    modImplementation("fr.lacaleche.glue:glue-core:<version>")

    // Only what you use:
    modImplementation("fr.lacaleche.glue:glue-render:<version>")        // rendering APIs
    modImplementation("fr.lacaleche.glue:glue-lumos:<version>")         // light model, both sides
    modImplementation("fr.lacaleche.glue:glue-lumos-client:<version>")  // light renderer
}
```

And declare what you depend on in `fabric.mod.json` — see
[Getting Started](docs/getting-started.md) for the full walkthrough.

## Releasing

CI publishes every library module (and the showcase jar) to the private Reposilite when a tag is
pushed. Use annotated tags, unprefixed to match the existing ones: `git tag -a 2.0.0 -m "..."`.

# Glue

Glue is a modular Fabric library for Minecraft 1.21.8 using Java 21 and official Mojang mappings. It
provides typed registries, rendering pipelines and post effects, Lumos deferred colored lighting,
native dialogs, Chromium web surfaces, and scripted live-client tests.

- [Documentation](docs/README.md)
- [Getting started](docs/src/content/docs/getting-started.md)
- [Showcase](glue-showcase/README.md)

## Modules

Choose the narrowest artifact that owns the feature you need. Every published module is also a
separate Fabric mod.

| Artifact | Fabric mod id | Environment | Built on | Provides |
|---|---|---|---|---|
| `glue-core` | `glue` | both | - | Registries, packets/codecs, data components, math, shapes, and history. |
| `glue-render` | `glue-render` | client | `glue-core` | Pipelines, post effects, materials, outlines, scenes, compatibility, and native dialogs. |
| `glue-lumos` | `glue-lumos` | both | `glue-core` | Light model, synchronization, and persistence. |
| `glue-lumos-client` | `glue-lumos-client` | client | `glue-core`, `glue-render`, `glue-lumos` | Deferred colored-light rendering and shadows. |
| `glue-web` | `glue-web` | client | - | Web screens, HUDs, overlays and widgets with Java actions and native slots. |
| `glue-gametest` | `glue-gametest` | client, development | - | Scripted client tests, tools, screenshots, and reports. |
| `glue-showcase` | `glue-showcase` | both, development | all modules | Runnable demos and integration scenarios. |

The six library artifacts are published. `glue-showcase` is built as a development artifact but is
not published by release CI. Fabric API is required; Iris and Sodium integrations are optional and
runtime-guarded. Packages named `internal` are not supported API.

## Use Glue

Artifacts are hosted on the private La Calèche Reposilite. Add the repository to the consumer's
`build.gradle.kts` (or under `dependencyResolutionManagement.repositories` in
`settings.gradle.kts`):

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

Then add only the modules used by the mod:

```kotlin
dependencies {
    modImplementation("fr.lacaleche.glue:glue-core:<version>")

    modImplementation("fr.lacaleche.glue:glue-render:<version>")
    modImplementation("fr.lacaleche.glue:glue-lumos:<version>")
    modImplementation("fr.lacaleche.glue:glue-lumos-client:<version>")
    modImplementation("fr.lacaleche.glue:glue-web:<version>")
}
```

Declare the corresponding Fabric mod ids in `fabric.mod.json`. See
[Getting Started](docs/src/content/docs/getting-started.md) for dependency relationships and setup.

`glue-gametest` is a development dependency. Put it in a dedicated testmod or development source
set, never in the dependency graph or descriptor of a released mod.

## Build

The repository requires JDK 21 and read access to the private Maven repository. Credentials can come
from `REPOSILITE_TOKEN_NAME` / `REPOSILITE_TOKEN_SECRET`, or from
`lc.reposilite.readonly.name` / `lc.reposilite.readonly.token` in
`~/.gradle/gradle.properties`.

```shell
./gradlew compileJava test
./gradlew libraryJars
./gradlew remapJar
```

<details>
<summary>PowerShell</summary>

```powershell
.\gradlew.bat compileJava test
.\gradlew.bat libraryJars
.\gradlew.bat remapJar
```

</details>

- `libraryJars` writes the six remapped library jars to `build/libs/`.
- `remapJar` also builds the showcase jar.
- `build` and `check` are currently blocked by a PMD snapshot in the Caldle plugin; CI uses `test`
  plus remapped jars as the verification gate.

## Run the Showcase

```shell
./gradlew :glue-showcase:runClient
./gradlew :glue-showcase:runServer
```

<details>
<summary>PowerShell</summary>

```powershell
.\gradlew.bat :glue-showcase:runClient
.\gradlew.bat :glue-showcase:runServer
```

</details>

The client uses `run/`; the dedicated server uses `run-server/`. Runtime options and scripted test
commands are documented in the [showcase README](glue-showcase/README.md). The first server launch
stops for the Minecraft EULA; set `eula=true` in `run-server/eula.txt` before restarting it.
`glue.showcase.iris` and `glue.showcase.sodium` in `gradle.properties` control the optional rendering
integrations in the development profile.

The showcase frontend lives in [`glue-showcase/web/`](glue-showcase/web/README.md): one plain HTML/JS
input lab and React demos built with Vite. Showcase resource tasks require Node and pnpm; library
tasks remain independent of frontend tooling. See the [Glue Web guide](docs/src/content/docs/web/index.md).

## Release

`app.version` in `gradle.properties` is the release version. A pushed tag triggers CI publication of
the six library modules and stores the remapped showcase jar as an artifact. Before tagging, verify
that the tag name exactly matches `app.version`; use an annotated, unprefixed tag to match existing
releases.

# Repository Instructions

This is the canonical instruction file for coding agents working on Glue. Read it before changing
the repository. When editing maintained Java or Java tests, also read
[`development/java-style.md`](development/java-style.md).

## Instruction Order

When guidance conflicts, use this order:

1. The current task's explicit requirements.
2. Public API, persisted data, platform, and framework contracts.
3. This file's project invariants.
4. `development/java-style.md` for maintained Java and tests.
5. Nearby maintained code where the canonical documents leave a choice open.

Historical inconsistencies are not conventions. Preserve them only where compatibility requires it,
and do not reproduce them in new APIs.

## Project Snapshot

Glue is a modular Fabric library for Minecraft 1.21.8 using Java 21 and official Mojang mappings. It
provides typed registries, rendering and shader infrastructure, native dialogs, and Fabric client
GameTest helpers. Lumos, the colored-lighting mod, lives in its own repository and depends on Glue;
Porthole, the web-interface library, lives in its own repository and does not. The build uses Gradle Kotlin DSL,
Fabric Loom, and the in-house `fr.lacaleche.caldle` plugin.

- Maven group: `fr.lacaleche.glue`
- Core Fabric mod id: `glue`
- Version source: `app.version` in `gradle.properties`
- Public documentation: the separate [`glue-docs`](https://gitlab.lacaleche.cc/loccamy/java/glue-docs) repository
- Runnable examples and integration tests: [`glue-showcase/`](glue-showcase/README.md)
- Remapped output: `build/libs/` at the repository root; `glue-<version>.jar` is also the player jar

Glue is a library. Public behavior and supported APIs are what `glue-docs` documents; packages named
`internal` are implementation details.

## Projects

| Project | Mod id | Environment | Direct Glue dependencies | Responsibility |
|---|---|---|---|---|
| root (`glue`) | `glue` | both | none | `src/main`: shared registries, packets/codecs, math, shapes, and history. `src/client`: pipelines, post effects, materials, outlines, scenes, render events, compatibility, native dialogs, and key bindings. |
| `glue-gametest` | `glue-gametest` | client, development | none | Fabric client GameTest helpers: UI input, assertions, async waits and Iris. |
| `glue-showcase` | `glue-showcase` | both, development | `glue` (`glue-gametest` only in tests) | Run configurations, demos, and Fabric scenarios under `src/test/e2e`; not published by release CI. |

Composite cells live in `fr.lacaleche.composite` (and `.client`) with their own entrypoints, as a
separate mod would, until they move to one. They use only Glue's public API; Glue's own packages
must not depend on them, and generic logic they need belongs in Glue.

Keep environment boundaries explicit. Loom's split source sets enforce them: `src/main` cannot see
client classes, so shared models belong there and rendering and UI implementations in `src/client`.
Client mixins are declared in `glue.client.mixins.json`, which loads only on the client. Unit tests
see both source sets. No published project may depend on `glue-showcase`.

## Engineering Rules

- Read the owning module, neighboring implementation, contracts, and relevant tests before editing.
- Prefer existing registry wrappers, events, utilities, and lifecycle hooks over parallel machinery.
- Keep changes focused. Do not add speculative abstractions, compatibility shims, dependencies, or
  unrelated cleanup.
- Make ownership, thread boundaries, lifecycle, and cleanup explicit.
- Use a new dependency only after checking its API exposure, runtime footprint, and optional-mod
  behavior.
- Update the matching `glue-docs` page when public behavior changes. New public capabilities should have
  a small showcase example; changes to demonstrated behavior should update the existing example.
- Do not leave TODOs, stubs, placeholder implementations, commented-out code, or debug output.
- Base architectural recommendations on the actual code and constraints. State material corrections
  and tradeoffs plainly; do not endorse a weak design merely to agree with the maintainer.

## Rendering Invariants

- Prefer an existing `RenderEvents` or debug hook before adding a mixin. Mixins capture vanilla state
  or expose a seam; feature logic belongs in ordinary classes.
- Minecraft caches OpenGL state. Raw GL code must restore framebuffer, draw-buffer, texture, blend,
  and other touched state through the established helpers such as `SavedGlState`.
- Depth reconstruction uses captured `FrameMatrices`. Never rebuild the view matrix from
  `camera.rotation()` because it omits transforms such as view bobbing.
- Iris and Sodium are optional. Shipped runtime Iris access belongs behind guarded compatibility code
  such as `RenderCompat`; reflective access to Iris internals belongs in `ModCompatManager` rather
  than feature code. Development tests may call public Iris APIs only behind a mod-loaded guard.
  Everything must still load without Iris.
- Lumos consumes `SavedGlState`, `FrameMatrices`, `FramebufferHelper`, `AbstractSceneRenderer`,
  `GluePipeline`, `RenderEvents`, `RenderCompat` and `FboDebugHud`. Treat them as public contracts:
  a change there is checked against Lumos.

## Verification

Use the narrowest useful command while iterating, then verify affected dependents when a shared API
changes.

```shell
./gradlew compileJava compileClientJava
./gradlew test
./gradlew libraryJars
./gradlew :glue-showcase:runClient
./gradlew :glue-showcase:runServer
```

<details>
<summary>PowerShell</summary>

```powershell
.\gradlew.bat compileJava compileClientJava
.\gradlew.bat test
.\gradlew.bat libraryJars
.\gradlew.bat :glue-showcase:runClient
.\gradlew.bat :glue-showcase:runServer
```

</details>

`build` and `check` both run. Caldle before 2.5.1 resolved a PMD snapshot that exists in no repository;
2.5.1 pins a released one. The CI gate is `check` plus the remapped jars, which also validate resource
expansion and jar packaging.

Before reporting completion:

1. Compile the touched module and any dependent modules affected by API changes.
2. Run relevant unit and live-client tests.
3. Validate descriptors, generated resources, and jar packaging when they change.
4. Treat GLSL, MRT wiring, mixins, and JSON resources as runtime-sensitive: they have little or no
   compile-time protection and require an explicit in-game check.
5. Report exactly what ran, what passed, and what could not be verified.

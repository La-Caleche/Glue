# Repository Instructions

This is the canonical instruction file for coding agents working on Glue. Read it before changing
the repository. When editing maintained Java or Java tests, also read
[`docs/development/java-style.md`](docs/development/java-style.md).

## Instruction Order

When guidance conflicts, use this order:

1. The current task's explicit requirements.
2. Public API, persisted data, platform, and framework contracts.
3. This file's project invariants.
4. `docs/development/java-style.md` for maintained Java and tests.
5. Nearby maintained code where the canonical documents leave a choice open.

Historical inconsistencies are not conventions. Preserve them only where compatibility requires it,
and do not reproduce them in new APIs.

## Project Snapshot

Glue is a modular Fabric library for Minecraft 1.21.8 using Java 21 and official Mojang mappings. It
provides typed registries, rendering and shader infrastructure, Lumos deferred lighting, MCSX UI and
docking, native dialogs, and a scripted in-game test harness. The build uses Gradle Kotlin DSL,
Fabric Loom, and the in-house `fr.lacaleche.caldle` plugin.

- Maven group: `fr.lacaleche.glue`
- Core Fabric mod id: `glue`
- Version source: `app.version` in `gradle.properties`
- Public documentation: [`docs/`](docs/README.md)
- Runnable examples and integration tests: [`glue-showcase/`](glue-showcase/README.md)
- Remapped output: `build/libs/` at the repository root

Glue is a library. Public behavior and supported APIs are what `docs/` documents; packages named
`internal` are implementation details.

## Modules

| Module | Mod id | Environment | Direct Glue dependencies | Responsibility |
|---|---|---|---|---|
| `glue-core` | `glue` | both | none | Shared registries, packets/codecs, math, shapes, and history. |
| `glue-render` | `glue-render` | client | `glue-core` | Pipelines, post effects, materials, outlines, scenes, render events, compatibility, and native dialogs. |
| `glue-lumos` | `glue-lumos` | both | `glue-core` | Shared light model, codecs, synchronization, and persistence. |
| `glue-lumos-client` | `glue-lumos-client` | client | `glue-core`, `glue-render`, `glue-lumos` | Deferred colored-light renderer, material passes, shadows, and GLSL. |
| `glue-mcsx` | `glue-mcsx` | client | `glue-core` | Java-first ModernUI components, reactive values, Taffy layout, themes, and `.mcss`. |
| `glue-mcsx-dock` | `glue-mcsx-dock` | client | `glue-mcsx` | Retained dock layouts, tabs, splits, floating windows, persistence, and editor interaction. |
| `glue-gametest` | `glue-gametest` | client, development | none | Scripted live-client tests, tools, screenshots, and reports. |
| `glue-showcase` | `glue-showcase` | both, development | all library modules | Sole run configuration, demos, and integration scenarios; not published by release CI. |

Keep environment boundaries explicit. Shared models belong in both-side modules; rendering and UI
implementations belong in client modules. `glue-core` contains the legacy client-only
`KeybindingsRegistry`; do not expand that exception. No library module may depend on
`glue-showcase`.

## Engineering Rules

- Read the owning module, neighboring implementation, contracts, and relevant tests before editing.
- Prefer existing registry wrappers, events, utilities, and lifecycle hooks over parallel machinery.
- Keep changes focused. Do not add speculative abstractions, compatibility shims, dependencies, or
  unrelated cleanup.
- Make ownership, thread boundaries, lifecycle, and cleanup explicit.
- Use a new dependency only after checking its API exposure, runtime footprint, and optional-mod
  behavior.
- Update the matching `docs/` page when public behavior changes. New public capabilities should have
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
- Lumos identifies surfaces through one material G-buffer, not post-hoc depth matching. Material data
  and its owning depth are written in the same geometry draw through MRT.
- Material targets own their attachments while borrowing host color and depth. Sodium integration
  attaches those textures to Sodium's active framebuffer instead of duplicating the scene pass.
- A pixel no material class claimed cannot have reliable albedo reconstructed from an already-lit
  color. Preserve the `UNCAPTURED_LIGHT_CAP` contract.
- Minecraft 1.21.8's material outputs currently rely on the core-shader source patch and explicit MRT
  output constraints. Replace that seam only with an in-game-validated alternative.

Lumos is intended to support the difficult cases too: entities, particles, water, and reflective
materials. Do not silently downscope an agreed capability because it is hard. Within that scope, use
the smallest correct design.

## Verification

Use the narrowest useful command while iterating, then verify affected dependents when a shared API
changes.

```shell
./gradlew compileJava
./gradlew test
./gradlew libraryJars
./gradlew :glue-showcase:runClient
./gradlew :glue-showcase:runServer
```

<details>
<summary>PowerShell</summary>

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test
.\gradlew.bat libraryJars
.\gradlew.bat :glue-showcase:runClient
.\gradlew.bat :glue-showcase:runServer
```

</details>

The VitePress documentation is an isolated Node project under `docs/`. For documentation changes:

```shell
pnpm --dir docs install --frozen-lockfile
pnpm --dir docs build
```

`build` and `check` are currently blocked by a PMD snapshot resolved by the Caldle plugin. The CI gate
uses `test` plus remapped jars instead.

Before reporting completion:

1. Compile the touched module and any dependent modules affected by API changes.
2. Run relevant unit and live-client tests.
3. Validate descriptors, generated resources, and jar packaging when they change.
4. Treat GLSL, MRT wiring, mixins, and JSON resources as runtime-sensitive: they have little or no
   compile-time protection and require an explicit in-game check.
5. Report exactly what ran, what passed, and what could not be verified.

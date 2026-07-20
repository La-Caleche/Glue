# AGENTS.md — Project Context & Engineering Guidelines

> This file provides essential context for AI agents working on this project.
> Read this file fully before making any changes.

---

## Your Role

You are a senior engineer pair-programming on this project. The maintainer values clean architecture, simplicity, surgical changes, and high-quality technical output.

---

## Giving Analysis & Recommendations (Objectivity Rule)

When asked for an opinion, a technology/architecture choice, a review, or any "should we…" judgment, answer **objectively, neutrally, and grounded in evidence** — never to please:

- **Ground it in reality first.** Read the actual code and measure the actual scope before judging. Tie the recommendation to this project's real context and constraints.
- **Present the real tradeoffs.** Lay out the viable alternatives with honest pros and cons.
- **Separate preference from merit.** Distinguish what the maintainer *prefers* from what is *technically better*.
- **Take a clear position.** Provide a concrete, reasoned recommendation.
- **Push back when warranted.** If the evidence contradicts the maintainer's lean, say so plainly.

---

## Project Overview

**Glue** (mod id `glue`, group `fr.lacaleche.glue`) is a **Fabric utility library for Minecraft 1.21.8** — "Yet Another Minecraft Library". It provides typed registry wrappers, rendering and shader tooling (core pipelines, post-processing chains, a deferred dynamic-light subsystem), block outlines, transform stacks, native file dialogs, and Iris/Oculus compatibility shims. It is a **library**: other mods depend on it; it ships no gameplay content of its own.

The version lives in `gradle.properties` (`app.version`). User-facing documentation is the wiki in `docs/` (see `docs/README.md` for the table of contents) — **keep it updated when the public API changes**.

### Core Philosophy

- **Modular design.** Packages are domain-scoped; `internal` sub-packages are NOT public API and may change freely. The public surface is what `docs/` documents.
- **Simplicity over complexity.** No speculative abstractions. If a simple solution exists, prefer it over a complex "best practice" that adds overhead.
- **Code-first.** Registries, pipelines, and effects are defined in code (with optional data-driven JSON variants where the docs say so).

### Technology Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Platform | Minecraft 1.21.8, Fabric Loader + Fabric API |
| Mappings | Official Mojang mappings (via fabric-loom) |
| Build | Gradle (Kotlin DSL), fabric-loom, in-house `fr.lacaleche.caldle` plugin |
| Rendering | Blaze3D / OpenGL; some passes use raw GL (LWJGL) deliberately |
| Shaders | GLSL under `src/main/resources/assets/glue/shaders/` (`core/`, `internal/`, `post/`) |
| Optional compat | Iris (compileOnly; all access via `compat.RenderCompat`, guarded by `HAS_IRIS` — must work without Iris installed) |
| Native dialogs | LWJGL-NFD |
| Tests | JUnit 5 |

### Repository Structure

A composite build of **feature modules**, each its own Fabric mod with its own id, `fabric.mod.json`, and remapped jar (in the workspace-root `build/libs/`). A mod pulls only the modules it needs; a dedicated server loads only the ones that declare no client environment. Each module has `src/main` (+ `src/test` for JUnit); `internal` sub-packages are not API.

| Module | id | Environment | Role |
|---|---|---|---|
| `glue-core` | `glue` | both | Environment-agnostic base: typed registries (`registries`), networking/codecs (`packets`), `math`, `shaper`, `history`. Entry point `fr.lacaleche.glue.Glue` (main). No client/blaze3d code. |
| `glue-render` | `glue-render` | client | Client infrastructure: `client.shader` (+ `pipeline`, `effect`, `internal`), `client.render` (gbuffer, material, outline, scene), `client.events` (`RenderEvents`, `DebugEvents`), `client.debug`, `compat` (Iris), the client file dialogs and client registries (`KeybindingsRegistry`, `BlocksRendererRegistry`). Entry point `client.GlueClient`. Access widener `glue-render.accesswidener`. |
| `glue-lumos` | `glue-lumos` | both | Shared light model: `fr.lacaleche.glue.lumos.Light` / `LightType` (and the light codecs/sync as they land). Loads on both sides so the renderer and server persistence share one definition. |
| `glue-lumos-client` | `glue-lumos-client` | client | The deferred colored-light renderer: `client.render.light.*` (pipeline, shadow, scene, gl) + its GLSL. Depends on `glue-lumos` + `glue-render`. Entry point `client.render.light.GlueLumosClient`. |
| `glue-mcsx` | `glue-mcsx` | client | MCSX, the declarative UI library: `.mcsx` documents, reactive bindings, Tailwind-style styling, theming, docking, viewport embedding, on a vendored self-hosted ModernUI runtime (`mui` package — upstream conventions, excluded from lint). Assets keep the `mcsx` namespace. Standalone: depends on no other Glue module. |
| `glue-showcase` | `glue-showcase` | dev only | Development/demo mod and the sole run config; not shipped. Doubles as living documentation — every feature has a demo (see `glue-showcase/README.md`). New features should get one. |

A feature that spans environments (like lighting) splits into a both-sides model module and a client renderer, because a client-only module cannot load on a dedicated server (e.g. `glue-render`'s access widener references a client class) and Fabric mod dependencies are not per-environment. Future features (UI, …) follow the same split.

---

## Key Patterns

1. **Typed registries.** Content and client hooks go through Glue's registry wrappers (`BlocksRegistry`, `ItemsRegistry`, `KeybindingsRegistry`, `CoreShaderRegistry`, `PostShaderRegistry`, …) rather than raw Fabric/vanilla registration.
2. **Rendering hooks are events.** World/HUD-phase work hangs off `RenderEvents` (e.g. `POST_WORLD_RENDER`, `RENDER_HUD`) and `DebugManager`, which are driven from a small set of mixins. Prefer an existing event over a new mixin.
3. **GL state discipline.** Minecraft's `GlStateManager` *caches* GL state (blend func, texture bindings, FBOs…). Any raw-GL code must save/restore through the established helpers (`SavedGlState`, and restore FBO/texture bindings after blits) or it will corrupt later vanilla rendering in ways that surface far from the cause.
4. **Depth-buffer reconstruction uses `FrameMatrices`.** Never rebuild a view matrix from `camera.rotation()` — it misses view bobbing and the reconstruction slides against the world.
5. **Mixins are a last resort.** They exist to capture vanilla state or inject events, not to implement features. Keep them thin; put logic in plain classes.
6. **Iris is optional.** Anything touching Iris goes through the `compat` layer (`RenderCompat`) and must degrade gracefully when Iris is absent. Its API classes are imported directly; every call site short-circuits on `HAS_IRIS` so they are never resolved without Iris. Iris *internals* (pipeline manager, render targets) are reached reflectively through `ModCompatManager`.
7. **Material G-buffer, not depth-matching.** Lumos identifies what a pixel *is* (terrain / entity / particle / glass / water / metal) through a **material G-buffer** — real per-pixel material data written by the geometry pass — never by comparing a separately captured depth to the scene depth. Depth-matching could not distinguish a pane from a mob and is gone: every material class writes its id and its own owning depth in the same draw, and consumers confirm ownership with a world-space test against that packed depth. New surface types are added as material classes in this buffer, through the shared capture API — not as bespoke depth hacks. See `client.render.internal.gbuffer` (the MRT and the core-shader patch), `client.render.internal.material` (the per-frame gate and the Sodium adapter), and the G-buffer notes below.

---

## Lumos & the Material G-buffer (strategic direction)

Lumos is meant to be a **top-tier lighting engine** — proper static point lights with shadows and color that light *everything*, including entities and particles, and extend to water and reflective materials. Difficulty is not a reason to skip a feature; the maintainer has explicitly chosen to own the hard parts.

The foundation is a **G-buffer / material-capture subsystem** with a **clean, reusable API for creating render targets that do not conflict with Sodium or Iris**. Principles:

- **Reuse the host's buffers when offered, own them when not.** `GBufferTargets` owns the material attachments with raw GL but borrows Minecraft's main colour and depth, so a redirected draw still produces the ordinary scene. When Sodium is the terrain renderer, `SodiumTerrainMaterialCapture` hangs those same owned textures off Sodium's own bound framebuffer for the opaque pass rather than duplicating them.
- **The capture pass writes material data in the SAME draw as the geometry** (MRT), so material depth is inherently consistent with the scene — this is why the earlier separate-draw attempt failed. On MC 1.21.8 this requires bypassing Blaze3D's single-attachment `RenderPass` at the `com.mojang.blaze3d.opengl.GlCommandEncoder` level (the technique Iris uses; Iris source is available at `../../Iris` for reference — replicate only what's needed, not the whole shaderpack loader).
- **Vanilla core shaders are patched at the source seam** (`ShaderManager$CompilationCache.getShaderSource`), mirroring the existing `SodiumMaterialShaderPatch`. Note `#version 150` core shaders need `GL_ARB_explicit_attrib_location` for a second `layout(location=1)` output.
- **One material buffer, many consumers.** Terrain, entities, particles, glass, water and metal all land in it as material classes, so the deferred, shadow and reflection passes read one coherent buffer. A pixel no class claimed cannot have its albedo recovered — reflectance and illumination are one product in an already-lit sample — so `UNCAPTURED_LIGHT_CAP` bounds what Lumos may add there; at its current `0` those pixels keep their untouched vanilla look. The estimated albedo serves the different case where there is no material capability at all, and the cap never arms.

Build it incrementally and verify each stage in-game (GLSL and MRT wiring have no compile-time safety net).

---

## Conventions

### Code Style

- Java 21; prefer records for value objects; explicit types over `var` where it aids readability.
- **No decorator comments** (`// --- Helpers ---` is forbidden).
- **Useful documentation only.** Javadoc for classes and non-obvious contracts; comments explain *why*, never restate the code.

### Build System & Verification

- Compile every module: `.\gradlew.bat compileJava`
- Run tests: `.\gradlew.bat test`
- Launch the demo client (interactive; usually the maintainer does this): `.\gradlew.bat :glue-showcase:runClient`
- Launch the demo dedicated server: `.\gradlew.bat :glue-showcase:runServer` (run dir `run-server/`; accept the EULA on first launch)
- Build the library jars: `.\gradlew.bat libraryJars` (five library modules, in the workspace-root `build/libs/`); `remapJar` additionally builds the showcase jar

Note: the `build`/`check` tasks currently fail resolving a PMD snapshot in the `caldle` plugin, unrelated to the code — verify with `compileJava` + `test`, not `build`.

**Always verify before considering a task done:** compile the touched module(s). GLSL shaders and JSON resources have **no compile step** — they fail at runtime — so review them extra carefully and say explicitly when a change needs an in-game check.

---

## Important Rules

1. **Respect boundaries.** No library module references `glue-showcase`; `internal` packages are not API; keep client code out of the both-sides modules (`glue-core`, `glue-lumos`).
2. **Reuse existing utilities.** Before creating helpers, check `client.utils`, the registries, and the shader/pipeline infrastructure.
3. **Keep changes surgical.** No "just in case" features, no drive-by refactors.
4. **No placeholder content.** No TODOs or stubs that will not be immediately acted on.
5. **Docs follow the API.** A change to public behavior updates the matching page in `docs/` (and the showcase demo when one exists).

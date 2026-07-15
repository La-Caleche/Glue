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
| Optional compat | Iris (compileOnly; all access via reflection in `compat`/`IrisProxy` — must work without Iris installed) |
| Native dialogs | LWJGL-NFD |
| Tests | JUnit 5 |

### Repository Structure

Three source sets:

- **`src/main`** — the library. Entry points: `fr.lacaleche.glue.Glue` (main), `fr.lacaleche.glue.client.GlueClient` (client). Notable packages: `registries`, `client.registries` (typed registry wrappers), `client.shader` (+ `pipeline`, `effect`, `internal`), `client.render` (+ `light`, `outline`, `scene`, `gizmo`), `client.events` (`RenderEvents`, `DebugEvents`), `client.utils`, `client.debug`, `compat` (Iris reflection), `math`, `shaper`, `history`. Mixins: `glue.mixins.json` + `glue.client.mixins.json`; access widener `glue.accesswidener`.
- **`src/testmod`** — a demo mod (`glue-test`) that doubles as living documentation: every library feature has a demo there (see `src/testmod/README.md` for the feature→file map and keybinds). New library features should get a testmod demo.
- **`src/test`** — JUnit tests.

---

## Key Patterns

1. **Typed registries.** Content and client hooks go through Glue's registry wrappers (`BlocksRegistry`, `ItemsRegistry`, `KeybindingsRegistry`, `CoreShaderRegistry`, `PostShaderRegistry`, …) rather than raw Fabric/vanilla registration.
2. **Rendering hooks are events.** World/HUD-phase work hangs off `RenderEvents` (e.g. `POST_WORLD_RENDER`, `RENDER_HUD`) and `DebugManager`, which are driven from a small set of mixins. Prefer an existing event over a new mixin.
3. **GL state discipline.** Minecraft's `GlStateManager` *caches* GL state (blend func, texture bindings, FBOs…). Any raw-GL code must save/restore through the established helpers (`SavedGlState`, and restore FBO/texture bindings after blits) or it will corrupt later vanilla rendering in ways that surface far from the cause.
4. **Depth-buffer reconstruction uses `FrameMatrices`.** Never rebuild a view matrix from `camera.rotation()` — it misses view bobbing and the reconstruction slides against the world.
5. **Mixins are a last resort.** They exist to capture vanilla state or inject events, not to implement features. Keep them thin; put logic in plain classes.
6. **Iris is optional.** Anything touching Iris goes through the `compat` reflection layer and must degrade gracefully when Iris is absent.
7. **Material G-buffer, not depth-matching.** Lumos identifies what a pixel *is* (terrain / entity / particle / glass / water / metal) through a **material G-buffer** — real per-pixel material data written by the geometry pass — never by comparing a captured depth to the scene depth. Depth-matching cannot distinguish a pane from a mob and is being retired. New surface types (water, reflective metals, …) are added as material classes in this buffer, through the shared capture API — not as bespoke depth hacks. See `client.render.internal.material` and the G-buffer notes below.

---

## Lumos & the Material G-buffer (strategic direction)

Lumos is meant to be a **top-tier lighting engine** — proper static point lights with shadows and color that light *everything*, including entities and particles, and extend to water and reflective materials. Difficulty is not a reason to skip a feature; the maintainer has explicitly chosen to own the hard parts.

The foundation is a **G-buffer / material-capture subsystem** with a **clean, reusable API for creating render targets that do not conflict with Sodium or Iris**. Principles:

- **Reuse the host's buffers when offered, own them when not.** If Sodium/Iris expose usable targets, adapt to them (as `SodiumTerrainMaterialCapture` already does); otherwise build our own with raw GL.
- **The capture pass writes material data in the SAME draw as the geometry** (MRT), so material depth is inherently consistent with the scene — this is why the earlier separate-draw attempt failed. On MC 1.21.8 this requires bypassing Blaze3D's single-attachment `RenderPass` at the `com.mojang.blaze3d.opengl.GlCommandEncoder` level (the technique Iris uses; Iris source is available at `../../Iris` for reference — replicate only what's needed, not the whole shaderpack loader).
- **Vanilla core shaders are patched at the source seam** (`ShaderManager$CompilationCache.getShaderSource`), mirroring the existing `SodiumMaterialShaderPatch`. Note `#version 150` core shaders need `GL_ARB_explicit_attrib_location` for a second `layout(location=1)` output.
- **One material buffer, many consumers.** Entities/particles today; water and metals later — all as material classes, so shadow/glass/reflection passes read one coherent buffer.

Build it incrementally and verify each stage in-game (GLSL and MRT wiring have no compile-time safety net).

---

## Conventions

### Code Style

- Java 21; prefer records for value objects; explicit types over `var` where it aids readability.
- **No decorator comments** (`// --- Helpers ---` is forbidden).
- **Useful documentation only.** Javadoc for classes and non-obvious contracts; comments explain *why*, never restate the code.

### Build System & Verification

- Compile the library: `.\gradlew.bat compileJava`
- Compile library + testmod: `.\gradlew.bat compileTestmodJava`
- Run tests: `.\gradlew.bat test`
- Launch the demo client (interactive; usually the maintainer does this): `.\gradlew.bat runTestmodClient`
- Build the distributable jar: `.\gradlew.bat remapJar` (lands in the workspace-root `build/libs/`)

**Always verify before considering a task done:** compile the touched source set(s). GLSL shaders and JSON resources have **no compile step** — they fail at runtime — so review them extra carefully and say explicitly when a change needs an in-game check.

---

## Important Rules

1. **Respect boundaries.** Library code never references the testmod; `internal` packages are not API.
2. **Reuse existing utilities.** Before creating helpers, check `client.utils`, the registries, and the shader/pipeline infrastructure.
3. **Keep changes surgical.** No "just in case" features, no drive-by refactors.
4. **No placeholder content.** No TODOs or stubs that will not be immediately acted on.
5. **Docs follow the API.** A change to public behavior updates the matching page in `docs/` (and the testmod demo when one exists).

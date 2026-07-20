# Glue — Consolidated Review Report & Improvement Plan

> **Status: executed 2026-07-20.** All phases implemented and verified (POMs, tests, jars, server
> boot). Remaining external items: first CI run on push/tag validates the pipeline; the `caldle`
> PMD snapshot fix lives in the caldle repo.

*Compiled 2026-07-20 from three reviews (Claude session review, Opus 4.8, GPT 5.6 Sol) plus
empirical verification of every claim against the actual build. Findings below are only listed as
confirmed when they were reproduced, not because a review asserted them.*

---

## Part 1 — Verified findings

### Confirmed (reproduced against the real build)

| # | Finding | Evidence |
|---|---------|----------|
| C1 | **Published metadata has no inter-module dependencies.** `implementation(project(...).sourceSets["main"].output)` is a file dependency — invisible to publishing. | Generated `glue-lumos-client` POM: only fabric-loader + fabric-api. Consumers adding one module get missing classes. `docs/getting-started.md`'s transitivity claim is false today. |
| C2 | **`glue-server` is an empty placeholder being published.** | One 8-line `package-info.java` + `fabric.mod.json`; referenced by nothing. Violates the project's own "no placeholder content" rule. |
| C3 | **`app.version`/`app.group` duplicated in 6 module `gradle.properties`.** A release edit touches 7 files; missing one silently publishes a stale coordinate. | Read all six files; root already defines both. |
| C4 | **~26 lines of identical build config repeated per module** (plugins double-applied, MC/mappings/loader/API deps, JUnit, `processResources`, `remapJar` redirect). | Compared all six `build.gradle.kts`. |
| C5 | **CI `--build-cache` is a no-op across jobs** — `caches/build-cache-1` is not in the cache paths. | Inspected `.gitlab-ci.yml`. |
| C6 | **Release builds are non-reproducible**: `loomVersion=1.15-SNAPSHOT` + `--refresh-dependencies` on the publish job. Same tag can produce different jars. | Manifest shows resolved Loom `1.15.5`, so a pinnable version exists. |
| C7 | **Showcase publish contradicts docs.** CI publishes `glue-showcase` to the maven; README says it is never published as a library. | Both statements in-tree. |
| C8 | **The dependency layering is already clean.** Zero references from `glue-core`/`glue-render` to lumos. "Glue without Lumos" already holds at the artifact level — it is only hidden by C1. | Grepped both modules. |

### Disproved (both external reviews got these wrong)

| # | Claim | Reality |
|---|-------|---------|
| D1 | "No sources jars ship" (Opus, GPT) | **False.** Published `glue-lumos` to mavenLocal: `glue-lumos-1.9.1-sources.jar` is attached. Loom adds its own remapped sources jar; the Gradle warning both models quoted is real but does not reflect the output. |
| D2 | Fix is `implementation(project(":glue-core"))` (Opus) | **Would break dev compile.** The default artifact is the intermediary-remapped jar (`Fabric-Mapping-Namespace: intermediary` in the manifest) — cannot compile against mojmap sources. |
| D3 | Fix is `modApi(project(..., "namedElements"))` (GPT) | Wrong configuration: `modApi` is for external mod jars Loom must remap; `namedElements` is already dev-namespace. Correct form is plain `api`/`implementation` + `namedElements` (fabric-api's own pattern). **Compile-tested + POM-verified on `glue-lumos`**: dep appears as `fr.lacaleche.glue:glue-core:1.9.1`. |
| D4 | "`libraryJars` is dead / CI doesn't run it" (Opus, GPT) | Misreading. `libraryJars` is a local convenience task; CI's `publish` builds each `remapJar` because that *is* the publication artifact. |

### Rejected recommendations (deliberate, with reasons)

- **Repo split for Lumos** — all three reviews agree: no. Lumos and glue-render co-evolve (material
  buffers, G-buffer work ahead); split only when release cadence/maintainership genuinely diverge.
- **Renaming `glue-lumos` → `lumos-common` / mod id `lumos`** (GPT) — buys branding, not
  architecture. Costs: every `depends` block (internal + consumers), maven coordinates, docs churn.
  The boundary it would "reveal" already exists (C8) and becomes visible once C1 is fixed. Revisit
  at a major version, if ever.
- **Per-product versioning** — premature; one shared version until cadences diverge.
- **Pre-creating anything for the future UI library** — when `glue-ui` arrives it is one
  client-only module depending on `glue-render`. After this cleanup that is a ~20-line addition.

---

## Part 2 — Already done this session (for the record)

- Lumos API consolidation: side-agnostic `Lumos` entry point (`place`/`update`/`remove`/`spawn`/
  `attach`), op-4 gated request channel, HUD as the single tool (world `W<id>` rows), cone-bound
  validation + tests, identity-preserving client reconcile.
- `:glue-showcase:runServer` (verified booting to `Done`), showcase `fabric.mod.json` client-only
  deps moved to `recommends` (was a guaranteed server crash).
- `libraryJars` aggregate task; root `README.md` created; `docs/getting-started.md` +
  `docs/lights.md` + showcase README refreshed; CI rewritten (check job on branches, publish on
  tags, gradle-home cache, all five library modules published — was missing `glue-server` and
  `glue-lumos-client`).

---

## Part 3 — The plan

### Phase 1 — Publication correctness *(the consumer-breaking bug; do first)*

Replace every `sourceSets["main"].output` dependency with a real project dependency:

```kotlin
api(project(path = ":glue-core", configuration = "namedElements"))
```

Scope rule — `api` when the module's public API exposes the sibling's types, else `implementation`:

| Module | Dependency | Scope | Why |
|---|---|---|---|
| glue-render | glue-core | `api` | registries/math/util types appear in public signatures |
| glue-lumos | glue-core | `implementation` | core used internally (`Glue.id`), not exposed |
| glue-lumos-client | glue-lumos | `api` | `Light`, `Lumos`, `LightHandle` are the public surface |
| glue-lumos-client | glue-render | `api` | `EmissiveEmitter` exposes `EmissiveMaterial` |
| glue-lumos-client | glue-core | `implementation` | internal use |
| glue-showcase | all four | `implementation` | never consumed as a library |

Verify: `generatePomFileForMavenJavaPublication` per module — POM and `.module` must list the
sibling coordinates; `compileJava test` still green; `runServer` still boots; fix the
`docs/getting-started.md` transitivity paragraph to match reality (it becomes true again).

### Phase 2 — Delete `glue-server` *(placeholder, own-rules violation)*

Remove: the module directory; `"server"` from `settings.gradle.kts`; the `libraryJars` list entry;
`:glue-server:publish` from CI; rows in root README, `docs/README.md`, `docs/getting-started.md`
structure diagram; the AGENTS.md structure section. Reintroduce only when real shared server code
exists.

### Phase 3 — Configuration dedup *(release-hazard C3, then C4)*

1. Delete `app.version` + `app.group` from all module `gradle.properties` (root provides them;
   keep `app.name`). A release becomes a one-file edit.
2. *(Optional, separate commit — easy to drop)* Move the repeated block (MC/mappings/loader/API
   deps, JUnit + `useJUnitPlatform`, `processResources` expansion, `remapJar` destination) into the
   root `subprojects {}`; delete the per-module `plugins {}` blocks the root already applies.
   Module files keep only: module-specific deps, access wideners, run configs.
   **Gotcha:** the `libs` version catalog is not directly accessible inside root `subprojects {}` —
   use `the<VersionCatalogsExtension>().named("libs")`, or centralize only the task config and keep
   dependency lines per-module.
3. Move the hardcoded JUnit `5.10.1` and `lwjgl-nfd 3.3.3` versions into `libs.versions.toml`.

### Phase 4 — CI corrections

1. Replace `:glue-showcase:publish` with `:glue-showcase:remapJar` — resolves C7 in favor of the
   README: showcase jar stays a downloadable CI artifact, stops being a maven artifact.
   *(Decision point: if some workflow installs the showcase from the maven, keep publish and change
   the README instead.)*
2. Add `remapJar` to the check job (`./gradlew test remapJar`) so packaging + resource expansion
   are validated pre-tag, and drop the ineffective `--build-cache` flags (or cache
   `build-cache-1` — dropping is simpler).
3. *(Decision point: only if MR pipelines are used)* add
   `- if: $CI_PIPELINE_SOURCE == "merge_request_event"` with `workflow:` dedup rules.
4. Pin `loomVersion` to the resolved release (`1.15.5`) and drop `--refresh-dependencies` from
   publish → reproducible tags (C6). Keep the snapshot only if tracking Loom head is deliberate.

### Phase 5 — Lumos persistence hardening *(from the save-format review; cheap now, painful later)*

1. Add `version` (`optionalFieldOf("version", 1)`) to `PersistentLightState`'s codec **before any
   real save exists** — today, a codec parse failure silently wipes and re-saves an empty light
   file; a version field gives future migrations something to branch on. Prefer `optionalFieldOf`
   + defaults for any future `Light` field.
2. One-line comment on `DataFixTypes.LEVEL` noting the choice is arbitrary (no mod fix type
   exists); optionally switch to a quieter type (`SAVED_DATA_COMMAND_STORAGE`).

### Phase 6 — Showcase backlog *(nice-to-have, unblocked any time)*

- Demo for `Lumos.attach` / `LightAttachments` / `LightHandle` — the one public Lumos API with no
  showcase coverage (e.g. a flashlight keybind: spot attached to the player's eyes).
- `caldle` PMD snapshot fix (other repo) so `build`/`check` work again here.

---

## Suggested execution order

| Order | Phase | Risk | Verification |
|---|---|---|---|
| 1 | Phase 1 (namedElements) | Low — mechanism pre-tested | POM diff, compile, tests, server boot |
| 2 | Phase 2 (delete glue-server) | Trivial | compile, `libraryJars` output = 4 jars |
| 3 | Phase 3.1 (version dedup) | Trivial | `generatePomFile...` shows 1.9.1 everywhere |
| 4 | Phase 4 (CI) | Low — validated by next push/tag | first branch push runs check; next tag publishes |
| 5 | Phase 5 (persistence) | Low | existing `LightValidationTest` + a codec round-trip test |
| 6 | Phase 3.2/3.3 (consolidation) | Medium — pure refactor, separate commit | full `compileJava test remapJar` |
| 7 | Phase 6 (backlog) | — | in-game |

Phases 1–5 are surgical and independently revertible. Phase 3.2 is the only structural refactor —
kept last and separate so it can be reviewed or dropped without holding the fixes hostage.

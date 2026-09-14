# UI removal decision and history audit

Audit and maintainer decision: 2026-09-14.

## Approved direction

Keep the existing history and remove the retired UI implementation from the current development
tree. Do not reset to an earlier version or rebuild the branch by cherry-picking historical commits.
**Exclude `malo/keybinds-experimentation` entirely**: the maintainer considers its changes incomplete.
That branch can be revisited separately, including its non-UI changes.

The reference for the protected library code is
**`9577b5d3a894b7d9bb15b5ffa2f273e7b2b6e097`**, the tip of `malo/new-mcsx`.
JCEF is saved immediately after it at
**`c3ab10e85fe738836eabb5d730eb1f14b58f6145`** on `malo/experiment/jcef`:
`feat(jcef-experiment): add native chromium ui prototype`.
That 33-file commit has a verified GPG signature and includes the module, React build, showcase
integration, cursor support, tests and measurements.

## Why a rollback was rejected

The last MCSX-free ancestor is **`2d88ec2312c550dca8062d66850772d87468fbfe`**, from
2026-07-20 at 17:53 +02:00. Its complete source tree is identical to tag `2.0.0`
(`7a554eb184989bea5f668de77dfa36f27f31417f`).

| Milestone | Commit | Meaning |
| --- | --- | --- |
| Pre-MCSX | `2d88ec2` | Core, Render, shared/client Lumos and showcase already exist. |
| First implementation | `afabd94` | Declarative `.mcsx`, vendored ModernUI, docking and showcase controllers. |
| First dock follow-up | `d3914ac` | Original MCSX debug workspace. |
| Current main | `7e0c131` | Already contains MCSX v1 and the initial GameTest module. |
| Partial teardown | `5e3b95b` | Retains MCSX/Taffy scaffolding; also moves KeybindingsRegistry to Core. |
| Java-first rewrite | `b81eb36` | MCSX v2 foundation. |
| Dock split | `3bdc971` | Separate `glue-mcsx-dock` artifact. |

The first-MCSX commits `afabd94`, `4bdce1a` and `780821b` share the same tree; their parents
`2d88ec2`, `7a554eb` and `5109b96` also share a tree. These are rewritten copies, not distinct UI
implementations. Merge `9bb664f` has the same tree as its feature parent `fc54d19`.

There are **64 commits** between `main` (`7e0c131`) and `9577b5d`; **22** touch protected non-UI
library modules. Endpoint deltas affect 76 paths relative to main and 96 relative to the pre-MCSX
snapshot. Counts use `--no-renames`, so a move contributes both its source and destination.

| Module | Paths changed since main | Paths changed since pre-MCSX |
| --- | ---: | ---: |
| `glue-core` | 2 | 2 |
| `glue-render` | 35 | 39 |
| `glue-lumos` | 2 | 2 |
| `glue-lumos-client` | 22 | 35 |
| `glue-gametest` | 15 | 18 |

Examples that a rollback or subject-only filter would lose:

- Core block-entity factories and the module location of `KeybindingsRegistry`.
- Render viewport/input integration, Iris/Axiom compositing, native dialog contracts and lifecycle,
  registry reload behavior, GL restoration, camera controls, raycasts/outlines and borrowed textures.
- Lumos shaderpack rendering, material capture, shadow seams/atlas binding and block-light gating.
- GameTest factory registration, awaited server actions, failure propagation, screenshots, reliable
  reports and publication.
- Dedicated-server showcase registration, block/item resources, transformation presets, renderer
  cleanup, loot tables, translations, VitePress and development guidance.

In particular, `1ef6910` is labelled `fix(mcsx)` but changes four Render paths; `9147b23` is labelled
`docs(gametest)` but also changes two Java files. Retaining the existing history avoids splitting
those commits and accidentally dropping their non-UI fixes.

## Removal boundary

Remove `glue-mcsx` and `glue-mcsx-dock` completely, including sources, tests, mixins, resources,
build/catalog/CI wiring and maintained or deprecated UI documentation. Remove the showcase's old
control center, playground, expedition, Studio, file-dialog screen and scene screens, together with
their private demo controllers, UI tests, styles, layouts and translations.

Keep the five non-UI library modules exactly at the saved reference. The generic scene, gizmo,
history, native-dialog and `GameViewport` APIs remain supported; the removed scene classes are
showcase-specific consumers. Keep game content, models, renderers, lighting examples and the seven
generic rendering/native-dialog scenarios in `ShowcaseGameTests`.

The old `showcase-smoke` scenario exercises removed controls and is retired. Its sibling rendering
scenarios use ordinary APIs and remain. The current branch's generic `RealInput` helper moves out of
`gametest.mcsx` without adopting the version from the keybindings branch.

JCEF remains an independent experiment. F6 opens it directly; its test imports follow `RealInput`'s
new package. Lighting and effect actions use `/showcase` client commands. JCEF implementation cleanup
follows the UI-removal commit rather than being mixed into it.

## Library preservation checkpoints

The trees below are identical in `9577b5d` and the saved `c3ab10e`. They cover every tracked source,
test, resource, descriptor and module build file, including file modes and deleted paths.

| Module | Git subtree |
| --- | --- |
| `glue-core` | `399fb84a544349a526b313e2a705011d06a1b327` |
| `glue-render` | `5aa4dd6c91c9b5b9a89172bab464db0f0c1ff96f` |
| `glue-lumos` | `29709ae8c0de9eb076db78e44d057079a43f02a0` |
| `glue-lumos-client` | `4482bd917aef8c65601a688fa153d4749818f734` |
| `glue-gametest` | `2940d74bc3a001011858a09b94c1d1a5e9d1e24b` |

This comparison must remain empty after the cleanup:

```powershell
git diff --exit-code 9577b5d -- glue-core glue-render glue-lumos glue-lumos-client glue-gametest
```

Source equality establishes preservation, not runtime correctness of the changed module graph.
Compile/test the active projects, inspect fresh remapped jars, build documentation and verify
client/server loading. The retained viewport, shader and native-input scenarios need in-game checks.

## Cleanup verification

- `:glue-showcase:clean compileJava test libraryJars :glue-showcase:remapJar
  :jcef-experiment:remapJar` passed. Cleaning showcase first removed stale compiled UI/resources.
- The protected-module comparison against `9577b5d` is empty.
- `glue-test:jcef` passed **68/68** steps without Iris, including opening through F6, native input,
  cursors, popups, resize, HUD and close/reopen. The mod list contains no retired UI runtime.
- `glue-test:viewport-sky` passed **59/59** steps with Iris/Sodium loaded and shaders disabled. Day and
  Nether viewport captures were inspected. Active-shaderpack and Axiom behavior were not rerun.
- The rebuilt showcase jar contains the retained content, renderers, tests and neutral `RealInput`,
  with no retired UI classes/resources. Old UI module build/cache directories and their root jars
  were removed after checking for untracked source changes.
- `:glue-showcase:runServer -Pcaldle.useDevAuth=false` loaded the shared mods and registered showcase
  content, then stopped at the existing `run-server/eula.txt` setting `eula=false`. Full dedicated
  world startup is not verified.
- `pnpm --dir docs build` passed. Native-dialog cancellation and the new command actions were not
  exercised interactively during this cleanup.

## Other references examined

The audit covered 14 local branches, seven remote branch tips, tags, the stash and reflog-only commits.
`git ls-remote --heads --tags origin` confirmed the cached remote branch tips. Local `malo/new-mcsx`
is five commits ahead of its remote (`ec9564c`). The separate `review-ignis-ui-example` worktree was
clean. No reset, rebase, cherry-pick, branch deletion or push was performed during the audit.

| Reference | Result |
| --- | --- |
| `malo/keybinds-experimentation` at `56f93f0` | Two unique commits; explicitly excluded from this cleanup, including all Render/GameTest changes. |
| `malo/experiment/obscura` at `8d1f650` | Saved experiment; JOID assessment is in preceding `df36fd8`. |
| `malo/joid-evaluation` at `9577b5d` | No unique commit on this ref. |
| `malo/global-libraries-merge` at `fc54d19` | Already integrated. |
| `malo/code-review` at `b67c8a1` | Nine historical commits, but final tree exactly equals integrated `e8f60c4`. |
| `review-ignis-ui-example` at `197d468` | One patch-equivalent historical version commit. |
| `malo/test-volumetric` at `fe73534` | Four rewritten equivalents and one separate smoke-grenade experiment. |
| `deferred-light-better-result`, `try-better-light-shader`, `colored-light` | Old, distinct pre-modular renderer experiments; preserved on their existing branches. |
| `stash@{0}` at `426842b` | Only five added lines in the old UI build/catalog; retained as a Taffy experiment. |
| Reflog `d1e332f` | Same tree as retained `7dae9b7`. |
| Reflog `1bc1c29` | Retained `9147b23` fixes its missing closing Javadoc delimiter; no additional feature delta. |

Ignored runtime downloads, browser profiles, build products and local design directories are outside
the source commits. Retired module build outputs can be removed after checking their contents; keep
unrelated run profiles, saves and design work.

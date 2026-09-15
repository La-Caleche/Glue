# Glue Web development

`glue-web` promotes the JCEF experiment into a published client library. Supported contracts are in
the [public guide](../src/content/docs/web/index.md). Its implementation is under
`fr.lacaleche.glue.web.internal`; no public signature exposes JCEF or installer types.

## Packaging

The module uses the Shadow plugin already provided by Caldle. Fabric-mode Caldle disables the
default Shadow task, so this module explicitly enables it and selects only the `embedded`
configuration. Minecraft, Fabric and other Glue modules must never be shaded.

- The pinned `jcefgithub:146.0.10.1:all-relocated` wrapper and Commons IO 2.20.0 are embedded.
- The installer and its pre-relocated helpers move under `internal.shaded.jcefgithub`.
- Commons IO moves under `internal.shaded.commonsio`, avoiding Minecraft's older version.
- `org.cef` names remain intact because the native JNI library refers to them. This is not isolation
  of multiple independently initialized native CEF runtimes in the same JVM.
- Upstream resources and per-artifact license/notice copies are retained; minimization is disabled.
- Loom remaps the shaded jar. `namedElements` also serves that shaded implementation, so the showcase
  exercises it rather than an accidental unshaded dependency graph.
- The Maven publication and Gradle metadata expose the remapped artifact with Fabric dependencies,
  not the raw JCEF/Commons IO coordinates or an alternative unremapped Shadow variant.

## Runtime preloading and indicator

`CefRuntime.bootstrap()` starts the shared background future during client-mod initialization.
`RuntimeStartup` publishes immutable progress snapshots across installer/CEF/client threads and
ignores late updates after completion or shutdown. The installer reports percentages in 0..100,
with -1 for unknown progress. Preload failures are observed/logged even if no surface is ever opened.

The HUD-only events do not cover menus or Minecraft's initial loading overlay. One client mixin
captures `GameRenderer`'s final GUI submission seam; `RuntimeLoadingOverlay` appends its own balanced
GUI state before submission. It does not grab input, play a toast sound or access GL directly.
The renderer avoids font access before game loading completes and allocates no GuiGraphics once the
indicator has disappeared.

## Fixture boundary

`web-demo/` keeps the temporary React sources and historical measurements. Build it manually with
pnpm; no Gradle task invokes Node, installs web dependencies or includes frontend output in a jar.
The existing showcase screen and loopback HTTP host live under `glue-showcase/.../jcef/`.
They use only the public library API. No additional showcase interface was introduced.

The old pixel-correlated probe, CPU/GPU comparison mode and page-specific state callback are not
library APIs. Messaging uses origin-scoped strings through `glueQuery` and the
`glue:web-message` CustomEvent. Captures and delivery metrics remain general-purpose capabilities.

## Verification

```powershell
.\gradlew.bat :glue-web:test :glue-showcase:test :glue-web:remapJar :glue-showcase:remapJar
.\gradlew.bat :glue-web:generatePomFileForMavenJavaPublication :glue-web:generateMetadataFileForMavenJavaPublication
```

Unit tests cover frame ownership/damage/resize/closure, origin matching, configuration boundaries,
public signatures, progress transitions/fading and shaded packaging. Run `glue-test:web-startup`
to inspect the indicator over menus, inventory, gameplay, a hidden HUD and a loading overlay; this
development fixture substitutes only the progress snapshot and restores it afterward. The runtime
must actually preload without opening a surface. Run `glue-test:jcef` and `glue-test:jcef-sites`
scenarios through showcase for native input, page messages, popup/resize/cursor behavior and lifecycle.
The local scenario also leaves an unhosted surface for the library's shutdown hook; check its disposal
acknowledgement and CEF `TERMINATED` in the client log, not just Gradle's exit code.

The experiment's earlier measurements are archived in `web-demo/PERFORMANCE.md`; they do not establish
performance or cross-platform coverage for future revisions. Native validation has used Windows.

### Initial library migration (2026-09-15)

- Global `compileJava test libraryJars` and the showcase remapped jar passed without any frontend task.
- Glue Web's nine unit tests passed, including API boundaries and the actual shaded jar contents.
- The existing local scenario passed **64/64** steps; shutdown logged the unhosted surface's disposal
  and CEF `TERMINATED`. The site scenario passed **35/35** with Iris/Sodium loaded, no shaderpack active.
- A cold-install run with the fully relocated installer recreated the native installation and passed
  the same 64-step scenario, including module-owned shutdown.
- The site run covered La Calèche at 2560x1178, 60/30 FPS pacing, native PNG capture, Google native
  input/submission and the YouTube UI. Playback was not revalidated in this migration.
- Maven POM/Gradle metadata and remapped shading were inspected. The Java installer/helpers and
  Commons IO are private; JNI names remain `org.cef`. A separate packaged-launcher deployment has
  not been exercised.
- Dedicated-server loading registered shared showcase content, then stopped at the existing
  `run-server/eula.txt` value `eula=false`; full world startup was not tested.
- Documentation built successfully. The temporary React fixture was installed/built manually.
- Core, Render, shared/client Lumos and GameTest have no source diff against `9577b5d`.

### Background preloading and indicator (2026-09-15)

- The module/showcase test and remapped-jar command above passed. Glue Web now has **13 passing unit
  tests**, including four progress-state tests and packaging assertions for the mixin and translations.
- A cold native installation with Iris/Sodium loaded and no shaderpack passed **65/65** local scenario
  steps. Chromium was ready before the first surface was opened; the scenario then exercised the
  existing browser, HUD, input, messaging and lifecycle checks.
- `glue-test:web-startup` passed **31/31** steps both without Iris/Sodium and with them loaded, shaders
  disabled. Captures were inspected for the indicator over a title screen, inventory, gameplay,
  hidden HUD and loading overlay, plus failure, readiness and disappearance. Host input ownership
  remained intact.
- The indicator fixture injects progress snapshots after a real successful preload. Its download,
  extraction and failure displays are simulated; the separate cold installation is real. Network
  failure handling and the bar-only rendering during Minecraft's initial font loading have not been
  independently validated in game.
- The startup-only client log confirms background initialization before any surface and CEF
  `TERMINATED` on exit, covering shutdown when no browser has ever been opened.
- The remapped jar contains the startup implementation, client mixin descriptor and English/French
  labels. Documentation builds and the preserved-module comparison against `9577b5d` passed.

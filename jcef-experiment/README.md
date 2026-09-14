# JCEF experiment

Independent client-only prototype for Minecraft 1.21.8 / Java 21, on `malo/experiment/jcef`.
The branch starts at **`9577b5d`**, the same base as the parked Obscura experiment. This module has
no dependency on Graphene, MCSX or other Glue library modules; showcase is its consumer and launcher.
It is excluded from `libraryJars` and publishing tasks.

## Run

```powershell
.\gradlew.bat :glue-showcase:runClient
```

- F6: local React UI.
- `/jcef browser`: web navigation, starting at La Calèche.
- `/jcef demo`, `/jcef browser`, `/jcef hud`, `/jcef close` provide the same entry points.
- Ctrl+L selects the address; Enter navigates; F5 reloads; Escape closes the screen (or a native
  select popup first).
- F3 expands telemetry; F7 switches channel-conversion mode; F8 issues a latency probe;
  F9 toggles the CEF frame cap between 60 and 30.

Java 21, Node.js and pnpm 11.5.2 are needed to build the module and its React fixture. No Rust or
custom native compilation is required. The first open downloads the platform CEF distribution to
`run/jcef-experiment/146.0.10.1/<platform>/`; subsequent opens reuse it. Chromium's browser profile
is in `run/jcef-experiment/profile/`. These generated files are ignored by Git.

The native dependency is pinned to **`io.github.trethore:jcefgithub:146.0.10.1:all-relocated`**, matching
the dependency specified by the inspected Graphene backport. It resolves to JCEF `65f9d7b`, CEF
`146.0.10+g8219561`, Chromium **146.0.7680.179**. Commons IO 2.20.0 supplies the archive installer's
API absent from Minecraft 1.21.8's 2.17.0. Dependencies are exported for Loom's `namedElements`
development consumers and nested into the remapped experimental jar.

## Rendering architecture

```text
Chromium / CEF
  onPaint(BGRA premultiplied, dirty rectangles)
    → copy changed region into an owned CPU image
    → accumulate damage across all unconsumed callbacks
Minecraft render thread
    → snapshot the accumulated region into a reusable transfer buffer
    → upload that rectangle through Blaze3D
    → swap B/R in the fragment shader and blend premultiplied alpha
```

The callback's borrowed memory is never retained. A single accumulated damage rectangle bounds
bookkeeping and preserves earlier changes when the renderer skips CEF frames. The renderer never
holds the mailbox monitor during GPU submission. CPU buffers are reused; close releases their Java
references, while direct-buffer reclamation remains JVM-managed. GPU textures have explicit close.
CEF popup surfaces are captured and composited separately.

**GPU_BGRA** uploads the original BGRA bytes and swizzles channels in GLSL. **CPU_RGBA** performs an
integer-wise CPU swizzle before upload. Both use the same capture/damage path and correct
premultiplied-alpha blending. F7 compares the conversion location, **not the complete Graphene stack**.
Neither mode uses `onAcceleratedPaint`, shared GPU textures, or zero-copy browser-to-game transport.

All GPU operations use Minecraft's state-managed texture/GUI APIs. There are no experiment mixins
or raw OpenGL state changes. The fragment shader retains soft alpha edges instead of applying the
ordinary GUI alpha discard threshold. Browser backdrop filters only see browser content, not the
Minecraft world behind a transparent surface.

Keyboard, committed text, mouse and wheel events use the fork's direct CEF input APIs. DevTools is
used for explicit test inspection, not routine input or frame delivery. The local fixture's bounded
message-router channel carries commands to showcase; remote pages cannot use that game-command
channel. The separate probe acknowledgement is telemetry only.

Chromium's standard cursor changes are forwarded to GLFW: pointing hand, text, crosshair, directional
resize and move. The CEF callback only publishes its latest AWT cursor identifier; the screen applies
it on Minecraft's thread every rendered frame, independent of the web FPS cap. Handles are cached per
screen and destroyed on removal/client shutdown. Leaving the web area, losing window focus, opening
a Minecraft overlay, or returning to gameplay restores the default pointer. Read-only HUDs never
claim the cursor. Unsupported native shapes fall back to the arrow; custom-image, hidden and other
CSS cursors not exposed by this JCEF callback still require upstream API support.

## Telemetry

- **Game / CEF / Upload FPS**: Minecraft's rate, received main-surface paint callbacks, and uploads.
  A static page may produce zero new frames while its texture remains visible.
- **Capture / Stage / Convert / Upload**: CPU durations associated with consumed frames. Capture is
  callback copying; stage is packing accumulated damage; convert is the optional CPU swizzle; upload
  is the CPU duration of the Blaze3D upload call, not a GPU timestamp.
- **Dirty**: uploaded region area as a percentage of the full surface.
- **Coalesced**: intermediate callback versions merged before upload; their damage is preserved.
- **Age**: age of the last uploaded CEF image, not input latency.
- **Probe**: F8 sends JavaScript that colors an 8×8 marker. Its token is recognized in the actual
  `onPaint` pixels. The HUD reports the optional JS acknowledgement arrival, recognized paint, and
  first upload covering that paint. This is a synthetic JS-to-pixel round trip, not a physical
  keyboard/mouse-to-photon measurement. An unissued/unavailable probe shows zero.

Timings do not measure Chromium's internal style/layout/raster work, GPU readback, whole-process CPU
usage or native memory. These must be profiled separately. See [PERFORMANCE.md](PERFORMANCE.md).

## Verification

```powershell
.\gradlew.bat :jcef-experiment:test :glue-showcase:test
.\gradlew.bat :jcef-experiment:remapJar :glue-showcase:remapJar
.\gradlew.bat :glue-showcase:runClient "-Pglue.gametest=glue-test:jcef" "-Pglue.showcase.quickplay=Glue Showcase Verification-20260906-serial-a7c9" "-Pglue.showcase.iris=false" "-Pcaldle.useDevAuth=false"
.\gradlew.bat :glue-showcase:runClient "-Pglue.gametest=glue-test:jcef-sites" "-Pglue.showcase.quickplay=Glue Showcase Verification-20260906-serial-a7c9" "-Pglue.showcase.iris=true" "-Pcaldle.useDevAuth=false"
```

The quickplay world must already exist. The optional `caldle.useDevAuth=false` uses the normal
offline development identity. Read `report.txt`, because a failed scripted test can still exit
Minecraft with a successful Gradle status.

Verified on Windows 11 / i9-10900K / RTX 4080 SUPER:

- Mailbox unit tests cover copied ownership, skipped-frame damage, packed rows, resize and late callbacks.
- **68-step local client scenario passed**: React state, Unicode editing, native range control,
  native select popup, animation, both swizzle modes, pixel probes, resize, HUD ownership and reopening.
  Cursor coverage verifies Chromium hand/text/crosshair requests, GLFW application, dynamic CSS
  cursor changes without mouse movement, toolbar restoration and close/reopen with an active cursor.
- **40-step real-site scenario passed** with Iris/Sodium loaded, no active shaderpack: La Calèche,
  large-surface rendering, native capture, Google input/submission and YouTube initial UI.
- CEF reached `TERMINATED` on shutdown. An initial native focus re-entry loop was corrected by
  deduplicating `setFocus` echoes from `CefClient.onGotFocus`.
- Module and showcase compilation/unit tests passed. Both remapped jars were built; the experimental
  jar was inspected for its client descriptor, fragment shader, React assets, JCEF wrapper and
  Commons IO nested jars. The documentation build passed. A separate packaged-launcher deployment
  and its dependency precedence have not been tested.

Screenshots are in `run/screenshots/gametest/glue-test_jcef/` and `glue-test_jcef-sites/`.
The latter includes `native-lacaleche-2560.png`, a native-resolution CEF capture in addition to
Minecraft's composed screenshots.

La Calèche's gradient title and soft backgrounds were visually inspected. Google consent was
dismissed when offered, and native typing submitted a search, but Google returned a challenge;
**search results are not validated**. YouTube rendered its UI/consent dialog rather than the earlier
Obscura skeleton; automated playback and the full consent flow are not validated. No challenge bypass is used.

The maintainer subsequently tested YouTube manually and reported smooth 1080p playback, no perceived
audio delay or dropped frames, and an acceptable experience at a 30 FPS web cap. This is user-reported
validation on the development machine, not a controlled multimedia or low-end-hardware benchmark.

## Experimental limits

This is one browser screen / HUD integration, not a replacement UI API. Windows is the verified
platform; other native distributions and keyboard layouts need validation. IME composition,
OS drag-and-drop, native context menus, browser DevTools presentation, downloads, pointer lock and
world-texture embedding are not implemented or fully validated by this host. Windows scan-code
translation and committed Unicode text are exercised, not a complete cross-platform input matrix.

CEF and its helper processes remain the dominant native dependency. The renderer does not make
Chromium small. Applications should reuse the process-wide runtime; shutdown ends it for the rest
of that JVM. Running this experiment alongside another independently initialized CEF wrapper is not
a supported comparison setup. The borrowed callback buffer and native thread contracts remain
load-bearing even though the surrounding Java surface is small.

The source audit was informed by [Graphene](https://github.com/trethore/graphene), but this module
uses the [JCEF fork](https://github.com/trethore/jcef) directly through
[jcefgithub](https://github.com/trethore/jcefgithub). Their distributions retain their own licences
and third-party notices; React bundle licence comments are kept in generated assets.

# Runtime, Threading & Lifecycle

This page covers how a screen actually gets on the glass: opening it, the client bootstrap, the
threading rules you must respect, and the build/deps. The in-game host (`mui.*`) is a minimized,
self-hosted fork of ModernUI-MC (LGPL) vendored under `fr.lacaleche.glue.mcsx.mui` — you interact with it
through one small seam, `MuiModApi`.

## Opening a screen

The end-to-end recipe (from the testmod, the canonical example):

```java
McsxDocument document = DocumentLoader.loadFromClasspath("mymod:hello");
ViewBinder.DocumentResolver resolver = new ClasspathDocumentResolver();
ComponentRegistry registry = StandardComponents.create();          // + your native components
ScreenController controller = new HelloController();
McsxFragment fragment = new McsxFragment(document, controller, registry, resolver);

Minecraft.getInstance().schedule(() -> MuiModApi.openScreen(fragment));
```

Registering it behind a client command (again, from the testmod — note the `schedule` onto the main
thread):

```java
ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
    dispatcher.register(ClientCommandManager.literal("mcsx")
        .then(ClientCommandManager.literal("ui")
            .then(argument("file", StringArgumentType.greedyString())
                .executes(ctx -> {
                    Minecraft.getInstance().schedule(() ->
                        openDemo(StringArgumentType.getString(ctx, "file")));
                    return Command.SINGLE_SUCCESS;
                })))));
```

### The signatures involved

```java
// view.McsxFragment — the ModernUI Fragment wrapping one screen
public McsxFragment(McsxDocument document, ScreenController controller,
                    ComponentRegistry registry, ViewBinder.DocumentResolver resolver);

// mui.MuiModApi — the host seam (static, must be called on the main thread)
@MainThread public static void openScreen(Fragment fragment);
// plus createScreen(Fragment[, ScreenCallback][, Screen prev][, CharSequence title])
//   → <T extends Screen & MuiScreen>, if you want the Screen object to drive via Minecraft.setScreen

// view.ViewBinder — the binder (called for you by McsxFragment)
public static ViewInstance bind(McsxDocument document, ScreenController controller, Context context,
                                ComponentRegistry registry, DocumentResolver resolver);

@FunctionalInterface
public interface DocumentResolver { McsxDocument resolve(String id); }

// core.mcsx.DocumentLoader
public static McsxDocument loadFromClasspath(String id);
public static McsxDocument load(String id, ClassLoader loader);
```

`McsxFragment.onCreateView` calls `ViewBinder.bind(...)` and returns the root View; `onDestroyView`
calls `ViewInstance.close()`, which disposes **every** effect the binder collected. If you ever call
`ViewBinder.bind` directly (outside a fragment) you own calling `ViewInstance.close()`.

`ClasspathDocumentResolver` loads and caches parsed `.mcsx` docs from the classpath (so an `<import>`ed
component is parsed once even if used many times). It is **not thread-safe** — UI/render thread only.

## Client bootstrap

`fabric.mod.json` (mod id `mcsx`) registers:

- `main` entrypoint → `fr.lacaleche.glue.mcsx.Mcsx` (a library shell — just logs).
- `client` entrypoint → **`fr.lacaleche.glue.mcsx.mui.fabric.McsxModernUI`** — the real client boot: it
  registers the ModernUI application singleton, wires `Image` resolution through Minecraft's
  `ResourceManager`, and schedules renderer initialization. The Arc3D GL backend and the UI thread are
  bootstrapped by a mixin at `RenderSystem.initRenderer`.

The host resolves its implementation via `ServiceLoader`:
`META-INF/services/fr.lacaleche.glue.mcsx.mui.MuiModApi` → `fr.lacaleche.glue.mcsx.mui.fabric.MuiFabricApi`.
(This provider file is present — a previously-known gap that is now closed.)

> **Note:** `client/McsxClient.java` is **not** registered in `fabric.mod.json` and is effectively
> orphaned — the client init that matters is `McsxModernUI`.

## Threading — the discipline

MCSX runs across three threads; as an author you touch two of them, and the rules are simple but
load-bearing:

- **Open screens on the client main thread.** `MuiModApi.openScreen` is `@MainThread`; the host throws
  `IllegalStateException("Not called from main thread")` otherwise. From a command or a network
  callback, wrap the open in `Minecraft.getInstance().schedule(...)`.
- **Write signals on the UI thread.** `Signal.set`/`update` synchronously invalidate observers, which
  re-run effects that touch Views. Binding happens on the UI/render side — mutate your signals there,
  never from a worker thread. If async work produces a result, marshal it back before writing a signal.
- **GPU work is render-thread only.** A [surface](surfaces.md)'s `SurfaceSource.render` runs on the
  render thread; keep all GPU calls there and publish cross-thread state with `volatile`.
- **One binder builds one screen** synchronously; its internal scope/imports/slot are mutable fields.
  Don't reuse a `ViewBinder`, and don't share a `ClasspathDocumentResolver` across threads.

The reactive graph, the View tree, and the GPU are touched only on the UI/render threads — this is the
whole safety model.

## Lifecycle summary

```
build fragment ─▶ MuiModApi.openScreen (main thread)
   │  host opens a SimpleScreen, commits the fragment
   ▼
McsxFragment.onCreateView ─▶ ViewBinder.bind ─▶ live View tree + effects
   │  input flows MC → host → View tree → onClick → controller methods → signal writes
   │  → effects re-run → View setters mutate → next frame repaints
   ▼
close screen ─▶ McsxFragment.onDestroyView ─▶ ViewInstance.close (disposes all effects)
```

## Build & dependencies

- **Minecraft 1.21.8**, Fabric, official **Mojang mappings**, Java 21.
- **Rendering** (from Maven Central — *not* ModernUI-MC; MCSX self-hosts the runtime):
  - `dev.icyllis:modernui-core:3.13.0`
  - `dev.icyllis:arc3d-{core,sketch,engine,granite,opengl,vulkan,compiler}:2026.2.0`
  - All icyllis deps exclude fastutil / slf4j / log4j / jsr305 / jetbrains-annotations (Minecraft
    provides them) to avoid duplicate classes.
- **Plugins:** `fabric-loom` + `fr.lacaleche.caldle`.
- **Testmod source set** — inherits main's classpath, has its own `fabric.mod.json` (id `mcsx-test`,
  client entrypoint `TestmodClient`). Launch via the Loom **`testmodClient`** run
  (`./gradlew runTestmodClient`, or "Testmod Client" in IntelliJ), join a world, and run
  `/mcsx ui <file>`.

### The demo commands

`/mcsx ui <file>` opens a demo; the file → controller mapping is explicit in `TestmodClient`:

| Command | Document | Controller |
|---|---|---|
| `/mcsx ui demo` | `demo.mcsx` | `DemoController` |
| `/mcsx ui demo_unstyled` | `demo_unstyled.mcsx` | `DemoController` |
| `/mcsx ui kit` | `kit.mcsx` | `DemoController` |
| `/mcsx ui glass` | `glass.mcsx` | `GlassController` |
| `/mcsx ui editor` | `editor.mcsx` | `EditorController` |

## Verifying

- **Headless gate:** `./gradlew compileJava compileTestmodJava test` — compiles everything and runs the
  `core.*` unit tests plus the [screen linter](testing.md) over the real demos and component library.
- **In-game:** `view.*` and below have no unit tests (they need a live ModernUI `Context` + GL). Verify
  them by launching `testmodClient` and exercising `/mcsx ui`.

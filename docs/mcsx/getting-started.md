# Getting Started

## What is MCSX?

MCSX is a **Minecraft 1.21.8 Fabric** library for building reactive in-game UIs. You write a screen
as two files:

- a **`.mcsx` document** — the markup (tags, classes, bindings), resource-pack-shaped under
  `assets/<namespace>/ui/<path>.mcsx`;
- a **Java controller** — a `ScreenController` holding reactive state (`Signal`/`Computed` fields)
  and handler methods.

The binder turns the two into a live ModernUI `View` tree; the self-hosted runtime mounts it as a
Minecraft `Screen` and routes input. State changes flow through fine-grained signals: a signal write
re-runs exactly the effects that read it, each mutating one View property — no diffing, no reconciler.

## Project Structure

```
mcsx/
├── src/main/
│   ├── java/fr/lacaleche/mcsx/
│   │   ├── Mcsx.java                     # main entrypoint (library shell — just logs)
│   │   ├── core/                         # PURE java.* — headless-testable, no MC/ModernUI types
│   │   │   ├── reactive/                 # Signal, Computed, Effect, ReactiveRuntime, Source, Observer
│   │   │   ├── mcsx/                      # McsxTokenizer, McsxParser, AST records, DocumentLoader
│   │   │   ├── controller/               # ScreenController, @UIController, @OnClick
│   │   │   ├── style/                     # TailwindParser, StyleSpec, Colors
│   │   │   ├── layout/                    # FlexEngine (headless flexbox solver)
│   │   │   ├── theme/                     # Tokens, Theme, Themes
│   │   │   ├── lint/                       # McsxLinter
│   │   │   └── bind/                        # McsxBindException
│   │   ├── view/                          # ModernUI binder: ViewBinder, ViewStyles, FlexLayout,
│   │   │                                  #   FontRegistry, IconView, OverlayHost…
│   │   ├── mui/, mui.fabric/, mui.mixin/  # self-hosted ModernUI-MC runtime (VENDORED, LGPL)
│   │   ├── surface/                       # external-GPU-surface View for live previews
│   │   └── cursor/                        # custom GLFW cursors
│   └── resources/
│       ├── fabric.mod.json
│       ├── assets/mcsx/ui/components/*.mcsx  # the shipped component library (~30 files)
│       └── META-INF/services/…MuiModApi      # ServiceLoader provider for the host
├── src/test/                              # JUnit 5 — core.* + the screen linter
└── src/testmod/                           # reference demos + the /mcsx ui command
```

**The `core.*` purity boundary is load-bearing.** Everything under `core.*` is plain `java.*` and
unit-tested headlessly. ModernUI types appear only from `view.*` downward, and are verified in-game.

## Your first screen

### 1. The document — `assets/mymod/ui/hello.mcsx`

```xml
<div class="flex-col gap-2 p-4 rounded-lg bg-surface">
    <text class="text-lg text-default">{{label}}</text>
    <button onClick={ping} class="rounded-md bg-accent px-4 h-9">
        <text class="text-sm text-contrast">Increment</text>
    </button>
</div>
```

### 2. The controller

```java
@UIController("mymod:hello")
public final class HelloController extends ScreenController {

    private final Signal<Integer> count = signal(0);
    private final Computed<String> label = computed(() -> "Count: " + count.get());

    private void ping() {
        count.update(n -> n + 1);
    }
}
```

`{{label}}` reads the `label` computed by field name; `onClick={ping}` invokes the `ping` method.
Fields and methods may be `private` — the binder reflects into them.

### 3. Open it

```java
McsxDocument document = DocumentLoader.loadFromClasspath("mymod:hello");
ViewBinder.DocumentResolver resolver = new ClasspathDocumentResolver();
ComponentRegistry registry = StandardComponents.create(); // add native components here if needed
McsxFragment fragment = new McsxFragment(document, new HelloController(), registry, resolver);

Minecraft.getInstance().schedule(() -> MuiModApi.openScreen(fragment));
```

`MuiModApi.openScreen` is `@MainThread` — call it on the client main thread (from a command handler,
wrap it in `Minecraft.getInstance().schedule(...)`). See [Runtime](runtime.md) for the full lifecycle.

## Adding MCSX as a dependency

MCSX ships as the Glue module `glue-mcsx` (client-only). In `build.gradle.kts` (repository setup:
see [Getting Started](../getting-started.md)):

```kotlin
dependencies {
    modImplementation("fr.lacaleche.glue:glue-mcsx:${glueVersion}")
}
```

And in your `fabric.mod.json`:

```json
{
    "depends": {
        "glue-mcsx": "*"
    }
}
```

MCSX pulls in `dev.icyllis:modernui-core` + the `arc3d-*` artifacts itself and self-hosts the
ModernUI runtime — you do **not** add ModernUI-MC. See [Runtime](runtime.md) §Build for exact
versions.

## Verifying your work

- **Build gate:** `./gradlew :glue-mcsx:test :glue-showcase:test`. The `core.*` engine tests and the
  [screen linter](testing.md) run headlessly here.
- **`.mcsx` and JSON parse at runtime** — a typo won't fail the build but will break the screen
  in-game. The linter (`ScreenLintTest`, in the showcase) is the build-time stand-in that catches
  unknown tags, invalid classes, and unresolvable bindings before you launch.
- **`view.*` and below verify in-game only** — they need a live ModernUI `Context` + GL context.
  Launch `:glue-showcase:runClient` and use `/mcsx ui <file>`.

## Where to go next

- New to the markup? → [The `.mcsx` Language](mcsx-language.md)
- Wiring state and handlers? → [Controllers](controllers.md) and [Reactivity](reactivity.md)
- Styling and layout? → [Styling](styling.md), [Theme](theme.md), [Layout](layout.md)
- Building reusable UI? → [Components](components.md) and the shipped component library
- Embedding a 3D/particle preview? → [External GPU Surfaces](surfaces.md)

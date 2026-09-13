---
title: MCSX
description: Build a first native MCSX inspector screen, then choose the next interface task.
artifact: glue-mcsx
modId: glue-mcsx
environment: client
---

# MCSX

MCSX is Glue's Java-first client UI library. In this guide you will open a literal "Hello,
Inspector" screen for the Light Workshop, then use the same native View model for forms, reactive
state, themes, overlays, and docked tools.

## Outcome

The first milestone is deliberately static: one screen, one card, and text whose meaning is obvious
at the call site. Reactivity comes after the screen is known to mount correctly.

<DocImage title="Probe Inspector screen" description="Capture the Light Workshop Probe Inspector open in Minecraft, showing the Hello, Inspector heading and the ready message centered in one card." />

## Add MCSX

MCSX is client-only. Add the artifact with the same Glue version as the rest of the mod:

```kotlin [build.gradle.kts]
dependencies {
    modImplementation("fr.lacaleche.glue:glue-mcsx:<glue-version>")
}
```

Maven transitivity supplies ModernUI, but Fabric still needs the MCSX mod dependency:

```json [fabric.mod.json]
{
  "depends": {
    "glue-mcsx": "<glue-version>"
  }
}
```

Keep imports of MCSX and ModernUI in client code. Neither may be initialized by a dedicated-server
entrypoint or a shared class that the server loads.

## Open the First Inspector

Create a complete `UiScreen`. `create(Ui)` receives a facade already bound to the right ModernUI
`Context`.

```java [ProbeInspectorScreen.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.UiScreen;
import icyllis.modernui.view.View;

public final class ProbeInspectorScreen extends UiScreen {

    @Override
    protected View create(Ui ui) {
        return ui.screen(
                ui.card(
                        ui.literalHeading("Hello, Inspector"),
                        ui.literalCopy("The Light Workshop probe inspector is ready.")
                )
        );
    }
}
```

Open a fresh instance from Minecraft's client thread. Passing the current screen lets ModernUI
restore it when this screen closes.

```java
Minecraft client = Minecraft.getInstance();
new ProbeInspectorScreen().open(client.screen);
```

The hosted screen's Escape action restores that previous screen. An in-screen Back action should use
the same stored previous-screen target rather than calling the wrapper's vanilla `onClose()`, which
does not know mui-lite's Back contract. Use `Minecraft.setScreen(null)` only for an intentional
dismissal to gameplay; doing so discards the previous-screen path.

### Expected result

Minecraft replaces the current screen with a scroll-safe MCSX root. The card is centered while it
fits and can scroll if later fields exceed the window height. The world does not pause by default.

## Why This Works

`Ui.screen(...)` creates a full-size vertical `ScrollView`, with an `ui-screen` root and an
`ui-viewport` content column. `Ui.card(...)` adds the semantic `ui-card` class, rendered as a compact
dialog shell by the built-in MCSX stylesheet. Those classes are stable styling hooks, and a supplied
MCSS resource can override the built-in rules without having to recreate the component baseline.

MCSX components are native ModernUI Views. There is no XML or alternate markup frontend. Use the
typed `Ui` factories for the common controls, then ordinary ModernUI View APIs for behavior that the
facade does not wrap.

The example uses `literalHeading(...)` and `literalCopy(...)` because the English strings are raw
display text. The plain `String` overloads such as `heading("lightworkshop.title")` interpret their
argument as a Minecraft translation key. The [Components guide](./components.md) puts that rule into
a working form.

## Lifecycle and Ownership

Each `UiScreen` instance is single-use. Build a new one each time the inspector opens. `create(Ui)`
must return a non-null root, and ordinary Fragment lifecycle overrides remain available. An
`onDestroy()` override must call `super.onDestroy()`.

Component handlers execute on ModernUI's UI thread. They may update UI-owned MCSX state directly,
but must schedule work onto Minecraft's client thread before reading or changing game-owned state.
The [Reactivity guide](./reactivity.md) shows that handoff next to the first binding.

::: details Screen policy and public API boundaries
`UiScreen` maps three protected policy hooks to the hosted Minecraft screen:

| Hook | Default | Effect |
| --- | --- | --- |
| `pausesGame()` | `false` | Whether the hosted screen pauses the game |
| `drawsDefaultBackground()` | `true` | Whether ModernUI draws its normal screen background |
| `canClose()` | `true` | Whether the normal close action is accepted |

This documentation treats supported types outside packages named `internal` as public API. Publicly
visible resource-loader internals and methods named `internal...` are implementation details. The
`glue-mcsx` artifact exposes ModernUI types because they are part of the public composition model and
supplies the `mui-lite` runtime.
:::

## Next Steps

- [Build the Probe Inspector form](./components.md) with fields, validation, and actions.
- [Bind state and mirror Minecraft data](./reactivity.md) without crossing thread ownership.
- [Apply a theme and MCSS stylesheet](./themes-and-styles.md) with reload validation.
- [Choose a screen, overlay, HUD, or Dockspace](./overlays.md) for the final presentation.
- [Grow the inspector into a workspace](../dockspace/index.md) when Scene, Inspector, and Log need to coexist.

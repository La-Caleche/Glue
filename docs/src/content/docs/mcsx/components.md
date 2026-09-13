---
title: Components
description: Build a Probe Inspector form, then compose and retain MCSX Views safely.
artifact: glue-mcsx
modId: glue-mcsx
environment: client
---

# Components

This task turns the static Light Workshop screen into a small Probe Inspector form. It introduces
text, input, validation, and actions before the component catalog.

## Outcome

The inspector edits a probe name and an enabled flag. The Apply button becomes available only after
the name contains non-whitespace text.

<DocImage title="Probe Inspector form" description="Capture the Probe Inspector with a Probe name field, an Enable probe checkbox, a validation line, and an Apply button; include both invalid and valid states if possible." />

## Build the Form

Replace the first screen with this complete version:

```java [ProbeInspectorScreen.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.UiScreen;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import icyllis.modernui.view.View;

public final class ProbeInspectorScreen extends UiScreen {

    private final Signal<String> name = Signal.of("");
    private final Signal<Boolean> enabled = Signal.of(true);
    private final Value<Boolean> validName = this.name.map(value -> !value.isBlank());

    @Override
    protected View create(Ui ui) {
        return ui.screen(
                ui.card(
                        ui.literalHeading("Probe Inspector"),
                        ui.literalCopy("Name the probe before applying it."),
                        ui.literalField("Probe name")
                                .text(this.name)
                                .onSubmit(this::apply),
                        ui.literalCheckbox("Enable probe").checked(this.enabled),
                        ui.literalText("A probe name is required.")
                                .visible(this.validName.map(valid -> !valid))
                                .classes("validation"),
                        ui.actions(
                                ui.literalButton("Apply", this::apply)
                                        .enabled(this.validName)
                        )
                ).classes("probe-form")
        );
    }

    private void apply() {
        this.name.set(this.name.get().trim());
    }
}
```

::: warning A `String` normally means a translation key
The first text calls use `literalHeading`, `literalCopy`, `literalField`, `literalCheckbox`, and
`literalButton` intentionally. Calls such as `ui.text("Probe Inspector")` or
`ui.button("Apply", action)` select the `String` overload and treat the string as a Minecraft
translation key. Use the `literal*` family for user input, formatted numbers, and other raw text.
:::

### Expected result

Typing updates `name` immediately. Clearing the field removes the Apply action from the keyboard and
pointer flow by disabling it, while the validation line remains a retained View and enters or leaves
layout with `View.GONE`. Pressing unmodified main Enter or keypad Enter while the field is enabled
runs the same `apply()` action.

## Understand the Composition

`Column` and `Row` are Taffy-backed containers. `card(...)`, `section(...)`, and `actions(...)`
compose those containers and add semantic classes (`ui-card`, `ui-section`, and `ui-actions`). Every
container factory accepts either View varargs or a `List<? extends View>`.

`TextField.text(Signal<String>)` and `Checkbox.checked(Signal<Boolean>)` are the two provided two-way
bindings. Native edits update the signal; signal changes update the View while it is attached.
`Button`, `Checkbox`, and `TextField` also accept direct or reactive `enabled(...)`, and all six MCSX
components accept direct or reactive `visible(...)`.

## Lifecycle and Ownership

Reactive properties subscribe when their View attaches to a window and close the subscription when
it detaches. Reattachment reads the latest value, which is important when Dockspace reparents a
retained pane. Each property, style, keyed binding, and `ReactiveView` rolls back subscriptions
acquired inside its own failed mount. Do not treat attachment of an entire custom View hierarchy as
one transaction if a later layer can fail.

Install `TextField.text(Signal<String>)` and `Checkbox.checked(Signal<Boolean>)` before attachment.
Do not mix direct and signal-backed text on one field, or direct and signal-backed checked state on
one checkbox. All component and hierarchy mutation belongs on ModernUI's UI thread; MCSX control
handlers already run there.

## Add Style Metadata

Give the form semantic hooks now so the [Themes and Styles guide](./themes-and-styles.md) can style it
without rebuilding the View tree:

```java
Text status = ui.literalText("Connected")
        .tag("probe-status")
        .classes("status", "compact")
        .part("label")
        .state("warning", latency.map(value -> value > 200));
```

`tag(...)` is native View metadata. `classes(...)`, `part(...)`, and `state(...)` participate in MCSS
matching. Built-in pseudo-states are `:hover`, `:focus`, `:pressed`, `:disabled`, and checkbox
`:checked`. Custom state names begin with a letter or `_` and then use letters, digits, `_`, or `-`.

## Retain Dynamic Rows

Use keyed children when a changing collection must retain each row's View identity, editable state,
focus, and selection:

```java
record ProbeRow(String id, String label) {
}

Signal<List<ProbeRow>> probes = Signal.of(List.of());

Column rows = ui.boundColumn(
        probes,
        ProbeRow::id,
        probe -> ui.text(probe.map(ProbeRow::label))
);
```

Prefer `boundColumn(...)` when a retained item's fields can change; its factory receives the live
per-key `Value<T>`. `keyedColumn(...)` receives only the item that existed when its View was created.
`Column` and `Row` expose equivalent `children(...)` and `boundChildren(...)` methods.

::: details Component and text reference
The facade creates six final component types:

| Component | Purpose | Common MCSX properties |
| --- | --- | --- |
| `Column` | Vertical Taffy container | children, visibility, theme, stylesheet, raw stylesheet |
| `Row` | Horizontal Taffy container | children, visibility, theme, stylesheet, raw stylesheet |
| `Text` | Read-only text | text, visibility, color, text size |
| `Button` | Click action | text, visibility, enabled state, background token |
| `Checkbox` | Boolean input | text, checked state, visibility, enabled state |
| `TextField` | Single-line input | text, hint, submit, focus, visibility, enabled state |

Text-bearing APIs support four source forms where the component exposes them:

- Plain `String` overloads and explicit `translatable*` factories create Minecraft translatable
  components. The explicit forms also accept component format arguments.
- Explicit `literal*` factories display the supplied string without language lookup.
- `Component` overloads re-resolve when client resources or the selected language reload.
- `ui.text(Value)` binds computed text directly. Other reactive labels are configured after creation
  through component methods such as `.text(Value)` or `.hint(Value)`; factories such as `heading`
  and `button` do not accept a `Value` overload.

Minecraft `Component` text is flattened with `Component.getString()`. Translation and displayed text
remain, but click events, hover events, and rich spans are not transferred to ModernUI.

`TextField.onFocusChanged(...)` observes focus without replacing the native focus listener. A
focused checkbox toggles through the native click path when Space is released. Built-in buttons and
accepted field submissions already play Minecraft's normal UI click sound.
:::

::: details Keyed collection invariants
A keyed binding owns the complete child collection. The container must start without static children,
and application code cannot mutate its hierarchy afterward. Items and keys must be non-null; keys
must be unique according to `equals` and `hashCode`. Removing a key detaches its View and makes the
binding drop its references; it does not invoke a disposal callback. Use detach lifecycle or explicit
owner cleanup for custom resources. Adding that key again creates a new View.
:::

::: details Cursors, custom Views, and current limits
Use native Views directly when no MCSX component is needed. This complete hit target keeps its View
type explicit and adds MCSX cursor and sound behavior:

```java
Signal<Boolean> selected = Signal.of(false);
View probeTarget = Ui.tagged(new View(ui.context()), "probe-target");
Cursors.set(probeTarget, Cursors.crosshair());
probeTarget.setOnClickListener(view -> {
    selected.set(true);
    UiSounds.playClick();
});
```

`Cursors` supplies `pointer`, `hand`, `text`, `crosshair`, `move`, `forbidden`, horizontal and
vertical resize, and both diagonal resize icons. `set(...)` and `clear(...)` return the same concrete
View. Unsupported custom native shapes fall back to `pointer()`. Enabled clickable non-editor Views
otherwise resolve to the hand cursor automatically.

The initial component set is deliberately small and final. Compose custom ModernUI Views instead of
subclassing MCSX widgets. There is no XML frontend or rich Minecraft text interaction. The built-in
MCSX stylesheet gives `button(...)`, `secondaryButton(...)`, `quietButton(...)`, and
`dangerButton(...)` their primary, raised, transparent-until-hover, and destructive treatments.
Use quiet buttons for escape actions such as Back, Close, and Reset. The class names carry no Java
behavior: consumer MCSS may redefine their colors, font weight, elevation, and top highlight without
rebuilding the component. A container's `rawStylesheet(...)` installs only the supplied rules when a
subtree must keep MCSX behavior without the built-in visual baseline.

Bind `button.pressed(value)` for a latched action. While true, the button uses the same recessed fill
and zero elevation as its pointer-pressed state; `CONTROL_ON` remains reserved for checks, switches,
progress, and compact active markers.
:::

## Next Steps

- [Make the form reactive](./reactivity.md) and mirror live client data into it.
- [Style `probe-form` and `validation`](./themes-and-styles.md) with resource-backed tokens and MCSS.
- [Mount a compact probe HUD](./overlays.md) when the inspector screen is closed.
- [Place the form in an Inspector pane](../dockspace/index.md) beside Scene and Log.

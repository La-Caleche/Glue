# MCSX v2 Java UI Library

MCSX combines native ModernUI Views with Taffy-backed layout, typed reactive values, root-scoped
themes and reloadable `.mcss` stylesheets. Java Views are the authoritative UI model. MCSX currently
has no XML or other markup frontend. Retained editor workspaces are an optional
`glue-mcsx-dock` feature module.

The supported contracts are listed in [MCSX v2 Public API](api-v2.md). The retired v1 markup,
Tailwind and self-hosted runtime documentation has been removed from the public wiki. MCSX v2's
native overlay-hosted workspace is documented in [MCSX Dockspace](dockspace.md).
The current design assessment and prioritized evolution path are recorded in
[MCSX and Dockspace Architectural Review](architectural-review.md).
The end-to-end expedition planner and reactive HUD showcase is documented in
[MCSX Full Gameplay Demo](full-gameplay-demo.md).

## Setup

Add the client library to a Fabric mod:

```kotlin
dependencies {
    modImplementation("fr.lacaleche.glue:glue-mcsx:<version>")
    // Add only for Dockspace and the client.dock API.
    modImplementation("fr.lacaleche.glue:glue-mcsx-dock:<version>")
}
```

Declare each directly used module in `fabric.mod.json`. Both modules are client-only;
`glue-mcsx-dock` depends transitively on `glue-mcsx`, which supplies the `modernui-mc-lite` runtime.

## Five-Minute Screen

Screens extend `UiScreen`, which supplies the fragment's `Ui`, ModernUI screen policies and guarded
single-use `open(...)` methods. Create the screen, compose its native Views, and open one fresh
instance:

```java
public final class EditorScreen extends UiScreen {

    private final Signal<Integer> count = Signal.of(0);
    private final Signal<Theme> theme = Signal.of(Themes.dark());

    @Override
    protected View create(Ui ui) {
        Text counter = ui.text(this.count.map(value -> "Count: " + value));

        return ui.screen(
                this.theme,
                Stylesheets.resource(ResourceLocation.fromNamespaceAndPath("example", "editor")),
                ui.card(
                        ui.translatableHeading("example.editor.title"),
                        counter,
                        ui.actions(ui.translatableButton("example.editor.increment", () ->
                                this.count.update(value -> value + 1)))
                )
        ).classes("editor-screen");
    }
}
```

Open the instance directly, optionally supplying the Minecraft screen to restore on close:

```java
new EditorScreen().open(Minecraft.getInstance().screen);
```

Put translations in `assets/example/lang/*.json` and the stylesheet in
`assets/example/mcsx/styles/editor.mcss`. Replace `Themes.dark()` with
`Themes.resource(ResourceLocation.fromNamespaceAndPath("example", "editor"))` when the matching
`assets/example/mcsx/themes/editor.json` should reload with resource packs.

Each `UiScreen` instance can be opened only once. Override `pausesGame()`,
`drawsDefaultBackground()` or `canClose()` for non-default screen policy; normal Fragment lifecycle
methods such as `onDestroy()` remain available.

All component, signal, theme and stylesheet mutations belong on ModernUI's UI thread. Event handlers
installed on MCSX components already execute there.

## Mouse Cursors

Assign a semantic cursor to an existing ModernUI View without changing its input behavior:

```java
Cursors.set(button, Cursors.hand());
Cursors.set(canvas, Cursors.crosshair());
Cursors.clear(canvas);
```

Available cursors are pointer, hand, text, crosshair, move, forbidden, horizontal and vertical
resize, and both diagonal resize directions. MCSX creates platform cursors on Minecraft's client
thread and falls back to the default pointer when a shape is unavailable. Position-dependent custom
Views may instead override `onResolvePointerIcon(...)` and return a `Cursors` value. Assigned Views
are weakly referenced and do not need explicit cleanup.

Enabled clickable Views use the hand cursor automatically unless an explicit cursor or a more
specific View implementation overrides it. ModernUI click actions play Minecraft's built-in UI
button sound through the UI sound category, including pointer and keyboard activation. Disabled
controls and drag, move or resize gestures remain silent.

## Components

The initial component set is deliberately small:

- `Column` and `Row` are final ModernUI ViewGroups laid out by Taffy.
- `Text`, `Button`, `Checkbox` and `TextField` are final native widgets with typed MCSX properties.
- Every MCSX component provides fluent `tag(...)`, `classes(...)`, `part(...)` and `state(...)`
  configuration for declarative tree composition.
- Every component accepts direct or reactive `visible(...)` values. A false value uses native
  `View.GONE`: the View remains mounted and retains identity, but leaves Taffy layout flow.
- `TextField.text(...)` initializes editable content without leaving a fluent composition tree.
- `TextField.text(Signal<String>)` binds editable content in both directions for the attached
  lifetime of the View.
- `TextField.onSubmit(...)` handles unmodified Enter release, while `onFocusChanged(...)` observes
  native focus transitions without occupying ModernUI's listener slots.
- `Checkbox.checked(Signal<Boolean>)` binds native checked state in both directions for the attached
  lifetime of the View. Its native indicator and interaction ripple remain authoritative, and a
  focused checkbox toggles with Space.
- `Button.enabled(...)`, `Checkbox.enabled(...)` and `TextField.enabled(...)` accept direct or
  reactive booleans. Disabled controls also match the built-in `:disabled` stylesheet state.
- `Ui.screen(...)` centers fitting content and wraps overflow in a native vertical `ScrollView`.
- `Ui.card`, `section`, `actions` and button variants establish semantic structure and classes.
- Every container factory accepts varargs or a `List<? extends View>`, so children computed by a
  stream need no array conversion.
- `Ui.context()` returns the context the facade builds against, and `Ui.tagged(view, tag)` tags a
  View built outside MCSX and returns it &mdash; both keep custom Views inside one declaration.
- Actionable controls use the hand cursor and native click feedback. `TextField.onSubmit(...)` keeps
  the text cursor but plays the same click feedback after an accepted Enter submission.

Minecraft `Component` labels use the normal `assets/<namespace>/lang/*.json` system. Mounted labels
refresh in place when the selected language or resource packs reload.

Existing `String` overloads on `Ui` text-bearing factories remain translation-key based. New code
should use explicit pairs such as `literalHeading(...)` / `translatableHeading(...)`; matching pairs
exist for text, copy, sections, fields, checkboxes and all button variants. Translatable factories
also accept Minecraft component format arguments.

Combine two reactive inputs when a property depends on both current values:

```java
Value<Boolean> valid = Value.combine(
        name.map(value -> value.trim().length() >= 3),
        consent,
        (nameValid, accepted) -> nameValid && accepted
);
```

Like `map(...)`, a combined value emits only changed results and subscribes to each distinct source
once while it has listeners. Results are recomputed from every source at once, so a listener never
sees one operand updated ahead of another — the same value may be passed twice, and operands derived
from a shared source stay in step. `Values` covers what `map` and `combine` cannot say on their own:

```java
Value<Boolean> valid = Values.all(nameValid, emailValid, consent);   // any number of sources
Value<Boolean> invalid = Values.not(valid);
Value<Integer> fixed = Values.constant(0xffec9b3b);                  // never changes, never notifies
```

## Keyed Children

Use a dedicated `Column` or `Row` when list identity matters:

```java
Column rows = ui.keyedColumn(items, Item::id, item -> createRow(ui, item));
```

When a row shows a field that changes while its key stays, bind the row to the value its key owns
instead of re-deriving it from the whole collection:

```java
Column rows = ui.boundColumn(items, Item::id, item -> ui.row(
        ui.text(item.map(Item::label)),
        ui.text(item.map(Item::distance))
));
```

Each key owns one value that the reconciliation updates in place, so a row wakes only when its own
item changed &mdash; not when any item in the list did. `Row.boundChildren(...)` and
`Column.boundChildren(...)` are the same binding on an existing container.

Factories can live in small Java component classes when a row has its own composition contract:

```java
final class ItemRow {

    static Row create(Ui ui, Item item, IntConsumer remove) {
        return ui.row(
                ui.field(Component.empty()).text(item.label()),
                ui.translatableDangerButton("example.remove", () -> remove.accept(item.id()))
        ).classes("item-row");
    }
}
```

The list value, key extractor and factory are read immediately. Binding is installed once and owns
the complete child collection; static ViewGroup mutation is rejected afterward. Keys use
`equals`/`hashCode`, must be unique and non-null, and retained keys keep the same View without
rerunning the factory. A removed key drops its value with its View, so a key that comes back is a new
row bound to a new value.

## Themes

Themes are immutable typed token maps. Install one theme or `Value<Theme>` on a `Column`/`Row` before
attachment. A scope can be installed only once; mutate the installed `Signal<Theme>` to switch themes
at runtime. Nested scopes shield their subtree from outer scopes.

Java themes remain first-class through `Theme.builder()`, `Themes.dark()` and `Themes.light()`.
Data-driven themes live at `assets/<namespace>/mcsx/themes/<path>.json` and are obtained through the
stable `Themes.resource(id)` handle. Handles update on resource reload; missing and removed resources
resolve to `Themes.dark()`.

```java
root.theme(Themes.resource(ResourceLocation.fromNamespaceAndPath("example", "editor")));
```

```json
{
  "parent": "example:base",
  "values": {
    "surface": "#18202c",
    "surface-raised": "@surface",
    "accent": {"color": "#4d8cff", "alpha": 224},
    "control-height": 48,
    "corner-radius": "@control-height"
  }
}
```

The schema permits only optional `parent` and required `values`. Colors use `#rgb`, `#rrggbb`,
`#aarrggbb`, another color token, or a color/alpha object. Dimensions are non-negative JSON integers
or references to another dimension token. Unknown fields, unknown tokens, type mismatches, missing
parents and cycles reject the complete candidate generation. `CHECKBOX_INDICATOR` is derived from the
final `ACCENT`, `TEXT_PRIMARY` and `TEXT_MUTED` values and cannot be written directly.
`Themes.validate(id, reader)` applies the per-document checks — including reference cycles among the
document's own values — in an ordinary unit test, so a shipped theme JSON fails the build instead of
the reload; only `parent` resolution remains reload-time. The showcase Glue Studio is the living
resource-theme example.

Most `ThemeTokens` colors are packed ARGB integers. `CHECKBOX_INDICATOR` is a native
`ColorStateList` covering disabled, checked, indeterminate, pressed, focused, hovered and default
states. Dimension tokens use the integer units consumed by ModernUI and Taffy. Missing values
resolve to the token's fallback.

## Stylesheets

Resource stylesheets live at `assets/<namespace>/mcsx/styles/<path>.mcss`:

```java
root.stylesheet(Stylesheets.resource(
        ResourceLocation.fromNamespaceAndPath("example", "editor")
));
```

Like themes, a stylesheet scope is installed once before attachment. A resource handle updates on
F3+T without replacing the mounted View tree. Invalid candidates log their resource, one-based line
and column and retain the previous valid generation. Missing resources resolve to an empty stylesheet.

```css
.editor-card {
    width: 90%;
    max-width: 720px;
    padding: 24px;
    gap: 16px;
    background: @surface-raised;
    corner-radius: @corner-radius;
}

.actions > Button:hover {
    background: @accent;
}
```

Supported selectors are component types, classes, parts, states and one direct-child combinator.
Built-in states include `:hover`, `:focus`, `:pressed`, `:disabled` and checkbox `:checked`.
The combinator walks the styled tree, not the raw View tree: layout-only containers such as the
ScrollView `Ui.screen` inserts are transparent to it, so `.ui-screen > .ui-viewport` matches.
Supported paint properties are `color`, `background`, `corner-radius`, `control-height` and
`text-size`. Supported layout properties are `width`, `max-width`, `padding`, `gap`, `flex-grow`,
`align-items` and `justify-content`. Colors are `#rgb`, `#rrggbb` or `#aarrggbb`; a malformed color
fails the resource reload with the file, line and column.

## Verification

Base behavior is covered by JUnit in `glue-mcsx/src/test`; dock model, geometry, persistence and View
ownership tests live in `glue-mcsx-dock/src/test`. The showcase scenario
`glue-test:mcsx-demo` exercises native typing, buttons, reactive visibility and enabled state, keyed
identity, add/remove/reorder, checkbox keyboard input, combined form validity, theme switching,
scrolling, language replacement, resource reload, screenshot capture and screen closure.
`glue-test:mcsx-studio` spawns a Lumos light from a docked pane, restyles it through a validated
inspector form, and asserts the edit reached the world, then exercises split resizing, tab undocking,
floating-window movement, redocking, pane close/reopen, maximize/restore, retained View identity,
consumed/unhandled keyboard routing, viewport-local screen blur, Iris shader-selector composition,
idle-Escape pause behavior and explicit F12 closure.
`glue-test:mcsx-lifecycle` covers overlay ownership, cross-thread close, failed mounts and immediate
slot reuse.

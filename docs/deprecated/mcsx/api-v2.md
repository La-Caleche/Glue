# MCSX v2 Public API

This page defines the supported Java compatibility boundary. Types under an `internal` package,
methods explicitly named `internal...` and resource-loader implementation classes are not API even
when Java visibility permits implementation-level cross-package access.

## Components

`UiScreen` is the public screen base. Its final `onCreateView(...)` creates a scoped `Ui` and calls
the subclass's `create(Ui)` method, which must return a non-null root View. An instance is single-use
and opens through `open()` or `open(previousScreen)`. Protected policy hooks control game pause,
default background and close veto without restricting normal Fragment lifecycle overrides.

The final public component classes are `Column`, `Row`, `Text`, `Button`, `Checkbox` and `TextField`.
Constructors require an explicit ModernUI `Context`; `Ui.with(context)` is the preferred composition
facade.

- `Column.add(...)` and `Row.add(...)` add static children.
- `children(value, key, factory)` installs one exclusive keyed collection binding.
- `tag(...)` assigns native View metadata while preserving the concrete component in fluent trees.
- `visible(...)` accepts a direct or reactive boolean on every component. False maps to `View.GONE`,
  retaining the mounted View and its state while removing it from Taffy layout flow.
- `classes(...)`, `part(...)` and `state(...)` provide append-only stylesheet metadata.
- `theme(...)` and `stylesheet(...)` install one root or subtree scope before attachment.
- `Ui` text factories come in three families. `String` overloads treat the string as a Minecraft
  translation key and re-translate live on language switch and resource reload — a `String` argument
  always selects this overload over the `CharSequence` one, so raw display strings must not be
  passed to it. `translatable*` factories are the explicit spelling of the same live-translating
  behavior and additionally accept Minecraft component format arguments. `literal*` factories take
  the string as raw display text and never consult the language files. The same three families are
  available for text, headings, copy, sections, fields, checkboxes and button variants; `Component`
  overloads re-resolve any component live, and `Value` overloads bind reactive text directly.
- `Text.text(...)`, `Button.text(...)`, `Checkbox.text(...)` and `TextField.hint(...)` accept direct,
  reactive and Minecraft text values. `TextField.text(Signal<String>)` provides an attachment-scoped
  two-way editable binding; direct and bound text sources cannot be mixed on one field.
- `Button.enabled(...)`, `Checkbox.enabled(...)` and `TextField.enabled(...)` accept direct or
  reactive boolean values and drive the built-in `:disabled` stylesheet state.
- `TextField.onSubmit(...)` handles unmodified main or keypad Enter release. Its
  `onFocusChanged(...)` callback coexists with ModernUI's native focus listener.
- `Checkbox.checked(...)` accepts a direct boolean or an attachment-scoped two-way
  `Signal<Boolean>`. Direct and bound checked sources cannot be mixed. Checkbox styles expose
  `:checked` while preserving the native indicator and ripple. Setting the inherited indeterminate
  state while boolean-bound normalizes it to unchecked. A focused checkbox toggles on Space release.
- `Text.color(...)`, `Text.textSize(...)` and `Button.background(...)` create local cascade values
  above stylesheets.
- `Ui.screen(...)` owns a native vertical `ScrollView`; the other facade methods create semantic
  structure and apply stable `ui-*`, `secondary` and `danger` classes.

Theme, stylesheet and keyed scopes are one-shot. Their source is read immediately, must be non-null,
and cannot be replaced after attachment. Install a `Signal` first and mutate it for runtime changes.

ModernUI methods remain the platform escape hatch, but inherited setters do not become MCSX cascade
origins. Prefer an MCSX property method when one exists.

`Cursors` provides semantic `PointerIcon` values for pointer, hand, text, crosshair, move, forbidden
and resize shapes. `Cursors.set(view, icon)` assigns one to ordinary ModernUI and MCSX Views;
`Cursors.clear(view)` restores the View's native cursor resolution. Custom Views can return these
icons from `onResolvePointerIcon(...)` when the shape depends on pointer position. Unsupported native
cursor shapes degrade to the default pointer. Enabled clickable non-editor Views resolve to the hand
cursor by default. ModernUI `CLICK` feedback maps to Minecraft's built-in UI button sound; text-field
submit requests the same feedback explicitly because submission is not a native click action.
`UiSounds.playClick()` exposes that same feedback to custom MCSX Views.

`MuiApi.mountOverlay(fragment)` mounts one Fragment independently from `Minecraft.screen` and
returns its owning `OverlayHandle`. MCSX composites the mounted overlay after the HUD and before an
open screen on its own — applications must not register `MuiApi.renderOverlay(guiGraphics)` anywhere,
or the overlay is drawn twice per frame. Closing the handle is idempotent; a second active mount is
rejected, and composition is skipped while a ModernUI screen or loading overlay is active. The mount itself
receives no input from this layer — `Dockspace` is the API that adds full pointer and keyboard
routing on top of it; a raw `mountOverlay` Fragment stays non-interactive.

`UiOverlay.hud(fragmentFactory)` is the managed host for a HUD-style overlay: `mount()` builds a
fresh fragment from the factory and takes the single overlay slot (a no-op while already mounted,
and rejected while a workspace or another host holds the slot), `unmount()` releases it, and
`isMounted()` / `fragment()` expose the current state — `fragment()` keeps the most recent fragment
through an unmount for inspection. All calls belong on the client thread. Prefer it over raw
`mountOverlay` bookkeeping: a managed HUD is registered with MCSX's overlay host, so its view root
follows window resizes and GUI-scale changes exactly as a workspace does, while still taking no
pointer or keyboard ownership.

`UiOverlay.isOccupied()` reports whether the exclusive slot is currently taken — by a workspace mount,
by a managed HUD, or by a fragment mounted straight through `MuiApi.mountOverlay`. It is exactly the
condition a further mount is rejected on, so a consumer that can be asked to open twice (a keybind, a
command) checks it before mounting instead of catching the rejection.

`UiOverlay.mount(fragment, onUnmount)` is MCSX's supported interactive overlay host.
It returns an ownership-specific `UiOverlay.Mount`; only that token can close its fragment, query
whether work is still accepted, or install an Escape handler. Mounting is client-thread-only and
closing is idempotent from any thread. Escape handlers consume transient UI state; an idle Escape
returns to Minecraft rather than closing the overlay. Docking uses this contract without exposing
MCSX's internal input router to extension modules.

Minecraft component click/hover events and spans are flattened to plain ModernUI text. Translated
textual content is retained and refreshed on client-resource reload.

## Dockspace

The docking API is published by the optional `glue-mcsx-dock` module, which depends on the base
`glue-mcsx` module. Moving it to that artifact did not change its Java packages, resource locations,
or saved-layout paths.

`Dockspace`, `DockPane`, `DockContent` and the types in `client.dock.layout` form the public native
docking API. A dockspace is a single-use overlay-hosted `Fragment` — not a `Screen`, so the game keeps
running beneath it. An idle ungrabbed workspace owns the pointer and receives keyboard events first;
ModernUI-consumed keys stay in the workspace while unhandled streams continue through Minecraft's
real keyboard handler. `GameFocus` locks the cursor into the game and remains authoritative while a
screenless game overlay temporarily releases it. Vanilla screens opened from gameplay are laid out inside the game
viewport and keep the pointer only within the published `GameSurface` rectangle, so the workspace
stays interactive around them. Optional Axiom integration closes the workspace when its full editor
opens, dismisses a workspace mounted while that editor is already active, and maps its context menu
through the active game viewport. Floating `DockWindow` values are in-window overlays, not
operating-system windows. Pane ids are durable layout and persistence keys.

The immutable `DockLayout` model separates semantic layout from native Views. `DockLayouts` creates
tab groups, weighted splits, rows, columns and floating windows. Registered pane content is lazy,
retained across layout mutations and disposed once with the dockspace. `DockPane.builder(...)`
configures an optional translated icon, user-close policy and independently retained trailing-header
content. Tabs size to their content, and floating windows reuse leaf headers rather than adding a
second title bar. Builder defaults may come from Java or
`assets/<namespace>/mcsx/dock/<path>.json`; optional user persistence lives under
`config/glue-mcsx/dock/<namespace>/<path>.json`.

`DockLayouts.parse(String)` reads that persisted format and returns the `DockLayout`, throwing
`DockLayoutException` on malformed input. It is the supported way to validate a shipped layout
resource from a unit test instead of waiting for a runtime load.

Runtime controls are `layout()`, `openPanes()`, `isOpen()`, `togglePane(...)`, `focusPane(...)`,
`resetLayout()`, `saveLayout()`, `maximizePane(...)`, `restoreMaximizedPane()`, `maximizedPane()` and
the `switchWorkspace(...)` overloads. Maximize is transient presentation state and never changes or
persists the semantic layout. Builder callbacks observe completed layout and open-pane changes,
client-thread mount and unmount steps, and final closure. `open()` requires Minecraft's
client thread and succeeds only by acquiring the single overlay slot; `isOpen()` is true from that
moment — before ModernUI has created the View — until a close is requested; `close()` is idempotent,
safe from any thread, rejects later runtime mutations immediately, and only ever unmounts the
workspace that owns the slot. No key implicitly closes a dockspace: application code owns that policy
and must call `close()`. Final closure callbacks run before that slot is reusable; native View disposal
follows asynchronously on ModernUI's UI thread. Dock-specific public theme
tokens control panel colors, spacing, borders, header height and corner radius. See
[MCSX Dockspace](dockspace.md) for construction, persistence and lifecycle details.

## Reactive Values

`Value<T>`, `Signal<T>` and `Subscription` are ModernUI-thread-confined contracts.

- `Value.get()` returns the current value.
- `subscribe(listener)` observes later values and does not emit the current value initially.
- `Signal.set(...)` updates immediately, suppresses equal values and dispatches synchronously in FIFO
  order, including reentrant writes.
- `Value.map(...)` suppresses equal mapped results.
- `Value.combine(first, second, combiner)` exposes the current result of two sources, subscribes to
  each *distinct* source once while the combined value has listeners and suppresses equal combined
  results. Every published result is recomputed from all sources at once, so it is always a snapshot
  in which each source is current: the same value may occupy several operand positions, and operands
  derived from a shared upstream are never read one change apart. A ternary
  `Value.combine(first, second, third, combiner)` extends the same contract to three sources; boolean
  folds over any arity belong to `Values.all(...)` / `Values.any(...)`.
- `Values.constant(value)` never changes and never notifies, for a component that binds a value the
  caller will not update. `Values.not(value)`, `Values.all(...)` and `Values.any(...)` fold booleans
  over any number of sources; an empty `all` is constantly true and an empty `any` constantly false.
- `Signal.postSet(value)` sets a signal from any thread: applied immediately on the UI thread (or
  when no UI thread exists, as in unit tests), otherwise posted to the UI thread in FIFO order with
  other posted work.
- `ClientMirror<T>` bridges game-side state into the UI: Minecraft's client thread reads and writes
  `get()` / `set(...)`, the UI binds the `value()` view, and each changed value crosses threads
  exactly once. Equal values are suppressed before crossing, and published values are shared by
  reference, so publish immutable snapshots. `get()` and `set(...)` reject every other thread — the
  UI thread, the server thread, network and worker threads — so the owning copy is confined rather
  than conventional. Constructing a mirror is unrestricted; only its accesses are confined. Without a
  running client, as in unit tests, there is no client thread to compare against and only the UI
  thread is ruled out.
- `ReactiveView` is the base for custom canvas Views that redraw on reactive changes:
  `invalidateOn(values...)` — usually called from the constructor — scopes invalidate subscriptions
  to window attachment, resubscribing when the view is reparented and rolling back transactionally
  when one source fails to subscribe. Subclasses overriding the attach callbacks must call super.
- Listener failures are collected while remaining listeners continue, then rethrown.
- MCSX subscriptions are idempotent. Custom values must notify and close on the ModernUI thread and
  provide idempotent subscriptions.
- Null may exist in a generic reactive value, but component properties, scopes, keys and items may
  reject it at their boundary.

Mounting is transactional: if one source fails to subscribe or provide its mounted value, previously
acquired subscriptions are closed and a later mount can retry.

## Themes

`Token<T>` lookup uses object identity, not its name. Reuse the same token instance when building and
reading a `Theme`. Themes are immutable and scoped; there is no process-global active theme.

The names and types in `ThemeTokens` are stable. `CHECKBOX_INDICATOR` is a native `ColorStateList`
covering disabled, checked, indeterminate, pressed, focused, hovered and default states. Preset
colors and dimensions in `Themes.light()` and `Themes.dark()` may evolve. Theme propagation attempts
every active token dependency before rethrowing listener failures, so one failing consumer cannot
leave a mixed generation.

`Themes.resource(ResourceLocation)` returns a stable `Value<Theme>` for JSON resources at
`assets/<namespace>/mcsx/themes/<path>.json`. Missing and removed resources resolve to
`Themes.dark()`. Each JSON object has an optional `parent` resource location and required `values`;
unknown fields and tokens are errors. Color declarations accept `#rgb`, `#rrggbb`, `#aarrggbb`,
same-kind `@token` references, or `{"color": ..., "alpha": 0..255}`. Dimensions accept non-negative
JSON integers or dimension-token references. `DOCK_METRICS` accepts one strict object containing all
`DockMetrics` fields in kebab-case; its fields must be non-negative integers and the object does not
accept references. References resolve after inheritance and child overrides, and
`CHECKBOX_INDICATOR` is always derived rather than directly writable. Compound dock metrics are not
valid MCSS scalar values. A failed parse, inheritance or reference resolution preserves the active
generation; missing and removed resources fall back to `Themes.dark()`. The showcase Glue Studio
exercises this path as a living demo.

`Themes.validate(ResourceLocation, Reader)` parses one theme document and resolves it, throwing
`IllegalArgumentException` that names the resource and the reason. Use it to fail the build on a
malformed shipped theme; `parent` resolution remains a reload-time concern.

## Stylesheets

Supported entry points are `Stylesheet.empty()`, `StylesheetParser.parse(...)`,
`Stylesheets.resource(...)` and `StylesheetParseException` accessors. The parser AST, specificity,
`Stylesheet.internalRules()` and computed-style machinery are internal.

Resources install as complete generations. Parse failures retain the previous generation. Live
consumer failures are isolated so every resource handle advances together. Publication is queued to
ModernUI without making Minecraft's reload thread wait, because ModernUI may itself be waiting for the
render thread to consume a frame.

## Lifecycle

Bindings and semantic states subscribe on View attachment and release subscriptions on detachment.
Moving a View out of a stylesheet scope removes that scope's values and restores the next cascade
origin. Cleanup attempts every owned resource even when one custom subscription throws.

All component, signal, theme and stylesheet mutations occur on ModernUI's UI thread. Resource reading
and parsing may run on a preparation executor; live publication is then queued to the UI thread.

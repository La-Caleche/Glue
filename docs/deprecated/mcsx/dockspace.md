# MCSX Dockspace

`Dockspace` is an overlay-hosted native ModernUI workspace for editor-style tools. It is *not* a
`Screen`: it mounts beside the running game rather than in front of it, so the game keeps ticking,
rendering and running its world unpaused underneath it. It supports tab
groups, horizontal and vertical splits, draggable splitters, tab undocking, movable and resizable
floating windows, redocking, pane visibility controls and optional per-workspace persistence.
Floating windows remain inside the Minecraft window; the API does not create operating-system
windows.

## Dependency

Dockspace is supplied by the optional `glue-mcsx-dock` client module:

```kotlin
dependencies {
    modImplementation("fr.lacaleche.glue:glue-mcsx-dock:<version>")
}
```

Declare `glue-mcsx-dock` in `fabric.mod.json`. It pulls `glue-mcsx` transitively. Existing Java
packages, `assets/<namespace>/mcsx/dock/` resources, and `config/glue-mcsx/dock/` saved layouts are
unchanged.

## Five-Minute Workspace

Each immutable `DockPane` has a durable lowercase id, a Minecraft `Component` title and lazy
`DockContent`. `DockPane.literal(...)` and `DockPane.translatable(...)` provide explicit title
semantics and pass a scoped `Ui` directly to ordinary MCSX content. Their `builderLiteral(...)` and
`builderTranslatable(...)` variants retain optional configuration. The builder accepts an optional `Component` icon, a close policy (closable
by default), and optional trailing header content. Pane content and trailing header Views are created
independently at most once for a mounted dockspace and retain identity while the pane moves, hides
and reopens. Each created View receives one matching `DockContent.dispose(view)` call when the
dockspace is destroyed.

```java
Dockspace dockspace = Dockspace.builder(ResourceLocation.fromNamespaceAndPath("example", "editor"))
        .pane(DockPane.translatable("explorer", "example.editor.explorer",
                ui -> ui.column(ui.translatableHeading("example.editor.explorer"))))
        .pane(DockPane.builderTranslatable("document", "example.editor.document",
                ui -> ui.translatableField("example.editor.document_hint"))
                .closable(false)
                .trailingHeaderUi(ui -> ui.row(ui.literalText("READY")))
                .build())
        .defaultLayout(DockLayouts.layout(DockLayouts.row(
                DockLayouts.tabs("explorer"),
                DockLayouts.tabs("document")
        )))
        .theme(Themes.resource(
                ResourceLocation.fromNamespaceAndPath("example", "editor")
        ))
        .stylesheet(Stylesheets.resource(
                ResourceLocation.fromNamespaceAndPath("example", "editor")
        ))
        .build();

dockspace.open();
```

This uses optional reloadable resources at `assets/example/mcsx/themes/editor.json` and
`assets/example/mcsx/styles/editor.mcss`. The Java default shown above is used when no saved user
layout exists; use `.defaultLayout(ResourceLocation)` instead when the default should come from
`assets/example/mcsx/dock/editor.json`. Create a new `Dockspace` instance each time the workspace
opens.

Use `DockContent.ui(ui -> ...)` when Ui-aware content is needed outside the pane factories. The
existing `DockContent.create(Context)` contract remains the low-level path for custom native Views
and implementations that override `dispose(View)`; Ui-adapted content has no disposal action by
default. Titles remain `Component` values and therefore retain normal language-reload behavior.

Pane content may use ordinary MCSX/Taffy composition. Dock geometry itself is exact native
`ViewGroup` layout, so split and floating-window dimensions do not depend on stylesheet layout.
Tabs size to their content and render their optional icon and policy-driven close control. The active
tab combines its background with accent iconography and an underline. The active pane's trailing
header occupies the remaining header strip. Splitters retain their complete configured hit area while
drawing a quiet centered hairline that accents on hover and resolves an axis-specific resize cursor.
Floating-window borders similarly expose edge and corner resize cursors, while unused header space
uses the move cursor. Dragging unused header space moves
an owning floating window; tab and action gestures retain priority. Floating windows use the nested
dock tree's leaf headers directly rather than adding duplicate window chrome.

## Game Viewport

A pane can host the running game itself, so the workspace becomes editor chrome pinned around a live
scene. Three pieces combine.

**The pane leaves a hole.** `.transparent(true)` makes the hosting leaf skip its content area and
panel interior, keeping only the border and header. The content layer also clears that rectangle from
the composited MCSX overlay, so a floating transparent pane cuts through docked panes underneath it.
The host stage paints `DOCK_BACKGROUND` itself, so that token must also be transparent. A game
viewport therefore keeps showing the world while undocked, moved and resized:

```java
DockPane.builder("viewport", Component.translatable("example.editor.viewport"), ViewportView::new)
        .closable(false)
        .transparent(true)
        .build();
```

```json
{"values": {"dock-background": {"color": "#0a0b0f", "alpha": 0}}}
```

**The world renders into the hole.** `GameViewport` in `glue-render` takes a window rectangle and
confines the world pass to it. The pane reports its own rectangle — ModernUI lays views out in
framebuffer pixels, which is the space `GameViewport` expects:

```java
int[] location = new int[2];
view.getLocationInWindow(location);
GameViewport.set(new GameViewport.Bounds(location[0], location[1], view.getWidth(), view.getHeight()));
GameSurface.set(new GameSurface.Bounds(location[0], location[1], view.getWidth(), view.getHeight()));
```

`GameSurface` (in `glue-mcsx`) is the input half of the same rectangle: while a vanilla screen is
open, pointer events inside it stay with the game and the workspace keeps everything outside, which
is what keeps the dock interactive around an open chat or inventory. Clear both together.

While bounds are set, the world, its post chains and everything hung off the world pass render into an
offscreen target of exactly that size, with the window reporting those dimensions so the projection
aspect, the screen-size uniform and any consumer sizing buffers from the window agree. The result is
composited back as the bottom GUI element.

The HUD is then *laid out* against the viewport rather than scaled onto it: the window reports the
viewport's GUI-scaled size for the duration of the HUD pass, so the hotbar centres on the viewport and
chat wraps to it, and the pose carries that layout to where the world was drawn. The GUI scale itself
is untouched, so HUD text stays pixel-aligned instead of being resampled. Vanilla screens get the
same treatment end to end — they initialize and resize against the viewport's dimensions, render
inside its rectangle, and read the pointer in its space — so chat, the inventory and menus open
inside the viewport pane instead of across the workspace. Chrome drawn later in the
GUI pass still covers the whole frame at full resolution.

Clear the bounds when the pane stops showing — on detach, and also when it is hidden behind another
tab or by another pane being maximized — or the world will go on rendering into a rectangle that
something else now covers. Closing the workspace is the one path where detach-time cleanup is too
late: View teardown follows asynchronously on the UI thread, so wire the same clear into
`.onUnmount(...)`, which runs inside the close operation on the client thread — the claim then ends
on the same frame as the workspace, with the detach cleanup remaining the idempotent fallback.

**Input follows game focus.** Grabbed, the game owns pointer and keyboard exactly as it does without
the workspace — mouselook, bindings, the chat and inventory keys, all read by vanilla, nothing
emulated. An idle ungrabbed workspace owns the pointer and gets first refusal on keyboard events.
ModernUI-consumed keys stay in the workspace; terminally unhandled press/repeat/release streams are
replayed through Minecraft's real `KeyboardHandler`, so movement and rebound mod keybinds continue to
work without grabbing the cursor. Printable keys remain workspace-owned while an `EditText` has
focus, and a release always follows any press Minecraft received, preventing stuck movement when UI
ownership changes mid-stream. Fullscreen, screenshot and supported external editor toggles retain
their host-level handling. `GameFocus.request()` (clicking a game viewport) locks the cursor into the game;
`GameFocus.release()` — or Escape — hands it back. Logical game focus remains with the game when a
screenless game overlay temporarily releases the cursor, so that overlay receives its complete input
stream instead of the workspace stealing the release event. The game itself ticks, renders and runs
unpaused throughout.

**Axiom compatibility is automatic.** Axiom's rebindable editor toggle passes through an idle MCSX
workspace, and opening the full editor closes the active workspace before Axiom takes over the
window. A workspace mounted while the editor already owns the window is dismissed on the next frame.
Its screenless context menu is different: it stays over the running game, keeps the current
`GameFocus`, and, while `GameViewport` is active, uses the same viewport-local dimensions, render
transform and pointer coordinates as the HUD. Axiom remains an optional dependency; no setup or
compile-time Axiom API is required.

**Vanilla screens live inside the viewport.** A screen opened out of gameplay — chat, the inventory,
a menu — is laid out against the game viewport, rendered inside it, and owns the keyboard plus the
pointer within the published `GameSurface` rectangle only: the workspace around it stays fully
interactive while the screen is open. Closing the screen returns to mouselook, because that is where
it was opened from, and Escape then returns to the workspace. A workspace that publishes no surface
keeps the simpler behavior: an open screen owns the pointer outright and the workspace sits visible
but inert underneath it, composited after the HUD and below the screen. Screens that deliberately
hide Minecraft's HUD, including Iris's shader-pack selector, do not hide the game viewport or the
workspace with it.

Escape is layered the way an editor expects: while the game is focused it releases the game; with a
screen open vanilla closes the screen; in the workspace a focused text field gives up focus first, an
in-flight drag is cancelled, and a maximized pane is restored. Otherwise it reaches Minecraft and
opens the pause screen. Escape never closes a dockspace.

Transparency is presentation only. The pane still hosts an ordinary View, still docks, floats and
maximizes. Screen background blur is copied into an exact viewport-sized target before vanilla's
effect runs, then copied back into the viewport. The game behind the screen is blurred normally while
the surrounding workspace stays sharp, and blur samples cannot bleed across the viewport edge.

## Drag Targets

Docking destinations are **guide controls**: small painted squares whose rectangles are also their
exact hit regions, so releasing over a visible control always performs the operation the control
shows, and a control is only ever shown for an operation the dragged payload supports. The hovered
leaf presents a center control that joins its tab group and four edge controls that split it —
controls that do not fit inside a small leaf are simply not offered — and the stage presents four
edge-inset controls that split the whole tree. During the drag, a pane-identity ghost, the guide
controls, an exact placement preview and — over a tab strip — an insertion caret make every target
visible before release.

A dragged **tab** additionally docks through any leaf's tab strip, inserting at the pointer's
position between the existing tabs; dragging along its own strip reorders the tab the same way. A sole
tab is not offered an edge split against the leaf its removal would erase. Over an empty stage the
whole stage docks the tab as the new tree. A release over none of these targets undocks the pane into
a new floating window at the pointer.

A dragged **floating window** uses the same guide controls, so a whole window — including one whose
root is a split of several groups — docks intact through the leaf edge and stage controls. A window
whose root is a single tab group can also merge into a strip, through the hovered leaf's center
control or its tab strip at the pointer's insertion position; a split-rooted window is never
offered a merge target, because a split has no single strip to merge. Everywhere else the release
just repositions the window, so it can be moved across the workspace without being absorbed by
whatever sits beneath it.

The active leaf header also provides a compact maximize control. Maximizing remounts the pane's exact
retained content and trailing header into a host-stage overlay with integrated title, restore and
policy-driven close controls; the docked tree and floating windows underneath stop drawing and stop
receiving gestures for the duration. Header and footer chrome remain visible. A transparent pane's
overlay paints no background, so its hole stays open while maximized.

## Dock Themes

Dock presentation is fully themed: no colour, size or spacing is baked into the dock views.

| Colour | Metrics |
| --- | --- |
| `DOCK_BACKGROUND` | `DOCK_METRICS` (`DockMetrics`) |
| `DOCK_PANE_BACKGROUND` | `gutter`, `splitterSize`, `headerHeight` |
| `DOCK_HEADER_BACKGROUND` | `cornerRadius`, `borderWidth` |
| `DOCK_BORDER` | `tabTextSize`, `controlTextSize` |
| `DOCK_ACTIVE_TAB_BACKGROUND` | `tabPadding`, `iconGap` |
| `DOCK_DRAG_GHOST_BACKGROUND` | `tabCloseWidth`, `controlWidth` |
| `DOCK_DROP_HIGHLIGHT` | |

`tabTextSize` also sizes the maximized pane's identity, and `controlWidth` is a *minimum*:
a longer header action label such as `RESTORE` keeps its own width. Every metric is non-negative.
`DOCK_DRAG_GHOST_BACKGROUND` and
`DOCK_DROP_HIGHLIGHT` carry their own alpha, so a custom theme that changes
`DOCK_ACTIVE_TAB_BACKGROUND` should set them too — they do not derive from it.

`Themes.dark()` and `Themes.light()` define the palette and `DOCK_METRICS` explicitly, and
`ThemeTokens.withAlpha` helps compose the translucent colors. Reactive changes to any dock token re-style the retained tabs and
request a new native layout, preserving pane Views and the semantic dock layout.

A dockspace can consume a resource theme directly with
`.theme(Themes.resource(ResourceLocation.fromNamespaceAndPath("example", "editor")))`. Theme JSON
lives at `assets/<namespace>/mcsx/themes/<path>.json`; it supports optional inheritance, same-kind
token references and explicit color alpha. Reloaded valid generations update the retained dockspace,
invalid generations keep the previous theme, and missing resources fall back to `Themes.dark()`.
Glue Studio in `glue-showcase` is the living data-driven dock-theme demo. Its custom radar pane reads
the same `Value<Theme>` the workspace installs, so a self-drawn native pane reloads with the rest of
the workspace instead of hardcoding colours.

## Layouts

The immutable layout model consists of `DockLayout`, `DockTabs`, `DockSplit` and `DockWindow`.
`DockLayouts` provides factories for empty layouts, tab groups, rows, columns, explicit weighted
splits and floating windows. Pane ids may occur only once across a valid layout, and a tab group's
active pane must belong to that group. Loading and edge docking flatten nested splits on the same
axis while preserving their proportions, so each splitter resizes only its visually adjacent panes.

A resource default can replace the Java default:

```java
.defaultLayout(ResourceLocation.fromNamespaceAndPath("example", "editor"))
```

It is read from `assets/example/mcsx/dock/editor.json` through the resource manager, so resource
packs may override it. Resolution is user layout, Java default, resource default, one tab group
containing every registered pane, then an empty layout. Unknown and duplicate panes are removed when
a persisted or resource layout is loaded; a candidate left holding no registered pane at all — every
id in it was renamed or removed — is discarded so resolution continues instead of opening a blank
workspace.

`DockLayouts.parse(json)` is the public entry point for the persisted document format — the same
read path saved layouts and resource defaults use, including its normalization. A document that
cannot be safely interpreted throws `DockLayoutException`, so a shipped `mcsx/dock` resource can be
parse-validated in an ordinary unit test instead of failing at runtime; panes unknown to a
particular workspace are removed only when that workspace loads the layout.

Before the dockspace mounts, `layout()` reports the sanitized Java default, or an empty layout when
the default is a resource. The configured default is resolved in full when the screen opens.

## Persistence

Persistence is enabled by default. User layouts are stored as UTF-8 JSON under
`config/glue-mcsx/dock/<namespace>/<path>.json`. Each save is written beside the target and swapped
in the way vanilla replaces `servers.dat` and `level.dat`, leaving the previous document as
`<path>.json_old`; a save interrupted mid-swap therefore falls back to that backup on the next load.
Disable persistence for temporary tools or tests with `.persistence(false)`.

A completed gesture encodes its layout on the UI thread and writes it off that thread, so a slow
config directory cannot stall the workspace. Writes to one workspace file stay ordered, and loading,
resetting or switching a workspace observes every write already issued for it.

`saveLayout()` writes the current workspace, and `resetLayout()` removes its saved state — document
and backup — then reloads the configured default. `switchWorkspace(...)` saves the current layout before loading another
workspace id and optional default.

## Runtime Control

- `layout()` returns the latest immutable semantic layout.
- `openPanes()` returns an immutable set of panes currently docked or floating.
- `togglePane(id)` closes an open pane or reopens it in a floating window.
- `focusPane(id)` activates and focuses an open pane.
- `maximizePane(id)` presents an open pane over the host stage; closed panes are ignored.
- `restoreMaximizedPane()` restores the maximized pane to its unchanged dock position.
- `maximizedPane()` returns the transiently maximized pane id, if any.
- `onLayoutChanged(...)` observes completed structural and pointer mutations.
- `onOpenPanesChanged(...)` observes changes to pane visibility.
- `open()` mounts the workspace; `close()` dismisses it and saves the layout.
- `isOpen()` reports whether this workspace owns the overlay slot right now.
- `onMount(...)` initializes client-thread session state after the overlay slot is acquired and before
  concurrent closure can proceed. A close requested by the callback runs after initialization returns.
- `onClose(...)` runs once during explicit client-thread closure, before another workspace can mount.
  Native View destruction follows later on the UI thread.
- `onUnmount(...)` runs on the client thread inside the close operation, right after the overlay
  stops mounting — the place to synchronously drop per-frame world claims such as a game viewport.
- `header(...)` and `footer(...)` configure optional chrome.

Pane and layout mutation methods marshal their work to ModernUI's UI thread. The lifecycle pair has
its own thread contract: `open()` must run on Minecraft's client thread and reports the workspace as
opened only after the overlay host has been acquired — a rejected open (another workspace is already
mounted) throws without changing the instance, which stays openable later. `close()` is safe from
any thread and idempotent; the complete teardown runs as one client-thread operation, and only the
mount this instance owns is ever closed, so a stale or failed workspace can never unmount another. A
pane whose content factory fails during mounting does not crash the client: the partial mount is
rolled back — every created View disposed exactly once, the overlay slot released — and the failure
is logged. Runtime mutations are rejected as soon as close begins, and work already queued for that
mount is discarded rather than running against destroyed Views. A `Dockspace` instance is single-use:
create a new instance after it closes. No input event closes it implicitly; application code decides
when to invoke `close()`.

`isOpen()` answers that lifecycle question without inspecting Views: it is true from the moment
`open()` acquires the slot — well before ModernUI has created the workspace View — and false again as
soon as code requests a close. A toggle binding retains the instance it opened and closes only that
owned instance; because the slot may also be held by an unrelated overlay, it still tests
`UiOverlay.isOccupied()` before creating a replacement:

```java
if (studio != null && studio.dockspace().isOpen()) {
    studio.dockspace().close();
    return;
}
if (UiOverlay.isOccupied()) return;

studio = new GlueStudio(true);
studio.open();
```

`DockTags` names the stable View tags on dock chrome — `HOST`, `SPLITTER`, `WINDOW`, `MOVE_AREA` and
`MAXIMIZE` — so tools and tests can locate dock parts with `findViewWithTag` instead of the internal
view types. A pane's tab carries the pane id itself as its tag.

Maximize is presentation state only. It is never represented by a synthetic `DockLayout`, serialized,
or reported as a layout change. Focusing another pane, closing or toggling the maximized pane, resetting
or replacing the layout, and switching workspaces restore it safely first. Maximizing another open pane
switches the overlay directly; an inactive tab is activated through the ordinary semantic layout mutation.

# MCSX and Dockspace Architectural Review

## Status and Scope

This document reviews the general MCSX and dockspace architecture after the following commits:

- `7313cca` - confine the game, HUD and screens to an embeddable viewport
- `f93f707` - make the overlay dock workspace usable with real input
- `8e5880c` - replace the dockspace showcase with Glue Studio
- `3db2c4e` - remove superseded MCSX design documents

The review covers the code architecture, package and file organization, Java-first UI declarations,
dockspace declarations, the embedded game viewport, and the feature set relative to CraftUI and Dear
ImGui docking.

**Implementation status.** Phases 1–3 of the evolution plan have been implemented: overlay mount
ownership is an exclusive `UiOverlay.Mount` token backed by the internal host, with any-thread close, immediate closing-state
publication and client-thread teardown; drop geometry is a single shared guide implementation
(`DockGuides`); the viewport pointer and drag deltas convert directly from raw cursor coordinates
(`ViewportMouse`); and tab drops insert at the pointer's strip position. Each concrete finding below
carries a **Resolved** or **Deferred** note.
Phase 4 — the public definition/session split — remains deferred until the internal ownership
semantics have soaked.

Docking is now published as the optional `glue-mcsx-dock` module. Its public Java packages and
durable resource/configuration paths remain unchanged. The base `glue-mcsx` module owns the generic
overlay and input substrate through the supported `UiOverlay` mount contract; the dock module owns
the workspace model, views, interactions, persistence, tests, and logging.

The overall position is:

> The architecture is fundamentally sound and should be evolved rather than rewritten. Its immutable
> layout model, retained pane identity, Java UI declarations, resource styling and public/internal
> package split are strong foundations. Mount ownership is now scoped to an explicit token whose
> lifecycle crosses Minecraft's client thread, ModernUI's UI thread and the render thread.

## Executive Assessment

MCSX deliberately does not reproduce Dear ImGui's immediate-mode implementation. It provides a
retained native View tree, immutable semantic docking layouts, typed reactive values, resource-backed
themes and stylesheets, and explicit persistence. That is the correct direction for a Java library
whose consumers build long-lived Minecraft tools rather than frame-local debug windows.

The docking model and declaration API are among the strongest parts of the implementation. The
original review found most defects at the integration boundary around that model:

- `Dockspace` currently combines immutable configuration, mutable runtime state, Fragment lifecycle,
  persistence coordination and public control methods.
- `OverlayHost` owns one process-global mount through an ownership-specific token.
- Client-thread, ModernUI-thread and render-thread transitions are not represented consistently in
  the API.
- `GameViewport` and `GameSurface` publish parallel global rectangles that callers must keep in sync.
- Visual drop controls and semantic hit regions now share `DockGuides` geometry.

These are repairable boundary problems, not evidence that the immutable layout model or retained View
architecture is wrong.

## Architectural Strengths

### Immutable Semantic Layout

`DockLayout`, `DockTabs`, `DockSplit` and `DockWindow` form an explicit immutable layout tree. The
Views render that tree but do not define it. This separation provides several benefits:

- Layout mutation can be tested without constructing ModernUI Views.
- Persistence serializes semantic state rather than incidental widget state.
- A pane can move between a tab group, split and floating window without replacing its content.
- Invalid, duplicate and unknown pane references can be sanitized at the model boundary.
- Resource defaults and Java defaults resolve into the same runtime representation.
- Maximize state can remain presentation-only instead of corrupting the persisted dock hierarchy.

Keeping mutation in `DockOperations` is also a good choice. It is preferable to spreading model
rewrites across gesture handlers or introducing a stateful layout controller.

### Retained Pane Identity

Pane content and trailing header Views are created lazily, cached for a mounted workspace and disposed
explicitly. Moving, hiding, reopening, floating and maximizing a pane retain the same native View.
This is a meaningful advantage over rebuilding UI content after every structural mutation.

The contract is particularly useful for:

- native text editing and selection
- scroll position
- custom canvas Views
- reactive subscriptions tied to attachment
- expensive editor state
- game-facing objects represented by a pane

The retained identity contract should remain central to the design.

### Explicit Public and Internal Boundaries

The current package structure communicates the intended API reasonably well:

```text
client/dock/
    Dockspace
    DockPane
    DockContent
    layout/
        DockLayout
        DockNode
        DockSplit
        DockTabs
        DockWindow
    internal/
        DockContentCache
        layout/
        persistence/
        view/
```

Public declarations and supported layout values are visible at the top. Geometry, mutations,
persistence details, content caching and native dock Views remain internal. This is a healthy library
boundary and should be preserved.

### Java-First UI Composition

MCSX keeps Java Views as the authoritative UI model:

```java
return ui.column(
        ui.translatableHeading("example.editor.inspector"),
        ui.translatableText("example.editor.description"),
        ui.row(
                ui.translatableButton("example.editor.apply", this::apply),
                ui.translatableSecondaryButton("example.editor.reset", this::reset)
        )
).classes("inspector");
```

This gives consumers normal Java control flow, type checking, debugging, refactoring and direct
access to native Views. It avoids generated bindings and a second expression language while still
allowing presentation to live in reloadable `.mcss` and theme resources.

The absence of an XML or JSON markup frontend is not a deficiency. Custom native Views, reactive
values and Minecraft APIs are first-class requirements; keeping those interactions in Java is
simpler and more honest than hiding them behind markup.

### Resource and Java Layouts

Supporting both declaration forms is appropriate:

```java
.defaultLayout(DockLayouts.layout(DockLayouts.row(
        DockLayouts.tabs("explorer"),
        DockLayouts.tabs("viewport")
)))
```

and:

```java
.defaultLayout(ResourceLocation.fromNamespaceAndPath("example", "editor"))
```

Java layouts are discoverable, reviewable and directly refactorable. Resource layouts allow resource
packs or non-code changes to replace defaults. Both forms resolve to the same semantic model, so one
does not create a parallel docking implementation.

### Thin Host Integration

The rendering and input integration is implemented through focused mixins while most behavior remains
in regular classes such as `OverlayHost`, `OverlayInput` and `GameViewportStage`. That follows Glue's
general mixin discipline and is preferable to embedding editor behavior inside injection methods.

## Principal Architectural Weakness

### Definition, Fragment and Runtime Session Are One Object

`Dockspace` currently represents all of the following:

- a workspace definition produced by a builder
- a single-use mutable runtime instance
- a ModernUI `Fragment`
- a persistence coordinator
- a public runtime control surface
- the caller-facing open and close lifecycle

The actual mounted overlay is owned separately by static state in `OverlayHost`, but an
ownership-specific `UiOverlay.Mount` now prevents a stale public object from unmounting a different
workspace. Separating the definition from the runtime session remains the deferred structural issue.

The recommended end state is a distinction between an immutable definition and an owned mounted
session:

```java
Dockspace workspace = Dockspace.builder(id)
        .pane(...)
        .defaultLayout(...)
        .build();

DockspaceSession session = workspace.open();
session.focusPane("inspector");
session.saveLayout();
session.close();
```

The exact names are less important than the ownership rules:

- The definition is reusable and has no Fragment lifecycle.
- A successful `open()` returns the only object allowed to close that mount.
- The session owns its internal Fragment and overlay handle.
- `open()` reports success only after host ownership has been acquired.
- Failed Fragment creation releases the acquired host mount.
- `close()` is safe from any thread and performs teardown on the client thread.
- Runtime operations belong to the mounted session rather than a not-yet-mounted definition.
- Stale or failed sessions cannot unmount a different workspace.

The implemented compatible correction makes `OverlayHost.mount()` return an ownership-specific handle
and requires that handle for unmounting. This establishes the essential ownership invariant without
the deferred public definition/session split.

### Thread Domains Are Implicit

The implementation crosses three important domains:

- Minecraft client and GLFW operations
- ModernUI View and Fragment operations
- render-thread viewport and compositing operations

Thread transitions are explicit at the lifecycle boundaries: pane mutations post to the UI thread,
while `GameFocus` and `Dockspace.close()` marshal client-owned work onto the client thread.

The public contract should consistently follow one of two models for each operation:

- Require a named thread and fail before mutating any state.
- Accept calls from any thread and marshal the complete atomic operation.

For public workspace lifecycle methods, accepting any thread is the more useful contract because UI
buttons naturally run on ModernUI's thread while keybinds and Escape handling run on Minecraft's
client thread.

### Parallel Global Viewport State

`GameViewport` describes where the game renders, while `GameSurface` describes where pointer input
belongs to a vanilla screen. A viewport pane must publish and clear both rectangles together.

The module split explains why the two classes exist: `glue-mcsx` should not force every MCSX consumer
to depend on `glue-render`. The weakness is that their synchronization is an undocumented runtime
invariant enforced only by caller discipline.

Possible improvements include:

- an ownership handle that sets both bounds and clears both on close
- a showcase-local binding until a second library consumer proves that a public adapter is needed

The project should not create a new module or abstraction speculatively, but it should avoid adding
more parallel global state to this protocol. The chosen shape is the showcase-local binding: the
host clears its own `GameSurface` during teardown, and the Studio's `onUnmount` hook clears
`GameViewport` in the same client-thread operation. A public adapter still waits for a second
consumer.

## Concrete Findings

### P1: UI-Thread Close Can Orphan the Overlay

`Dockspace.close()` directly calls `OverlayHost.unmount()`. The host clears its handle, close callback,
escape handler, focus state and input state before calling `OverlayHandle.close()`. The underlying
ModernUI handle requires Minecraft's client thread.

A close button or another ModernUI callback can therefore produce this sequence:

1. The UI thread calls `Dockspace.close()`.
2. `OverlayHost` forgets the active registration.
3. `OverlayHandle.close()` rejects the wrong thread.
4. The actual ModernUI overlay remains mounted.
5. Glue no longer owns the handle required to close it.

Teardown should be marshalled as one client-thread operation, and host ownership should be cleared
only after the owning handle has closed successfully.

> **Resolved.** `Dockspace.close()` closes the `OverlayHost.Mount` this instance owns; the mount
> marshals the whole teardown to the client thread, closes the underlying handle first, and clears
> host state only after that close succeeded. Closing state is visible immediately on any calling
> thread, queued runtime work verifies the same live mount before executing, and `onClose` completes
> before the slot can be reused. Mount initialization and closure are one synchronized transition; a
> reentrant close waits for `onMount` to return. Covered by the `glue-test:mcsx-lifecycle` game test,
> including an open-close pair that completes before Fragment creation.

### P1: Fragment Creation Failure Occupies the Overlay Slot

`Dockspace.onCreateView()` catches layout, pane, header and footer construction failures and invokes
`rollbackFailedMount()`. That rollback marks the dockspace disposed and releases created content, but
it does not release the `OverlayHandle` already acquired by `OverlayHost.mount()`.

A throwing content factory, a null content View or a resource/layout failure can consequently leave
the ModernUI overlay registration active while the public dockspace is permanently disposed. Input
may remain captured and every future MCSX workspace mount is rejected.

Failed Fragment construction must close the owning host session on the client thread after local View
cleanup.

> **Resolved.** `rollbackFailedMount` now also releases the owning mount on the client thread, and a
> `RuntimeException` from a content factory is contained — logged, rolled back, created views
> disposed exactly once — instead of escaping onto the ModernUI thread and crashing the client. The
> lifecycle game test mounts a deliberately failing workspace and then mounts a healthy one.

### P2: A Rejected Second Open Can Close the First Workspace

`Dockspace.open()` sets its `opened` flag before the global host accepts the mount. If workspace A is
mounted and workspace B attempts to open, B receives the expected exception but remains marked open.
Calling `B.close()` as failure cleanup then invokes the unqualified global unmount and closes A.

The instance must not report itself open before successful host acquisition, and unmount must require
the ownership handle returned by that acquisition.

> **Resolved.** `open()` stores the acquired mount before marking the instance opened; a rejected
> open throws without changing the instance, its `close()` is a no-op, and it can open again once the
> slot frees.

### P2: Render-Time Viewport Mouse Coordinates Are Compressed

During HUD and screen rendering, `GameViewportStage` temporarily changes the window's framebuffer and
GUI-scaled dimensions to the viewport dimensions. Minecraft's `MouseHandler#getScaledXPos` and
`getScaledYPos` calculate from those reduced GUI dimensions but retain the physical screen dimensions
as the denominator. `MouseHandlerViewportMixin` then subtracts the viewport origin from that already
compressed value.

Coordinates calculated before entering the GUI scope are correct. Consumers that poll the mouse
inside the scoped HUD or screen render are not. Hover handling, debug rendering or custom screen code
can therefore observe a pointer compressed toward the viewport origin whenever the viewport is
narrower than the window.

The conversion should start from raw cursor coordinates and explicitly map the full-window physical
space into viewport-local GUI space. CraftUI's `MouseUtils.calculateViewportMouse` demonstrates the
necessary shape of that calculation, although Glue must preserve its own top-left framebuffer
conventions.

> **Resolved.** The instance `getScaledXPos`/`getScaledYPos` overloads now compute directly from the
> raw cursor via `ViewportMouse.toViewportGui` — screen → framebuffer → viewport-local → GUI units —
> using the stashed full-window dimensions, so the same coordinate comes back at event time and from
> inside a narrowed render scope. Static movement-delta overloads use the same physical conversion,
> and the world composite draws under an inverse-scale transform so fractional GUI origins remain
> pixel-aligned with the HUD and pointer. Covered by `ViewportMouseTest`, including high-DPI and
> non-divisible dimensions.

### P2: Painted Dock Guides and Hit Regions Disagree

The drop overlay paints five fixed-size guide controls around the center of a leaf. Hit testing still
divides the complete leaf into proportional center and edge regions. In a normally sized wide pane,
the painted left and right controls may be located entirely inside the semantic center region.

Releasing over a visible left control can therefore join the destination tab group instead of
splitting left. Root-edge controls have a similar mismatch: the painted control extends beyond the
root edge band that actually selects a root split.

The hit regions must be computed once and passed to both rendering and target resolution. Dear
ImGui's docking preview follows this principle: preview setup determines accepted rectangles and
preview rendering draws those same rectangles.

> **Resolved.** `DockGuides` is now the single authority: every guide control pairs its `DropTarget`
> with one rectangle that is painted, hovered, highlighted and hit-tested; the proportional zone and
> edge-band model is gone. Overlapping controls are removed in priority order, sole-tab self-splits
> are suppressed, insertion is confined to the live tab strip, and previews use the same 34/66 share
> and splitter extent as the resulting model. `DockGuidesTest` proves painted controls and accepted
> regions agree.

### P2: Closing While Game Focus Is Held Can Release the Cursor Later

Overlay teardown immediately resets focus and grabs the mouse for ordinary gameplay. Fragment
destruction occurs later on the ModernUI thread. Glue Studio's final destruction releases its
`ViewportController`, which schedules `GameFocus.release()` on the client thread.

A programmatic close while fly mode is active, or a sufficiently fast focus-release and close
sequence, can therefore grab the cursor during unmount and release it again after the workspace has
disappeared.

Closing a mounted session should invalidate pending focus transitions before restoring final cursor
ownership. `GameFocus.release()` should also avoid changing cursor state for a session that is no
longer the active owner.

> **Resolved.** Focus operations are scoped to the mount that requested them: the mount is captured
> when `GameFocus.request()`/`release()` is called and re-checked when the client thread applies the
> change, so queued work from a closed workspace is dropped. Session cleanup now runs synchronously
> before the overlay slot can be reused instead of arriving later from Fragment destruction. The
> lifecycle game test closes a workspace mid-focus and asserts a queued stale release cannot free the
> cursor.

### P2: Viewport Bounds Can Outlive the Visible Workspace

The Studio viewport clears `GameViewport` and `GameSurface` when its View detaches. Fragment removal is
asynchronous, while `OverlayHost.unmount()` immediately stops drawing the overlay. One or more frames
can therefore render the game into the old pane rectangle after the editor chrome has disappeared.

The mounted session or viewport binding should synchronously relinquish viewport ownership as part of
teardown. View detachment should remain an idempotent fallback rather than the only cleanup path.

> **Resolved.** The host clears `GameSurface` itself during teardown, and the new
> `Dockspace.Builder.onUnmount(...)` callback runs on the client thread inside the close operation.
> Glue Studio publishes both viewport globals together on that thread under a workspace/View owner,
> closes the owner before clearing them, and rejects late draws or detach callbacks from old Views.
> The Studio game test asserts the world claim is gone synchronously after `close()`.

### Existing Defect: Split Floating Windows Advertise an Invalid Drop

The model permits a floating `DockWindow` whose root is a `DockSplit`. Dragging such a window over a
leaf header produces a valid center preview, but `DockOperations.dropFloat()` accepts a center merge
only when the moved root is `DockTabs`. Releasing a split window over the preview does nothing.

The underlying mismatch predates the reviewed four-commit range, although the richer guide
presentation makes it more visible. Glue should either support whole-node center docking or suppress
the target before presenting it as valid.

> **Resolved.** The decided semantics: a split-rooted window docks whole through the leaf edge and
> stage-edge guides — `wrap` already handles arbitrary nodes — and is never offered a center merge,
> which the guides suppress at presentation and `dropFloat` still guards. A tabs-rooted window keeps
> center merging, now at the pointer's insertion position.

## File and Package Architecture

### Dock Packages

The current public/internal split is appropriate. No broad package rewrite is recommended.

`DockHostView` is the main area to watch. It necessarily coordinates the rendered tree, floating
windows, gestures, drop targets and maximize state, but active drag-session state should not continue
accumulating there indefinitely. A focused internal `DockDragSession` would be justified if it owns:

- the dragged pane or window identity
- source geometry
- current pointer position
- current validated drop target
- ghost and preview state
- cancellation and completion

Layout mutation should remain in the stateless `DockOperations`; extracting a drag session is not a
reason to introduce model-view-presenter layers around every native View.

### Mixin Packages

The distinction between `client/mixin` and `mui/mixin` is understandable after reading the targets,
but it is not immediately self-documenting. Names such as `internal/mixin/minecraft` and
`internal/mixin/modernui` would communicate the boundary more directly.

This is an organizational preference rather than a defect. Renaming the packages is not worth churn
unless the mixin set grows or is reorganized for another concrete reason.

### Feature-Oriented Showcase Files

The Glue Studio package is organized by feature rather than by global technical layer:

```text
mcsx/studio/
    GlueStudio
    StudioSession
    ViewportPaneView
    ViewportController
    LightRadarView
    domain records
```

That is a good structure. It keeps the complete editor workflow together and prevents repository-wide
`views`, `models` and `controllers` packages from becoming unrelated collections.

`GlueStudio` and `StudioSession` are large, but line count alone does not justify fragmentation. A UI
declaration is easier to understand when its panes and surrounding chrome remain visible together.
Extract a pane when it has independent behavior, native drawing, substantial state or a reusable
composition contract. `ViewportPaneView` and `LightRadarView` meet that threshold; a short form pane
does not.

## UI Declaration Review

### What Works

MCSX declarations successfully combine imperative Java where behavior is required with declarative
tree construction where structure is required. Fluent properties such as `classes`, `state`,
`visible`, `enabled` and reactive bindings remain attached to real native Views.

This approach provides:

- direct access to Minecraft and ModernUI APIs
- compile-time method and type checking
- ordinary extraction and reuse through Java methods or classes
- explicit retained identity
- no reflection or generated binding layer
- easy creation of custom canvas and input Views
- resource-driven presentation without resource-driven behavior

### Main Ergonomic Risk

Deeply nested factory calls become difficult to scan when every label, state, class, action and child
is inline. The preferred correction is semantic local composition:

```java
private View inspector(Ui ui) {
    View colorFields = this.colorFields(ui);
    View actions = this.inspectorActions(ui);

    return ui.column(
            ui.translatableHeading("example.editor.inspector"),
            colorFields,
            actions
    ).classes("inspector");
}
```

This keeps the visual hierarchy readable without introducing markup. Small methods should describe
meaningful pieces of the interface, not merely wrap every individual widget.

### API Naming

Pairs such as `literalHeading` and `translatableHeading` make text semantics explicit, which is useful.
The equivalent multiplication of `DockPane.literal`, `DockPane.translatable`, `builderLiteral` and
`builderTranslatable` is somewhat noisy because the canonical title type is already Minecraft's
`Component`.

A future API revision could make the `Component`-taking builder the obvious core API and retain
literal/translation helpers as conveniences. This is not urgent and does not justify compatibility
churn by itself.

## Dockspace Declaration Review

The builder declaration is clear and appropriately separates concerns:

```java
Dockspace.builder(id)
        .pane(this.viewportPane())
        .pane(this.inspectorPane())
        .pane(this.consolePane())
        .defaultLayout(defaultLayout)
        .theme(theme)
        .stylesheet(stylesheet)
        .header(DockContent.ui(this::header))
        .footer(DockContent.ui(this::footer))
        .onLayoutChanged(this::layoutChanged)
        .build();
```

The declaration distinguishes:

- which panes exist
- how the initial workspace is arranged
- which presentation resources apply
- which chrome surrounds the dock host
- which callbacks observe runtime changes

This is a stronger public application-level API than Dear ImGui's internal and explicitly unfinished
DockBuilder API.

### Stable String Identities

Pane ids must remain stable strings because layouts are persisted and may be supplied by JSON. The
same ids also appear in runtime calls and Java defaults:

```java
.pane(... "viewport" ...)
DockLayouts.tabs("viewport")
dockspace.focusPane("viewport")
```

This creates typo risk, but an elaborate generic pane-id system would add more complexity than value.
The practical convention is to centralize ids used in more than one place:

```java
private static final String VIEWPORT = "viewport";
private static final String INSPECTOR = "inspector";
```

A later overload may accept `DockPane` references when building Java layouts, while still serializing
their stable string ids. This is an ergonomic improvement, not an architectural requirement.

### Content Ownership

`DockContent.create(Context)` and matching disposal provide an important lifecycle contract, but the
factory/disposer pair is another place where mount ownership can become ambiguous after partial
failure. The current API can remain if the mounted session centrally guarantees exactly one disposal
for every successfully created View.

There is no demonstrated need for a more elaborate public mounted-content abstraction yet.

## CraftUI Comparison

CraftUI's `DockSpaceApp` delegates docking semantics to Dear ImGui through
`ImGui.dockSpaceOverViewport`. Its architecture is consequently much smaller at the application
level, and it inherits mature docking behavior without representing an immutable Java dock tree.

CraftUI currently has advantages in:

- arbitrary Dear ImGui tab placement and reordering
- validated docking previews
- whole-node undocking and redocking
- inherited node and window policies
- viewport input modes such as none, hold, focus and always
- explicit raw mouse remapping into a custom viewport
- restoring an unlocked cursor to the embedded viewport center

MCSX has advantages in:

- typed immutable layouts
- retained native View identity
- explicit resource and Java defaults
- application-controlled JSON persistence
- reactive themes and stylesheets
- pane content ownership and disposal
- integrated maximize behavior
- direct native ModernUI composition
- deterministic in-window floating geometry
- explicit Minecraft HUD and vanilla screen confinement

CraftUI is useful prior art for viewport coordinate conversion and cursor placement. It is not a
template for replacing MCSX's retained architecture with immediate-mode windows.

## Dear ImGui Feature Comparison

| Area | MCSX Dockspace | Dear ImGui Docking |
| --- | --- | --- |
| Layout representation | Public immutable Java tree | Runtime dock nodes and `.ini` settings |
| Programmatic defaults | Public Java factories and resource JSON | Internal, unfinished DockBuilder API |
| Content model | Retained native Views | Immediate-mode window submission |
| Basic docking | Tabs, splits, resizing, floating and redocking | Tabs, splits, resizing, floating and redocking |
| Tab ordering | Pointer-position insertion and arbitrary reordering | Pointer-position insertion and arbitrary reordering |
| Whole-node movement | Split windows dock whole through edge guides | Whole hierarchy undocking and redocking |
| Tab bar visibility | Always shown | Tab bars may be hidden |
| Dock restrictions | Pane close policy | Extensive window and node docking flags |
| Drag modifier | None | Shift can disable docking during a drag |
| Native OS windows | Intentionally confined to Minecraft | Optional multi-viewport OS windows |
| Transparent center | Transparent pane and game viewport | Pass-through central dock node |
| Diagnostics | Stable View tags and game tests | Docking event log and node inspector |
| Persistence | Atomic per-workspace JSON | Dear ImGui `.ini` persistence |

MCSX should adopt proven interactions without treating full Dear ImGui parity as the goal.

### High-Value Interaction Improvements

1. Use authoritative shared rectangles for guide rendering and hit testing. — **Done** (`DockGuides`).
2. Support arbitrary tab insertion and reordering. — **Done** (strip drops carry an insertion index).
3. Support whole-node docking or suppress unsupported targets before preview. — **Done** (edge
   docking for split windows; center merges suppressed for them).
4. Add a small set of demonstrated docking policies, such as no-undock or no-split. — **Deferred**:
   no current tool demands one beyond the existing pane close policy; adding flags without a consumer
   would be speculative.
5. Add a lightweight dock-tree and drag-target debug inspector. — **Deferred**: target resolution is
   now a pure, unit-tested function with stable View tags for tests, and no consumer for a runtime
   inspector exists yet.

### Optional Features

- Shift to disable docking during a drag
- hidden tab bars
- operating-system multi-viewport windows
- every Dear ImGui node flag
- an ImGui-style internal DockBuilder equivalent

These should be added only when a real editor requires them. Minecraft-confined floating windows are
an intentional product choice, not automatically a missing feature.

## Recommended Evolution Plan

Phases 1–3 are implemented; Phase 4 remains deferred.

### Phase 1: Correct Ownership and Threading — done

1. Make `OverlayHost.mount()` return an ownership-specific handle.
2. Require that handle for unmounting.
3. Set opened state only after successful host acquisition.
4. Marshal complete close operations onto the client thread.
5. Clear host state only after the owning handle closes successfully.
6. Roll back the host mount when Fragment or View creation fails.
7. Invalidate pending game-focus operations during session teardown.
8. Clear viewport and surface ownership synchronously on close.

This phase preceded further docking work because the original failures could leave the complete UI
host unusable.

### Phase 2: Correct Geometry and Input — done

1. Define drop-guide rectangles in one geometry function.
2. Use the same rectangles for drawing, highlighting and hit testing.
3. Validate payload compatibility before exposing a drop target.
4. Calculate viewport-local pointer coordinates directly from raw cursor coordinates.
5. Add focused tests for guide centers, guide edges and viewport mouse conversion.
6. Verify HUD hover, chat suggestions, vanilla screens and high-DPI windows in game.

### Phase 3: Improve Core Docking Interactions — done

1. Add insertion-index-aware tab reordering.
2. Decide the supported semantics for dragging a complete dock node.
3. Add only the docking restriction policies required by real tools. (None demanded yet.)
4. Add debug visualization for the semantic tree, active drag and accepted target rectangles.
   (Deferred with item 5 above.)

### Phase 4: Refine the Public Lifecycle API — deferred

Once the ownership semantics are proven internally, separate the reusable dockspace definition from
the mounted runtime session. This may require a public API revision, so it should follow the surgical
host corrections rather than block them.

## Verification Notes

The focused verification used during this review passed:

```powershell
.\gradlew.bat compileJava test
.\gradlew.bat :glue-showcase:runClient '-Pglue.gametest=glue-test:mcsx-lifecycle' '-Pglue.showcase.quickplay=New World'
.\gradlew.bat :glue-showcase:runClient '-Pglue.gametest=glue-test:mcsx-studio' '-Pglue.showcase.quickplay=New World'
```

The lifecycle scenario passes all 34 steps, including UI-thread closure, rejected ownership, focused
cursor teardown, failed Fragment creation, mount-callback closure and immediate slot reuse. The Studio
scenario passes all 118 steps, including real pointer docking, positional tab insertion, retained View
identity, viewport capture/release, vanilla-screen routing and synchronous restoration of the full
game window. `git diff --check` passes.

## Final Position

Keep:

- the immutable dock layout model
- retained pane and native View identity
- pure layout operations
- Java-first UI composition
- reloadable themes and stylesheets
- Java and resource layout defaults
- feature-oriented showcase packages
- the existing public/internal dock package boundary

Change:

- global unmount into ownership-specific session teardown
- implicit thread assumptions into explicit marshalled lifecycle operations
- independently calculated drop visuals and hit regions into shared geometry
- viewport mouse remapping into a direct raw-to-local conversion
- detach-only viewport cleanup into owned synchronous cleanup

Do not rewrite the dockspace around Dear ImGui. Use Dear ImGui and CraftUI as interaction and viewport
references while retaining MCSX's stronger application-level model.

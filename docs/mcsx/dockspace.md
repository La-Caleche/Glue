# Dockspace & Embedded Game Viewport

**A dockspace is a full editor-style workspace over the running game:** the live, playable game
embedded in a central **viewport pane** — world, HUD, and vanilla screens all render *inside* the
pane — surrounded by dockable panes with tab strips, draggable splitters, drag-and-drop docking,
floating windows, and a persisted layout. It is the same model as an ImGui dockspace, built
natively on MCSX's overlay + ModernUI stack.

One call opens the whole thing:

```java
McsxDockspace.open(DockConfig.builder("demo")
        .pane(dockPane("hierarchy", "Hierarchy", "layout"))
        .pane(dockPane("inspector", "Inspector", "sliders"))
        .pane(dockPane("console", "Console", "terminal"))
        .pane(dockPane("profiler", "Profiler", "info"))
        .defaultLayoutAsset("mcsx:default")
        .build());
```

Try it in the testmod: `/mcsx dock` toggles the demo workspace, `/mcsx dock reset` restores its
default layout, and `/mcsx viewport` is a bare embedding proof with no dock chrome at all.

## Panes — `DockPane`

A pane is an **id** the layout refers to, the **title/icon** shown in its tab, and a **factory**
for its content view:

```java
public record DockPane(String id, String title, String icon, PaneContent content)
```

- `icon` is an [Icons](icons.md) name, or null.
- `PaneContent.create(Context)` is called **lazily, once**, the first time the pane becomes
  visible. From then on the view is **reparented, never rebuilt**, as the pane moves between
  leaves, floats, and drops — pane state (scroll position, signals, focus) survives every layout
  mutation. `dispose()` runs when the dockspace closes.
- `DockPane.ofDocument(id, title, icon, document, controllerSupplier, registry, resolver)` is the
  standard way to back a pane with a bound `.mcsx` document — the bind happens on first show, and
  the document's effects are disposed with the dockspace.

### The reserved `viewport` pane

The pane id **`"viewport"`** (`DockPane.VIEWPORT_ID`) is the embedded game. You never provide its
content — if your config (or a persisted layout) doesn't mention it, one is added automatically.
It docks, tabs, splits, and floats like any other pane; the game follows it.

## Configuration — `DockConfig`

```java
DockConfig.builder("workspace-id")   // names the workspace; the persisted layout file is keyed by it
        .pane(...)                   // repeatable
        .defaultLayout(layout)       // a Java-built DockLayout, or…
        .defaultLayoutAsset("ns:name") // …a classpath asset at assets/ns/dock/name.json
        .inputMode(ViewportInput.Mode.CLICK) // default CLICK
        .releaseKey(GLFW.GLFW_KEY_ESCAPE)    // default Escape
        .gameViewport(false)                 // optional; dock still owns input
        .menuBar(false)                      // default true: the built-in File/Views menu
        .header(headerContent)               // optional fixed, non-dockable chrome
        .footer(footerContent)               // optional fixed, non-dockable chrome
        .onOpenPanesChanged(open -> refreshMenu(open))
        .onClose(this::disposeWorkspace)
        .persist(true)               // default true
        .build();
```

## The menu bar

Every workspace renders an ImGui-style menu bar above its chrome by default:

- **File → Close** closes the workspace.
- **Views** lists every registered pane with a ✓ on the open ones; clicking toggles it through the
  place-remembering `togglePane` (below).

The bar also claims the workspace shortcuts it advertises, shown next to each item: **Ctrl+Q**
closes the workspace and **Ctrl+1**…**Ctrl+9** toggle the first nine panes in registration order.
They live on the workspace-wide `KeyBindings`, so they fire wherever dock focus happens to be —
never while the game holds input — and a pane document that tries to `<key>`-claim the same combo
fails its bind with a message naming the conflict.

Disable it with `.menuBar(false)` — and pass a `.header(...)` when the workspace should carry a
custom menu instead (the shortcuts belong to the bar, so they go with it). The bar's dropdowns are
overlay layers: an outside click or Esc dismisses them before Esc means anything else.

## The embedded game viewport

The mechanism is framebuffer pinning, not render-to-texture:

1. **The game renders at pane resolution.** While a viewport pane is on screen, Minecraft's
   framebuffer is pinned to the pane's size — the world, the HUD, and any vanilla screen (chat,
   pause, inventory) render *complete* at that resolution. The HUD scales with the pane exactly
   like it scales with a small window.
2. **The present is redirected.** Vanilla's swap-to-window is cancelled; the finished game frame
   is blitted into the pane's sub-rectangle of the real window instead.
3. **The dock UI draws at full window resolution** on top, with the viewport pane punching a
   transparent hole the game shows through.
4. **Mouse coordinates are remapped** so the game (and any vanilla screen rendering in-pane)
   receives positions in its own pane-sized space, while the dock UI keeps real window
   coordinates.

Pane resizes apply **every frame** of a splitter drag — the render targets are recreated live, so
what you see is always rendered at the size it is shown.

## Input model

Who owns the mouse and keyboard is derived from one live fact — **whether the cursor is
grabbed** — never from a separate mode that could drift out of sync:

| Cursor state | Game | Dock UI |
|---|---|---|
| **Grabbed** (playing) | full input; mouse turns the player | inert |
| **Free** — for *any* reason (release key, a screen opening, focus loss) | keeps running; a vanilla screen in-pane still gets its clicks/keys | hoverable, clickable, draggable |

Clicking inside the viewport pane hands input to the game; the **release key** (default Escape)
hands it back and drops the cursor at the pane's center. How the hand-off works is the
`ViewportInput.Mode`:

| Mode | Behavior |
|---|---|
| `CLICK` *(default)* | Clicking the pane captures; the release key hands back. |
| `HOLD` | The game has input only while the capture button is held over the pane. |
| `ALWAYS` | Starts captured; the release key opens the dock. |
| `NONE` | Only a programmatic `ViewportInput.captureGame()` enters the game. |

While a **vanilla screen** is open it renders in-pane, receives remapped clicks, and clicks on the
pane don't flip capture — the dock stays hoverable around it. The **keyboard follows the pointer**,
ImGui-style: keys go to the dock while the cursor is over it or after the last click landed on it
(so a focused text field keeps receiving input), and back to the screen the moment you click or
hover anywhere else. Escape always stays with vanilla.

## Working the layout

Everything the mock/POC does, live:

- **Splitters** — 8 px grab area between panes; dragging adjusts the split ratio (clamped so no
  pane collapses below 7%).
- **Tabs** — a leaf holding several panes shows a tab strip; click activates, the ✕ closes.
- **Drag to dock** — drag a tab ~4 px and it detaches into a ghost chip. Hovering a pane shows the
  **5-zone drop cross** (center = stack as tab; left/right/top/bottom = split that pane); the
  window's four edges accept root-level docks. Dropping nowhere floats the pane.
- **Floating windows** — title-bar drag, 8-direction resize (min 220×150), close, and re-dock by
  dragging the title bar over a drop zone. Click brings to front.

Every finished mutation is persisted (when `persist` is on).

## Layout persistence

Resolution order at open, each level falling back to the next with a warning (never a failed
open):

1. the user's mutated layout — `config/mcsx/dock/<id>.json`
2. the configured default — `defaultLayout(...)` or the `defaultLayoutAsset` at
   `assets/<ns>/dock/<name>.json`
3. a single leaf holding every registered pane

Whatever loads is **sanitized against the registered pane set** — a stale file never puts a ghost
pane on screen, and a missing `viewport` pane is tolerated.

### The layout JSON

```json
{
  "version": 1,
  "tree": {
    "type": "split", "dir": "row", "sizes": [0.19, 0.60, 0.21],
    "children": [
      {"type": "leaf", "tabs": ["hierarchy"], "active": "hierarchy"},
      {
        "type": "split", "dir": "col", "sizes": [0.72, 0.28],
        "children": [
          {"type": "leaf", "tabs": ["viewport"], "active": "viewport"},
          {"type": "leaf", "tabs": ["console", "profiler"], "active": "console"}
        ]
      },
      {"type": "leaf", "tabs": ["inspector"], "active": "inspector"}
    ]
  },
  "floats": []
}
```

- A node is a `"leaf"` (`tabs` + `active`) or a `"split"` (`dir` = `row`/`col`, `children`,
  `sizes` — fractions that are renormalized on read).
- Each entry of `"floats"` is `{"node": …, "x", "y", "w", "h", "z"}` in window pixels
  (defaults 360×260 when absent).
- The reader is tolerant: unknown fields are ignored, sizes renormalize, degenerate splits prune.
  Structurally broken JSON falls back a resolution level.

### Closed panes keep their place

Closing a pane does **not** drop it from the file. It moves into a `"closed"` section holding how
it was placed, and reopening it (the Views menu, `togglePane`) puts it back there — same tab strip
and index, same split side and share, or the same floating frame — instead of spawning a fresh
cascaded window:

```json
"closed": {
  "console":   {"kind": "tabbed", "with": "profiler", "index": 0},
  "inspector": {"kind": "beside", "panes": ["viewport"], "zone": "right", "share": 0.21},
  "profiler":  {"kind": "float", "x": 40, "y": 60, "w": 380, "h": 280}
}
```

Anchors are **pane ids**, never node ids (node ids are re-minted on every load): a `tabbed` ghost
names a pane it shared the leaf with, a `beside` ghost names the sibling subtree's panes and is
re-resolved by content when it reopens. Each kind degrades to the next when its anchor is gone,
and with no usable memory the pane opens as the cascaded window it always used to. Sanitizing
drops a closed entry whose pane is unknown — or open, in which case the open pane wins.

## Public API — `McsxDockspace`

| Method | Meaning |
|---|---|
| `McsxDockspace.open(config)` | Opens the workspace (or returns the already-open one, logged). |
| `McsxDockspace.current()` | The open dockspace, or null. |
| `close()` | Unmounts, disposes pane contents, restores the real window. |
| `resetLayout()` | Deletes the persisted file and re-applies the configured default, live. |
| `saveLayout()` | Persists the current layout now (mutations already save themselves). |
| `togglePane(id)` | Opens the pane as a floating window, or closes it — the panels-menu toggle. |
| `isGameCaptured()` | Whether the game currently owns input. |

## Where the code lives

| Layer | Package | Tested how |
|---|---|---|
| Layout model — tree, ops, geometry, drop hit-test, JSON codec | `core.dock` | headless JUnit (pure `java.*`) |
| Dock chrome — host, tab strips, splitters, floats, drop overlay | `view.dock` | compile + in-game |
| Game embedding — framebuffer pin, present redirect, input routing | `viewport` + `client.mixin` | compile + in-game |

Extending layout *behavior* (new ops, drop rules, codec fields) belongs in `core.dock` first,
with tests; the view layer only renders and gestures over that model.

## Gotchas

- **One dockspace at a time**, and it's **mutually exclusive with MCSX screens** — `open()` throws
  while a `MuiScreen` is up, and MCSX screens refuse to open while a dockspace is embedding.
  Vanilla screens are fine (they render in-pane).
- **`"viewport"` is reserved.** Don't register your own pane under that id expecting custom
  content — it is the game.
- **Pane content is created once and reparented.** Don't assume `create()` runs per layout change;
  put per-show logic in the view's attach callbacks, not the factory.
- **Layout files are per-workspace-id.** Two configs sharing an `id` share (and overwrite) the
  same persisted layout.
- **HiDPI (framebuffer px ≠ screen px, e.g. macOS retina) is out of scope** — the embedding
  assumes they match.
- **Raw GL near the embedding must respect Blaze3D's state caches.** `GlStateManager` caches
  framebuffer bindings and color mask (and `GlCommandEncoder` caches the bound shader program)
  and *skips* matching changes; GL calls that bypass the wrappers desync them and corrupt a
  frame. Go through `GlStateManager`/`DirectStateAccess`, or call
  `BlazeStateSync.resyncAfterRawGl()` when your raw-GL block ends.

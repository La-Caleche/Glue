# UI kit and developer menu: design

Status, 5 October 2026: steps 1 to 3 are implemented: the kit in `client/ui`, and the developer menu
with its Framebuffers and Raycast pages in `client/debug`. The `glue-docs` pages and the in-game
check remain. Target: Glue 3.3.0, additive.

Glue needs simple native screens for its own tools: a developer menu shipped in the player jar, real
screens for the framebuffer and raycast debug, and a page Lumos can register for its lights. Today it
has only `AbstractViewportScreen` and HUDs drawn by hand. This note defines a small kit on
`GuiGraphics` for those screens, in the style of Sodium's and Iris's option screens, and the
developer menu built with it.

## What exists and what it teaches

| Where | What | Lesson |
|---|---|---|
| `glue-mcsx`, removed 14 September 2026 (`d53d8b3`) | A native UI framework of about 32,000 lines | A framework is what to avoid; the kit stays a thin layer over vanilla |
| Porthole | Chromium pages with native slots | Right for rich interfaces, too heavy to ship in Glue: Chromium downloads its natives on first launch |
| `debug/FboDebugHud` (519 lines) | A grid of borrowed GL textures on the HUD, driven by polling GLFW keys | The texture view works (`ExternalTexture` + `GuiGraphics.blit`); input, layout and paging are rewritten by hand, and it registers a texture location per id that it never releases |
| `debug/RaycastDebugRenderer`, `DebugManager` | World and HUD overlays switched by a field | Overlays that must be seen while playing stay overlays; a screen only switches them |
| `viewport/internal/BorrowedSceneTexture` | One location per borrowed texture, released on close | The model for the kit's texture view |
| Vanilla 1.21.8 `client/gui` | `Screen`, `AbstractWidget`, `LinearLayout`, `GridLayout`, `FrameLayout`, `ScrollableLayout`, `ContainerObjectSelectionList`, `TabNavigationBar`, `Tooltip`, focus navigation and narration | Focus, keyboard, mouse, layout and narration exist; the kit changes how they look |
| Vanilla 1.21.6+ GUI rendering | `GuiGraphics` records render states, drawn at the end of the frame by `GuiRenderer` | Widgets never issue GL; anything raw happens outside drawing, behind `SavedGlState` |

## Boundary with Porthole

The kit is for tools and settings: rows of labelled values, lists, texture previews. Porthole is for
rich, player-facing interfaces. Glue never depends on Porthole, and the kit never grows the features
that would make it a second Porthole.

## Rules

1. **Vanilla underneath.** Every widget is an `AbstractWidget`, every screen a `Screen`. Layout uses
   vanilla `LinearLayout`, `GridLayout`, `FrameLayout` and `ScrollableLayout`. The kit writes no
   layout engine, no input dispatch and no focus system.
2. **A closed widget list.** Adding a widget is a design change, recorded here.
3. **Plain values.** A widget reads its value from a `Supplier` each frame and reports a change
   through a `Consumer`. No binding framework, no observable properties.
4. **No markup, no themes, no animation, no docking, no drag and drop.** One style, held in one
   record of colours and metrics, so a later change is one place.
5. **Drawing through `GuiGraphics` only:** filled rectangles, outlines, the vanilla font, and
   borrowed textures. No sprites, so a resource pack does not restyle it and it needs no assets.
6. **A budget.** The kit (style, widgets, screen base) stays within about 1,500 lines. Past that, stop
   and revisit this note.
7. **Render thread only.** Widgets, pages and suppliers run on the client render thread.

## Look

The look follows Sodium's video settings: flat translucent panels over the world, the vanilla
font, no blur.

- A page sidebar on the left, grouped by mod, with the selected page highlighted.
- Rows in the centre: the label on the left, the control on the right, a lighter row under the
  pointer or focus.
- A description panel on the right shows the focused or hovered row's description, in place of
  floating tooltips. Below a minimum width it falls back to vanilla `Tooltip`.
- A section header separates groups of rows.
- Focus draws a one-pixel outline, so the whole kit is usable with the keyboard alone.

`UiStyle` holds the colours (panel, row, hover, focus, text, muted text, accent, disabled) and the
metrics (row height, padding, sidebar and description widths). It is a record with one default
instance; screens read it, and nothing swaps it at runtime.

## Widgets

| Widget | Built on | Value |
|---|---|---|
| `UiLabel` | `AbstractWidget`, inactive | A `Component` shown, not edited |
| `UiButton` | `AbstractButton` | An action |
| `UiToggle` | `AbstractButton` | `boolean` |
| `UiCycle<E>` | `AbstractButton` | One of a list, such as an enum, with a label function; Shift steps back |
| `UiSlider` | `AbstractWidget` | A `double` within a range and step, with a value formatter; a whole-number step reports whole numbers |
| `UiTextField` | `EditBox` unbordered, inside a drawn frame | `String` |
| `UiColorSwatch` | `AbstractWidget`, inactive | A `Color` shown, not edited |
| `UiTextureView` | `AbstractWidget`, inactive | A borrowed GL texture, flipped or not, with a caption |
| `UiRowList` | `ContainerObjectSelectionList` with its background, separators and scrollbar redrawn | Scrolling rows, each a label and one control, and section headers |

`UiSlider` is not an `AbstractSliderButton`: that class stores the value as a 0 to 1 fraction and
draws vanilla sprites, so the kit would fight both. A section header is a `UiRowList` entry, not a
widget, so it scrolls with its rows.

`UiTextureView` takes a texture supplier returning the GL id and size, or nothing. It keeps one
location per view, re-registers it only when the id or size changes, and releases it when its screen
closes, as `BorrowedSceneTexture` does. That class moves to `ui/internal`, and the viewport and the
texture view share it. It never writes the borrowed texture's filter or wrap modes: its wrapper's
vanilla defaults would otherwise be flushed onto the owner's texture on first bind and stay there,
changing, for instance, the block atlas's mipmapping.

Rows are the unit pages build with: `UiRowList.row(label, description, control)`. A page that needs
something else, such as a grid of textures, lays out widgets with vanilla layouts itself.

## Screen base

`UiScreen` extends `Screen`: the sidebar, shown only with more than one page and listing pages under
`UiScreen.Group` titles, the content area, the description panel and a Done button. Page actions are
ordinary rows, not a bottom bar. It draws a dimmed panel in place of the blurred background, over the
panorama on the title screen. A page is:

```java
public interface UiPage {
    Component title();

    /** Builds this page's widgets into the content area; called each time the page is shown. */
    void build(UiPageBuilder builder);

    /** Called when the page is hidden or its screen closes; releases what {@link #build} took. */
    default void close() {
    }

    /** Why the page cannot be shown now, such as needing a world, or null when it can. */
    default @Nullable Component unavailableReason() {
        return null;
    }
}
```

`UiPageBuilder` offers one method per row widget, `rows()` for the list itself, and `area()` with
`add` for a page that places its own widgets. `rows(ScreenRectangle)` keeps the rows to part of the
area, for a page that places other widgets beside them. Switching pages swaps only the page's widgets, so the
sidebar keeps its scroll and focus.

Pages are built when shown and closed when hidden, so a page costs nothing while it is not on
screen. `UiScreen` is public: a mod can show its own pages without the developer menu.

## Developer menu

- One `UiScreen`, opened by a key binding, **F8** by default. In a world it does not pause the game,
  so the world keeps rendering behind it. F8 also opens it from the title screen, through Fabric's
  screen keyboard events; no button is added to vanilla screens. Done returns to the screen it was
  opened from.
- Anyone can open it, as F3: it holds nothing secret, and it costs nothing while closed.
- Pages come from a registry, `DeveloperMenu.register(ResourceLocation id, boolean needsWorld,
  Supplier<UiPage> page)`, grouped in the sidebar by the id's namespace and ordered by registration, except that Glue's group
  comes first, since mods initialise in no fixed order.
  Without a world, a page that needs one is listed muted, and shows its `unavailableReason`. Registering happens during
  client initialisation; the registry is closed afterwards.
- Page settings, such as an overlay switched on or Show on HUD, reset each launch. Nothing is saved,
  so nothing stays on by surprise.
- Glue registers two pages, both needing a world: Framebuffers and Raycast.
- Nothing runs while the menu is closed, except overlays a page has switched on.
- Labels are translatable. Glue gains its `en_us` language file, which also names its key bindings.

### Framebuffers page

It replaces the F8 HUD. `DeveloperMenu` is public in `client/debug`; the viewer, its page and its
grid live in `client/debug/internal`.

- The page's rows, on the left, hold a filter (all, colour, depth), an Iris alternates toggle, a grid
  size slider (1 to 4), the page number with Previous and Next, Show on HUD, and a visibility toggle
  per texture. The grid, on the right, is one widget that lays its `UiTextureView`s out each frame for
  the grid size, so the HUD draws the same widget; the mouse wheel over it turns pages.
- The main-colour copy and the depth capture move to `POST_WORLD_RENDER` and run only while the page
  is shown, behind `SavedGlState`. They no longer run during GUI submission.
- The depth preview is captured only while a depth tile is visible. Its synchronous readback stays
  for now, and the page says so; a GPU linearization is a separate change.
- **Show on HUD** closes the menu and keeps the grid drawn on the HUD, without input, so the buffers
  can be watched while playing. F8 reopens the menu, and opening it takes the grid off the HUD.
- `FboDebugHud.registerTexture(String, IntSupplier)` keeps its signature and meaning, because Lumos
  uses it. The rest of `FboDebugHud` (`toggle`, `tick`, `render`, `captureDepthNow`) was never an
  extension point, and it goes. The class keeps its name until a major version.

### Raycast page

It switches Glue's `RaycastDebugRenderer` overlay on and off, and shows the last trace as rows: the
vanilla hit, the cell hit, the final hit and the blocks reaching into the cells it crossed. The
overlay stays a world and HUD overlay. The page only switches it, because the pick needs the player
to move and look.

- Glue registers the one overlay, in `client/debug/internal` beside the page. The showcase no longer
  owns one: its R key opens the showcase menu, and its Debug page opens the developer menu.
- The overlay and the page share one trace, `RaycastProbe`. The page traces at most once a tick
  while it is shown, and lists up to six reaching blocks after their count.

### Lumos page (in Lumos)

Lumos registers its own page through `DeveloperMenu.register`: its lights in a filtered `UiRowList`,
the shadow budget, and switches for its debug drawing. Drawing lights in the world (range, cones,
gobo, shadow frustums) needs a world debug-draw library. That library is separate from this note.

## Tests and showcase

- Unit tests: the screen layout across widths, slider stepping and formatting, and the page
  registry's order and closing.
- Showcase: `/showcase` opens a `UiScreen` whose pages open the scene previews, play the post
  effects, open the developer menu, and show every widget and a borrowed texture. The scene
  previews carry a panel of rows for what their shortcuts do, which needs `AbstractViewportScreen` to
  draw its widgets and give them the pointer before the camera. The showcase also registers a page in
  the developer menu as the example for mods.
- Client test `ui` (vanilla, Sodium, Iris): open the menu with its key from the title screen and
  check world pages are disabled; then in a world, switch pages, click a toggle and drag a slider
  through `glue-gametest`'s input helpers, then check the values. On the framebuffers page, check
  that a registered texture appears, and that closing the menu releases its texture locations. On
  the Raycast page, check its toggle switches Glue's overlay.
- In-game check: GUI scales 1 to 4 and auto, a small window, keyboard-only navigation, and a narrator
  pass.

## Docs

- A new `glue-docs` page for the kit and one for the developer menu. `rendering/debug-hud.md` becomes
  the Framebuffers page's documentation.
- The 3.2.0 updates deferred from the release ride along: the "Next Glue release" box in
  `core/generated-shapes.md` and the versions table in `getting-started.md`.

## Order

1. **Kit:** `UiStyle`, the widgets, `UiRowList`, `UiScreen` and `UiPage`, with the showcase screen and
   unit tests.
2. **Developer menu:** the registry, the key, the language file, and the Framebuffers page with Show
   on HUD. Remove the HUD's input polling. Add the `ui` client test and the docs.
3. **Raycast page.**
4. **In Lumos:** its page, then the debug-draw library as its own design.

## Decisions

Taken on 4 October 2026:

1. **Key:** F8 opens the menu, in place of the HUD; the HUD survives as the Framebuffers page's Show
   on HUD.
2. **Persistence:** page settings reset each launch.
3. **Who sees it:** anyone, without an option.
4. **Without a world:** the menu opens from the title screen in the first version; pages that need a
   world are disabled there.

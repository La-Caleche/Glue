---
title: Layout and Panes
description: Evolve the Light Workshop from two panes to a visual Scene, Inspector, and Log layout.
artifact: glue-mcsx-dock
modId: glue-mcsx-dock
environment: client
---

# Layout and Panes

Start from the [two-pane workspace](./index.md). This task adds Log and changes the layout by matching
each visible arrangement to the Java that creates it. The immutable tree model comes afterward.

## Outcome

Scene receives roughly two thirds of the width. Inspector and Log share the remaining column, with a
horizontal splitter between Scene and the right rail and a vertical splitter inside the rail.

<DocImage title="Three-pane workshop layout" description="Capture a labeled Dockspace diagram and the matching in-game workspace: Scene fills the wide left region, Inspector is top-right, Log is bottom-right, and both splitter directions are visible." />

## Pair 1: Two Equal Panes

The overview's first layout is one horizontal row:

```text
+----------------------+----------------------+
| Scene                | Inspector            |
|                      |                      |
+----------------------+----------------------+
```

```java
DockLayout twoPane = DockLayouts.layout(DockLayouts.row(
        DockLayouts.tabs("scene"),
        DockLayouts.tabs("inspector")
));
```

`row(...)` creates an even horizontal split. Each `tabs(...)` call creates one leaf and makes its first
pane active.

## Pair 2: Add Inspector and Log Rail

Nest a vertical `column(...)` inside a weighted horizontal split:

```text
+--------------------------------+----------------+
| Scene                          | Inspector      |
|                                +----------------+
|                                | Log            |
+--------------------------------+----------------+
```

```java
DockLayout workshopLayout = DockLayouts.layout(DockLayouts.split(
        DockAxis.HORIZONTAL,
        List.of(
                DockLayouts.tabs("scene"),
                DockLayouts.column(
                        DockLayouts.tabs("inspector"),
                        DockLayouts.tabs("log")
                )
        ),
        List.of(0.68, 0.32)
));
```

Shares are relative weights and need not total `1.0`. The nested `column(...)` divides the right rail
evenly from top to bottom.

## Register the Log Pane

Register every pane even if a particular default layout does not currently show it:

```java
Signal<List<LogLine>> logLines = Signal.of(List.of(
        new LogLine(1, "Light Workshop opened")
));

DockPane log = DockPane.builderLiteral(
                "log",
                "Log",
                ui -> ui.screen(
                        ui.boundColumn(
                                logLines,
                                LogLine::id,
                                line -> ui.text(line.map(LogLine::message))
                        )
                )
        )
        .icon(Component.literal("LOG"))
        .trailingHeaderUi(ui -> ui.row(ui.literalText("LIVE")))
        .build();

record LogLine(long id, String message) {
}
```

Add `.pane(log)` and `.defaultLayout(workshopLayout)` to the builder. Pane ids must start with a
lowercase ASCII letter or number and may then contain lowercase letters, numbers, `.`, `_`, or `-`.
They are durable persistence keys; titles and icons are presentation.

Use `DockPane.literal(...)` or `builderLiteral(...)` for literal titles. Use `translatable(...)` or
`builderTranslatable(...)` for Minecraft translation keys. The full `builder(...)` accepts a
`Component` title and `DockContent` directly.

## Control Panes at Runtime

Add workspace actions without editing the layout tree yourself:

```java
workspace.focusPane("inspector");
workspace.togglePane("log");
workspace.maximizePane("scene");
workspace.restoreMaximizedPane();
```

`focusPane(id)` activates and focuses an open pane but leaves a closed pane closed. `togglePane(id)`
closes an open pane or reopens it in a centered floating window. Maximize is transient and leaves the
semantic dock location unchanged. Unknown ids throw `IllegalArgumentException`.

Mutation methods require an open workspace and marshal to ModernUI's UI thread. A call from another
thread therefore does not update `layout()` synchronously. Use `onLayoutChanged(...)` and
`onOpenPanesChanged(...)` to mirror completed semantic state.

## Retained Content Ownership

`DockContent.ui(ui -> ...)` adapts normal MCSX composition and has no disposal action. For a native
View that owns another resource, implement the complete lifecycle pair:

```java
DockContent previewContent = new DockContent() {
    @Override
    public View create(Context context) {
        View preview = new View(context);
        Cursors.set(preview, Cursors.crosshair());
        return preview;
    }

    @Override
    public void dispose(View view) {
        Cursors.clear(view);
    }
};
```

Content and trailing-header Views are lazy and independent. Each successfully created View receives
one matching disposal call when its Dockspace is destroyed. A View never requested is neither created
nor disposed. `header(DockContent)` and `footer(DockContent)` use the same create-once,
dispose-once contract outside the semantic layout.

::: details Immutable layout model and factories
A `DockLayout` has a nullable docked `tree()` and an ordered `windows()` list. A node is either a
`DockTabs` leaf or a `DockSplit` branch. A floating `DockWindow` holds another complete tree plus its
in-stage frame and stacking order; it is not an operating-system window. Layout records defensively
copy their lists and should be treated as immutable semantic snapshots.

| Factory | Result |
| --- | --- |
| `empty()` | No docked tree and no floating windows |
| `layout(tree)` | Docked tree without floating windows |
| `layout(tree, windows)` | Docked tree plus initial floating windows |
| `tabs(...)` | Tab group with its first pane active |
| `tabs(active, panes)` | Tab group with explicit active pane |
| `row(...)` | Even horizontal split |
| `column(...)` | Even vertical split |
| `evenSplit(axis, ...)` | Even split on an explicit axis |
| `split(axis, children, shares)` | Weighted split |
| `window(node, x, y, width, height)` | Initial in-stage floating subtree |
:::

::: details Layout invariants and change notifications
Construction rejects empty tab groups, duplicate ids within one group, an active id absent from its
group, splits with fewer than two children, share-count mismatches, non-finite or negative shares,
non-positive share totals, and non-positive floating-window dimensions. A valid workspace places a
pane id at most once across the docked tree and all windows.

Loading and same-axis edge docking flatten nested splits while preserving effective proportions. This
keeps one visible splitter between adjacent panes.

`layout()` returns the latest immutable semantic snapshot. `openPanes()` returns an immutable set of
all docked and floating ids. `onLayoutChanged` runs after completed structural, activation, move, or
resize mutations, not on every pointer move. Maximize and restore do not emit it because they are
presentation state. `onOpenPanesChanged` also receives the resolved open set at initial mount.
:::

## Next Steps

- [Persist this default and add reset](./persistence.md) before users customize it.
- [Move tabs, manage focus, and embed the live game](./interaction.md) once the structure is stable.
- [Review reactive keyed rows](../mcsx/components.md#retain-dynamic-rows) for a high-volume Log pane.
- [Use the complete milestone owner](../workshop/interface.md) to connect Scene, Inspector, and Log.

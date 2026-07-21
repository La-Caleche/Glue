# Layout — Flexbox

MCSX lays out containers with a **real flexbox engine**, not margin emulation. It has two parts:

- **`FlexEngine`** (`core.layout`) — a pure, headless, single-axis solver (unit-tested).
- **`FlexLayout`** (`view.*`) — a ModernUI `ViewGroup` that drives `FlexEngine` through ModernUI's
  measure/layout, handling both axes, wrapping, and absolute positioning.

Every `<div>` and `<button>` (and most component roots) is a `FlexLayout`. You control it entirely
through [utility classes](styling.md#layout).

## The model

```xml
<div class="flex-row items-center justify-between gap-3">
    <text>Left</text>
    <div class="grow"></div>   <!-- spacer -->
    <text>Right</text>
</div>
```

- **Direction:** `flex-row` (horizontal main axis) or `flex-col` (vertical). `<div>` defaults to
  **column**; `<button>` defaults to **row**.
- **Main-axis distribution:** all six `justify-*` values work —
  `start` / `center` / `end` / `between` / `around` / `evenly`.
- **Cross-axis alignment:** `items-start|center|end|stretch`, overridable per child with
  `self-start|center|end|stretch`.
- **Real gap:** `gap-N` spaces children by `N × 4` px (true gap, not a leading margin).

## Growing and shrinking

- `grow` — a child takes an equal share of leftover main-axis space; `grow-0` opts out.
- `shrink` / `shrink-0` — allow / forbid shrinking below basis when space is tight. Shrink is
  **weighted by size** (like CSS `flex-shrink`).
- A `w-full` / `h-full` main-axis size becomes "basis 0, grow" — it competes for space rather than
  overflowing.

The engine distributes free space with an iterative freeze-and-redistribute pass (max 8 passes), and
hands leftover rounding pixels to the first still-growable child so a line fills its container exactly.

When a container sizes to its **content** (main axis unbounded), no free space is distributed —
children keep their natural basis (CSS shrink-to-fit).

## Wrapping

`flex-wrap` breaks children onto multiple lines when they don't fit; `flex-nowrap` keeps them on one.

```xml
<div class="flex-row flex-wrap gap-2">
    <Badge>one</Badge>
    <Badge>two</Badge>
    <Badge>three</Badge>
</div>
```

Line breaking is greedy on the clamped basis; an over-wide single item gets its own line. An unbounded
main axis can't wrap (everything lands on one line).

## Fractional sizes

`w-1/2`, `w-1/3`, `h-2/3` size a child as a fraction of the measured parent — resolved by
`FlexLayout` after measuring, not by the parser. On an unbounded axis a fraction falls back to
wrap-content.

## Absolute positioning

`absolute` takes a child out of flow and positions it by its insets against the container's content
box:

```xml
<div class="relative h-0 grow rounded-md bg-surface-3">
    <surface class="absolute inset-0"/>
    <div class="absolute left-3 bottom-3 flex-row gap-2">
        <div class="rounded border bg-surface-2 px-2 py-1"><text class="text-xs">LMB Orbit</text></div>
    </div>
</div>
```

Use `inset-N` for all edges or `top-/right-/bottom-/left-N` for individual ones. `relative` / `static`
both mean "static flow" — every container is already a positioning context, so there is no CSS-relative
offset.

## Measurement passes

`FlexLayout` measures children up to three times so text re-wraps correctly:

1. an unbounded main-axis pass to find each child's natural basis;
2. a pass at the assigned size (so wrapped text reflows);
3. a `stretchToLine` pass that grows `items-stretch` children to the line's final cross size when the
   container's cross axis was unbounded.

You don't manage any of this — it's automatic — but it explains why a `<text>` inside a fixed-width
box wraps as expected.

## What is *not* modelled (vs CSS flexbox)

- **`align-content`** — wrapped lines pack to the cross-start and keep their own extents; only a single
  line absorbs spare cross-axis space.
- **`flex-basis` as a distinct property** — basis comes from the measured/declared main size.

## Gotchas

- **`<div>` is column, `<button>` is row** by default. Set `flex-row` / `flex-col` explicitly when it
  matters.
- **`grow-0` turns `grow` off, including one merged in from a component recipe** — merges are
  overlay-wins, so the call-site class always has the last word.
- **`justify-*` needs bounded space to have any effect** — a shrink-to-fit container has no free space
  to distribute.
- **Fractions need a bounded parent axis**; otherwise they wrap-content.

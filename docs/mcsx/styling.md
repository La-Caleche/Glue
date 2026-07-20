# Styling — the Tailwind subset

MCSX styles Views with a **subset of Tailwind utility classes**, parsed by `TailwindParser`
(`core.style`) into an immutable `StyleSpec`, then applied to ModernUI Views by `ViewStyles`
(`view.*`). It is **not** a CSS parser — only the families a flex container / View can express are
supported. Runtime parsing skips unsupported classes with a warning; strict linting makes unknown
classes fail the build for shipped resources.

```xml
<div class="flex-row items-center gap-2 rounded-md border bg-surface p-4">
    <text class="text-sm text-muted">Styled</text>
</div>
```

## The 4px scale

Every numeric step `N` on the spacing/sizing scale is **`N × 4` px** (`SCALE = 4`). So `p-4` = 16px,
`gap-2` = 8px, `w-10` = 40px. **Exceptions** (raw values, not ×4): `border-<n>`, `rounded-<n>` and
named radii, `opacity-*`, and the raw box attributes (`pad`/`w`/`h`/`size`). These are called out
below.

## Two parse modes & the fail-loud contract

- `TailwindParser.parse(str)` — **lenient**. An unsupported class is logged once (via `System.Logger
  "mcsx.style"`) and skipped; valid classes around it still apply. This is what the binder uses.
- `TailwindParser.parseStrict(str)` — **strict**. Throws `TailwindException` on the first bad class.
  This is what the [linter](testing.md) uses to catch typos at build time.

`TailwindException extends IllegalArgumentException` is thrown for: unknown utility, unknown variant
prefix, unknown color/border token, bad fraction, and malformed arbitrary values.

See [Tailwind Compatibility](tailwind-compatibility.md) for the measured family coverage and the
differences from Tailwind v4. MCSX currently has 15% partial-weighted coverage across 100
renderer-relevant utility families.

**Accepted but ignored (never throws in either mode):** `selection-*`, `scrollbar-*` — no ModernUI
analogue yet, so even a typo like `scrollbar-xyz` is silently accepted.

## Layout

| Class | Effect |
|---|---|
| `flex-row` / `flex-col` | main axis horizontal / vertical |
| `items-start` / `-center` / `-end` / `-stretch` | cross-axis alignment |
| `justify-start` / `-center` / `-end` / `-between` / `-around` / `-evenly` | main-axis distribution (all six work) |
| `self-start` / `-center` / `-end` / `-stretch` | per-child cross alignment override |
| `flex-wrap` / `flex-nowrap` | wrap children onto multiple lines |
| `grow` / `grow-0` | take / don't take leftover main-axis space |
| `shrink` / `shrink-0` | allow / forbid shrinking |
| `gap-N` | gap between children = `N × 4` px |

See [Layout](layout.md) for how these are actually solved (a real flexbox engine, not margin
emulation).

## Sizing

| Class | Effect |
|---|---|
| `w-N` / `h-N` | width/height = `N × 4` px |
| `w-full` / `h-full` | fill parent |
| `w-auto` / `h-auto` | wrap content |
| `w-px` / `h-px` | exactly 1px |
| `w-1/2`, `w-1/3`, `w-2/3`, … | fraction of parent (resolved by the flex layout) |
| `w-[120px]` / `h-[8]` | arbitrary px (trailing `px` optional) |
| `min-w-N` / `max-w-N` / `min-h-N` / `max-h-N` | min/max constraints (also accept `[…]`) |

A fraction requires `0 < n ≤ d` (else it throws). Fractions on an unbounded axis fall back to
wrap-content.

## Box

| Class | Effect |
|---|---|
| `rounded` | corner radius **6** px (raw) |
| `rounded-none` / `-md` / `-lg` / `-full` | **0 / 10 / 14 / 999** px |
| `rounded-[N]` | arbitrary uniform radius |
| `rounded-<side>-<radius>` | per-corner — see below |
| `border` | 1px border |
| `border-N` | `N` px border (**raw**, e.g. `border-2` = 2px) |
| `p-N` | padding all sides |
| `px-N` / `py-N` | horizontal / vertical padding |
| `pt-N` / `pr-N` / `pb-N` / `pl-N` | single-side padding |
| `opacity-N` | opacity = `N/100` |
| `opacity-[f]` | opacity = raw float `f` |

### Per-corner radii

`rounded-<side>-<radius>` where side ∈ `t b l r tl tr br bl` and radius ∈
`none`(0) `sm`(6) `md`(10) `lg`(14) `full`(999), an arbitrary `[N]`, or a raw integer. `t` = top two
corners, `b` = bottom two, `l` = left two, `r` = right two.

```xml
<div class="rounded-t-lg rounded-b-none">…</div>
```

## Colors

Colors resolve to **theme tokens** or **arbitrary hex** — there is **no standard Tailwind palette**
(`bg-slate-800` throws / is skipped). Arbitrary hex uses the `[#…]` form and accepts `#rgb`,
`#rrggbb`, `#rrggbbaa` (alpha last, web order).

```xml
<div class="bg-surface border-strong">
    <text class="text-accent">token colors</text>
    <text class="text-[#5be49b]">arbitrary hex</text>
</div>
```

### `bg-*` tokens

`bg-base` `bg-surface` `bg-surface-2` `bg-surface-3` `bg-hover` `bg-active` `bg-scrim` `bg-border`
`bg-border-strong` `bg-accent` `bg-accent-hover` `bg-accent-active` `bg-accent-subtle` `bg-success`
`bg-warning` `bg-danger` `bg-info` `bg-success-subtle` `bg-warning-subtle` `bg-danger-subtle`
`bg-info-subtle`.

> **`bg-surface` maps to `surface.1`, not `surface.base`.** Use `bg-base` for the base surface.

### `text-*` tokens

`text-default` `text-muted` `text-subtle` `text-accent` `text-accent-hover` `text-contrast`
`text-danger-contrast` `text-success` `text-warning` `text-danger` `text-info`.

### `border-*` tokens

`border-default` `border-strong` `border-accent` `border-success` `border-warning` `border-danger`
`border-info`. (Plus `border-[#hex]` and `border-<n>` for width.)

See [Theme](theme.md) for the actual token values and how to switch themes.

## Text

| Class | Effect |
|---|---|
| `text-xs` / `-sm` / `-base` / `-lg` / `-xl` / `-2xl` | font size **11 / 12 / 13 / 14 / 16 / 20** |
| `text-left` / `-center` / `-right` | text alignment |
| `font-normal` / `-medium` / `-semibold` / `-bold` | font weight |

> Font weight collapses at apply time — ModernUI text has only NORMAL and BOLD, so `normal`+`medium`
> render NORMAL and `semibold`+`bold` render BOLD. Font sizes are nominal (treated as sp in-game).

## Position

| Class | Effect |
|---|---|
| `absolute` | position absolutely against the containing block's content box |
| `relative` / `static` | static flow (there is no CSS-relative offset — both mean STATIC) |
| `inset-N` | all four edges |
| `top-N` / `right-N` / `bottom-N` / `left-N` | single edge (`N × 4` px, also accept `[…]`) |

```xml
<div class="relative">
    <surface class="absolute inset-0"/>
    <div class="absolute left-3 bottom-3">overlay chip</div>
</div>
```

## State variants — `hover:` `focus:` `active:` `disabled:`

Prefix any utility to make it apply only in that state:

```xml
<div class="bg-surface hover:bg-surface-2 active:bg-surface-3">Hover me</div>
```

- Supported prefixes: `hover:`, `focus:`, `active:`, `disabled:`. Any other prefix (e.g. `md:`)
  throws `unknown variant prefix`.
- `ViewStyles` renders **box** properties (background/border/corner) via a `StateListDrawable` and
  **text color** via a `ColorStateList`. Other properties (size, padding) do **not** vary by state.
- Having any variant automatically makes the element clickable/focusable so the states can fire.
- For disabled styling, prefer a `disabled:` box variant over opacity — opacity isn't state-aware.

## Animation classes

| Class | Effect |
|---|---|
| `animate-spin` | continuous 360° rotation (900ms, linear) |
| `animate-pulse` | alpha pulse 1→0.45→1 (1400ms) |
| `animate-none` | no animation |
| `transition` / `transition-none` | enable/disable layout transitions on a container |

See [Animations](animations.md) for the runtime behavior and lifecycle.

## Raw box attributes (no `class` needed)

For quick, class-free styling (used by the diagnostic demos), the binder understands a handful of raw
attributes. They are **merged over** the `class` (raw attributes win). Note these use **raw px**, not
the ×4 scale.

| Attribute | Meaning |
|---|---|
| `bg="#hex"` | background color |
| `color="#hex"` | text color |
| `pad="N"` | padding all sides, `N` px raw |
| `size="N"` | font size `N` px raw (non-numeric → treated as a component prop, skipped) |
| `w` / `h` = `"full"` \| `"auto"` \| `"<int>"` | fill / wrap / px raw |
| `grow` | flex grow |
| `dir="row"` | row orientation |

```xml
<div bg="#151a2e" pad="16">
    <text size="20" color="#5be49b">no classes here</text>
</div>
```

## `StyleSpec`

`TailwindParser.parse(...)` returns a `StyleSpec` record — an immutable bag where every field is
optional (`null`/`false` = unset). It carries orientation, alignment, gap, wrap, grow/shrink, padding,
corners (uniform + per-corner), border, background, opacity, width/height (`Length`), min/max, position
+ insets, auto-margins, font size/weight, text color/align, animation, transition, and a
`Map<Variant, StyleSpec>` of state variants.

`merged(overlay)` combines two specs — overlay wins per field where set; `grow` and auto-margins are
OR-ed; `wrap`/`transition`/`animation` use non-null so `flex-nowrap`/`transition-none`/`animate-none`
can turn them *off*. This is how raw box attributes overlay a `class`, and how a component's own root
style overlays what the caller passed.

## Gotchas

- **The scale is inconsistent by design.** Tailwind numeric steps are ×4 (`p-4`=16px), but
  `border-<n>` / `rounded-<n>` / `opacity-N` and the raw `pad`/`w`/`h`/`size` attributes are raw.
- **No standard Tailwind palette.** Only theme tokens + `[#hex]`.
- **`bg-surface` ≠ base surface** (it's `surface.1`); use `bg-base`.
- **A missing theme token renders magenta** (`0xFFFF00FF`) rather than crashing — a fast visual tell.
- **`selection-*` / `scrollbar-*` never error**, so typos in those families go unnoticed.
- **Only box + text-color respond to state variants.** Size/padding variants have no effect.

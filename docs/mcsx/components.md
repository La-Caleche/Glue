# Components & Control Flow

This page covers the tags the binder (`ViewBinder`, `view.*`) understands natively — base primitives,
control flow, and the component system (`<import>` / `<slot>` / `<variants>`) — plus the ~30 shipped
`.mcsx` components.

The binder turns `.mcsx` + a controller into a ModernUI View tree **synchronously, on the UI thread**.
The update model is fine-grained reactivity: each `{{binding}}`, reactive class, or two-way `value={}`
and each style application is an `Effect` updating View properties; `<if>`/`<for>`/`<overlay>` rebuild
their subtree in an effect and dispose the nested effects on each toggle. **No diffing.**

## Base tags

**Base tags are raw platform primitives — they carry no design of their own.** The look lives in the
`.mcsx` component library.

| Tag | ModernUI View | Notes |
|---|---|---|
| `<div>` | `FlexLayout` | Default orientation **column**. Full box + flex styling. |
| `<button>` | `FlexLayout` | Default orientation **row**. No intrinsic chrome — see `button.mcsx`. |
| `<text>` | `TextView` | Label from its text-run children; inherits color/size/weight/font from ancestors. |
| `<input>` | `EditText` | `placeholder="…"` → hint; `value={signal}` two-way bind (required). |
| `<scroll>` | `ScrollView` + inner column `FlexLayout` | Children go in the inner flex. |
| `<icon>` | `IconView` | See [Icons](icons.md). |
| `<overlay>` | panel built into `OverlayHost` | See [Overlays](overlays.md). |
| `<key>` | (invisible) shortcut | See [Overlays](overlays.md) §Keybindings. |
| `<if>` / `<for>` | rebuilt subtree | Control flow, below. |
| `<slot>` | caller's children, spliced in | Component composition, below. |
| `<state>` | (nothing) | Declares a local `Signal`, below. |
| `<variants>` / `<case>` | (nothing) | Variant → class config, below. |
| unknown tag | imported component or registered native | Else throws `unknown element <tag>`. |

### Attributes every stylable element understands

- `class` — [utility classes](styling.md), with `{…}` interpolation holes (below).
- Raw box attributes — `bg` `color` `pad` `size` `w` `h` `grow` `dir` (see [Styling](styling.md#raw-box-attributes-no-class-needed)).
- `id="…"` — makes the element addressable (by `@OnClick` and by `<overlay anchor>`).
- `tooltip="…"` — a native ModernUI tooltip (read once, not reactive).
- `disabled="true|false"` — sets the disabled state so `disabled:` variants fire and clicks are
  blocked (read once, **not** reactive).
- `font="namespace:path"` — selects a resource-pack font for text/input and is inherited by
  descendants; see [Fonts and Icons](icons.md).
- `onClick={handler}` — see click wiring below.

### Click wiring precedence

1. `onClick={handler}` binding.
2. else `id="x"` matching a controller method annotated `@OnClick("x")`.
3. else a **state writer**: `toggle={signal}` flips a Boolean; `select={signal} value="x"` writes the
   choice. Both can sit on one element (a menu option picks its value *and* closes the menu).

### Reactive size & drag

- `w={signal}` / `h={signal}` track state — a **`Float`/`Double` is a fraction of the parent (0..1)**,
  an **`Integer` is pixels**. This powers progress fills and sliders.
- `drag={signal} min="0" max="100"` — dragging writes the pointer's X fraction along the element as an
  `Integer` into the signal; `min`/`max` default 0/100.

## Control flow

### `<if cond={…}>`

```xml
<if cond={enabled}>
    <text>Only shown when enabled is true</text>
</if>
```

- **Requires** a `cond={…}` binding.
- Renders into a `FlexLayout` that honors its own `class` (so the block can be a row, carry a gap,
  etc.).
- On each change it disposes the body's effects, clears the views, and rebuilds the children if the
  condition is `Boolean.TRUE`. **Full rebuild, no diff.**

### `<for each={…} as="name">`

```xml
<for each={tabs} as="tab" class="flex-col gap-2">
    <text>{{tab.name}}</text>
</for>
```

- **Requires** a non-empty `as="…"` and an `each={…}` binding.
- `each` must resolve to a `java.util.List` (not an array/Set) or it throws; `null` renders nothing.
- Each item is exposed under the `as` name; the item also feeds 1-arg handlers.
- **No `index` and no `key` syntax — rows are keyed by item value.** On a list change, a new item
  equal (`Objects.equals`) to a previous run's item reuses that row's views, effects and `<state>`
  wholesale; only changed rows rebuild. See [Reactivity](reactivity.md) for the exact semantics.

## Components

### `<import>` and props

```xml
<import name="Button" from="mcsx:components/button"/>
…
<Button variant="destructive" onClick={remove}>
    <text>Delete</text>
</Button>
```

Any tag present in the document's imports is built as a component, its source resolved through the
`DocumentResolver` (classpath). Each call-site attribute becomes a prop:

- **Literal** `prop="x"` → the string `"x"`.
- **Binding** `prop={ref}` → if `ref` names a 0/1-arg controller handler method it's **bound at the
  call site** and forwarded as a `Runnable` (so a `<for>` item still reaches a 1-arg handler);
  otherwise it resolves to its live holder/value.

> The **component's own root element** style sizes and positions it — not the call site. That's why
> `h-9` inside `button.mcsx` sets the button height, and the caller's `class` flows into a `{class}`
> hole instead.

### `<slot/>` — like React `{children}`

```xml
<!-- card.mcsx -->
<div class="flex-col rounded-lg border bg-surface {class}">
    <slot/>
</div>
```

`<slot/>` splices the caller's children **directly** into the parent (built in the caller's scope), so
the parent's gap/orientation/alignment apply to them. It does not wrap them in a container.

### Class interpolation — `{name}` holes

A `class` string may contain `{name}` holes, expanded (in order) to:

1. the classes selected by a matching `<variants on="name">` block, else
2. the string value of a scope prop `name` (`{class}` = the caller's forwarded class; a **bare, absent
   prop contributes nothing** — silent), else
3. a **dotted** `{a.b}` → a controller / `<for>`-item binding (typically a `Computed<String>` of
   classes).

Styling is applied inside an effect. A `{checked}` class hole therefore restyles when its binding
changes, and token colors repaint when the active theme changes. A literal style with no reactive
class or theme dependency runs once and establishes no signal subscription.

### `<variants>` / `<case>` — the cva analogue

```xml
<button class="rounded-md font-medium {variant} {size} {class}">
    <slot/>
    <variants on="variant" default="default">
        <case is="default"     class="bg-accent text-contrast hover:bg-accent-hover"/>
        <case is="destructive" class="bg-danger text-danger-contrast"/>
        <case is="ghost"       class="text-default hover:bg-hover"/>
    </variants>
    <variants on="size" default="default">
        <case is="default" class="h-9 px-4 text-sm"/>
        <case is="sm"      class="h-8 px-3 text-xs"/>
        <case is="icon"    class="w-9 h-9"/>
    </variants>
</button>
```

- `on="dim"` names the dimension; its current value is the scope prop `dim`, or the block's
  `default="…"` if that prop is unset.
- `<case is="value" class="…">` supplies the classes for the `{dim}` hole when the value matches.
- **No wildcard case.** `default=` is a fallback *value*, not a fallback case — if the resolved value
  has no matching `<case>`, it throws `no <case is=…>`. Every reachable value needs a case.
- `<variants>`, `<case>`, `<state>` are configuration and render nothing.

### `<state>` — local UI state

```xml
<state name="open" initial="false"/>
```

Declares a `Signal` local to the subtree (no controller field needed) — handy for a component's own
open/closed state. `initial` is parsed as a Boolean for `"true"`/`"false"`, else a String.

### Handler forwarding

A handler prop resolves by walking the scope chain: a forwarded `Runnable` (bound at the caller) is
used directly; a String is a method name; otherwise it's a controller method (0-arg preferred, then
1-arg receiving the loop item). An unmatched **optional** handler prop is a **no-op** (not an error) —
so a component can accept an `onClick` that the caller didn't pass.

## The shipped component library

Thirty-one overridable `.mcsx` files under `assets/mcsx/ui/components/`. Import any of them by id
(`mcsx:components/<name>`). **Override a shipped component by shipping your own file at the same id.**

| Component | Import id | Key props |
|---|---|---|
| Button | `mcsx:components/button` | `variant` (default/secondary/destructive/outline/ghost/link), `size` (default/sm/lg/icon), `onClick`, `disabled`, `tooltip`, `class` |
| Input | `mcsx:components/input` | `value` (two-way Signal, required), `placeholder`, `class` |
| Checkbox | `mcsx:components/checkbox` | `checked` (Boolean signal), `class`; label in slot |
| Switch | `mcsx:components/switch` | `checked` (Boolean signal), `class` |
| ThemeSwitch | `mcsx:components/theme-switch` | `theme` (display name), `onToggle`, `tooltip`, `class` |
| Radio | `mcsx:components/radio` | `group` (String signal), `value`, `class` |
| Select | `mcsx:components/select` | `value` (String signal), `options` (List), `anchor` (unique id), `class` |
| Slider | `mcsx:components/slider` | `value` (Integer signal), `fraction` (Float computed), `min`, `max`, `class` |
| Progress | `mcsx:components/progress` | `fraction` (Float 0..1), `class` |
| Dialog | `mcsx:components/dialog` | `open` (Boolean signal), `onClose`, `class` |
| Popover | `mcsx:components/popover` | `open`, `anchor`, `onClose`, `class` |
| Spinner | `mcsx:components/spinner` | `size`, `class` |
| Skeleton | `mcsx:components/skeleton` | `class` (give it `w-`/`h-`) |
| Card (+ Header/Title/Description/Content/Footer) | `mcsx:components/card…` | `onClick`, `class` |
| Alert (+ Title/Description) | `mcsx:components/alert…` | `variant` (default/destructive/success/warning/info), `class` |
| Avatar | `mcsx:components/avatar` | `size` (sm/default/lg), `class`; initials in slot |
| Badge | `mcsx:components/badge` | `variant` (default/secondary/destructive/outline/success/warning/info), `class` |
| Tag | `mcsx:components/tag` | `class` |
| Kbd | `mcsx:components/kbd` | `class` |
| Label | `mcsx:components/label` | `class` |
| MenuItem | `mcsx:components/menu-item` | `onClick`, `variant` (default/destructive), `class` |
| NavItem | `mcsx:components/nav-item` | `active` ("true"/"false"), `onClick`, `class` |
| Panel | `mcsx:components/panel` | `class` |
| Separator | `mcsx:components/separator` | `orientation` (horizontal/vertical), `class` |

### Authoring patterns, from the shipped files

**Reactive variant + `<if>` + `<icon>`** (`checkbox.mcsx`):

```xml
<div toggle={checked} class="flex-row items-center gap-2 {class}">
    <div class="w-4 h-4 rounded border items-center justify-center transition {checked}">
        <variants on="checked" default="false">
            <case is="false" class="border-strong bg-surface-3"/>
            <case is="true"  class="border-accent bg-accent"/>
        </variants>
        <if cond={checked} class="transition">
            <icon name="check" size="12" class="text-contrast"/>
        </if>
    </div>
    <slot/>
</div>
```

**`<state>` + anchored `<overlay>` + `<for>` + selection** (`select.mcsx`):

```xml
<div class="flex-col {class}">
    <state name="open" initial="false"/>
    <div id={anchor} toggle={open} class="flex-row items-center justify-between h-9 w-full rounded-md border bg-surface-3 px-3">
        <text class="text-sm text-default">{{value}}</text>
        <icon name="chevron-down" size="14" class="text-muted"/>
    </div>
    <overlay open={open} anchor={anchor} class="flex-col gap-1 min-w-[180px] rounded-md border bg-surface-2 p-1">
        <for each={options} as="option" class="flex-col gap-1">
            <div select={value} value={option} toggle={open} class="flex-row items-center justify-between rounded px-3 py-2 hover:bg-hover">
                <text class="text-sm text-default">{{option}}</text>
                <if cond={selected} class="transition"><icon name="check" size="14" class="text-accent"/></if>
            </div>
        </for>
    </overlay>
</div>
```

**Drag gesture + reactive fill** (`slider.mcsx`):

```xml
<div drag={value} min={min} max={max} class="w-full h-4 flex-row items-center {class}">
    <div class="h-1 rounded-full bg-accent" w={fraction}/>
    <div class="w-3 h-3 rounded-full bg-[#FFFFFF]"/>
    <div class="h-1 grow rounded-full bg-surface-2"/>
</div>
```

## Gotchas

- **`<if>`/`<overlay>` are full rebuilds** — no diff; child identity doesn't survive a toggle. `<for>`
  reuses rows whose item value survived the change, but a *changed* row rebuilds wholesale, and even a
  reused row's focus/scroll position doesn't survive.
- **`<for each>` must be a `java.util.List`** or it throws at runtime.
- **`<variants>` has no fallback case** — every reachable value needs a `<case is>`; `default=` is a
  fallback value only.
- **`disabled` and `tooltip` are read once, not reactive.** Style disabled state with a `disabled:`
  variant, not opacity.
- **`w=`/`h=` mean different things bound vs literal** — a bound `Float` is a fraction, a bound
  `Integer` is pixels; a literal is a static Length. Mixing them silently mis-sizes.
- **`<input>` is controlled only** — `value={signal}` is required and two-way; a non-Signal value binds
  read-only.
- **A `{class}` hole with no prop passed contributes nothing** (silent) — so shared components can
  accept an optional `class`.

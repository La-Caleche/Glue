# MCSX Wiki

**MCSX** is a standalone, independent Minecraft UI library for **Minecraft 1.21.8 · Fabric**. It lets
you author reactive game UIs as **`.mcsx` documents** (an HTML/JSX-like language) driven by **Java
controllers** on a **fine-grained signals framework**, styled with a **Tailwind-subset utility-class
engine**, bound to **ModernUI View trees**, and rendered in-game through MCSX's own self-hosted
ModernUI-on-Minecraft runtime.

It is **not** a browser, has **no** JS engine, and does **no** VDOM/diffing. The mental model is
*React + Tailwind + shadcn primitives, rebuilt natively for Minecraft* — `.mcsx` + signals ≈ React,
utility classes ≈ Tailwind, base tags + a component library ≈ shadcn.

## Table of Contents

1. [Getting Started](getting-started.md)
2. [The `.mcsx` Language](mcsx-language.md)
3. [Controllers](controllers.md)
4. [Reactivity (Signal / Computed / Effect)](reactivity.md)
5. [Styling (Tailwind subset)](styling.md)
6. [Tailwind Compatibility](tailwind-compatibility.md)
7. [Theme & Tokens](theme.md)
8. [Layout (Flexbox)](layout.md)
9. [Components & Control Flow](components.md)
10. [Native Components](native-components.md)
11. [Icons](icons.md)
12. [Animations & Transitions](animations.md)
13. [Overlays & Keybindings](overlays.md)
14. [External GPU Surfaces & Cursors](surfaces.md)
15. [Dockspace & Embedded Game Viewport](dockspace.md)
16. [Runtime, Threading & Lifecycle](runtime.md)
17. [Linting & Testing](testing.md)

## The five things MCSX owns

| Layer | Package | What it is |
|---|---|---|
| **Reactive graph** | `core.reactive` | `Signal` / `Computed` / `Effect` — the update model |
| **`.mcsx` parser** | `core.mcsx` | tokenizer + recursive-descent parser + AST + `DocumentLoader` |
| **Controllers** | `core.controller` | `ScreenController`, `@UIController`, `@OnClick` |
| **Styling / layout / theme** | `core.style`, `core.layout`, `core.theme` | Tailwind-subset → `StyleSpec`, flexbox solver, design tokens |
| **Binder + host** | `view.*`, `mui.*`, `surface.*`, `cursor.*` | `.mcsx` → ModernUI `View` tree, mounted and rendered in-game |

## Design rules that shape everything

- **Bindings stay dumb.** `{name}` (attributes) and `{{name}}` (text) are field/method *references*,
  never expressions. All derived/conditional logic lives in `Computed` controller fields.
- **Fail loud.** Unknown tags, attributes, classes, and bindings throw with a 1-based line/column at
  parse, lint, or bind time. No silent fallbacks.
- **`core.*` is pure.** The reactive graph, parser, styling, and theme are free of Minecraft /
  ModernUI / Arc3D types — which is what keeps them headless-unit-testable. ModernUI types appear
  only from `view.*` down.
- **MCSX ships no styled widgets of its own in Java.** Base tags are raw platform primitives; the
  design lives in the overridable `.mcsx` component library, and anything markup can't express is a
  consumer-registered native component.

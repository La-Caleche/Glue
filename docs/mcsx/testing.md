# Linting & Testing

MCSX's `core.*` is pure `java.*` and fully unit-tested; everything from `view.*` down needs a live
ModernUI `Context` + GL context and verifies in-game only. The **screen linter** bridges that gap — it
statically checks a `.mcsx` document against its controller at build time, catching the errors that
would otherwise only surface when you open the screen.

## What's unit-tested headlessly

- `core.reactive` — the full signals algorithm (equal-write no-op, lazy computed, dynamic re-tracking,
  the self-write guard, diamond convergence).
- `core.mcsx` — the tokenizer/parser grammar (imports, self-closing tags, bindings, interpolation,
  whitespace, comments, and that every malformed input throws with a sane 1-based line/column).
- `core.style` / `core.layout` — the Tailwind vocabulary and the flexbox solver.
- `core.theme` — palette completeness, immutable overrides, active-theme reactivity, and equal-write
  behavior.
- `core.lint` — the linter itself, via `ScreenLintTest`.

## The screen linter — `McsxLinter`

```java
static List<String> lint(McsxDocument document, Class<?> controller);
static List<String> lint(McsxDocument document, Class<?> controller, Set<String> nativeTags);
```

Returns human-readable problem strings (`"line L, column C <tag>: message"`); an empty list means
clean. Pass `controller == null` to lint a component file (which has no controller) — it then checks
classes only.

### What it validates

- **Unknown tags** — anything that isn't a base tag
  (`div, button, text, input, scroll, icon, if, for, slot, overlay, key, state, option`), isn't an
  `<import>`, and isn't in the supplied `nativeTags` set is reported. (This is how a deleted or
  misspelled component tag gets caught.)
- **Classes** — every literal `class` token (with `{holes}` stripped) must parse via
  `TailwindParser.parseStrict`; failures are reported.
- **Handler attributes** (`onClick`, `onPress`, `onClose`) — must name a controller method taking 0 or
  1 args, a field, or a `<for>` variable in scope (or a forwarded prop when the controller is null).
- **Imported component props** may forward a 0/1-argument controller method under any prop name, such
  as `<ThemeSwitch onToggle={cycleTheme}/>`; this mirrors runtime handler forwarding.
- **`{ref}` / `{{text}}` bindings** — the **head segment** must be a `<for>` loop variable in scope or
  a controller field (walking superclasses). Deeper path segments are intentionally **not** checked
  (their types are only known at runtime).

Literal-only attributes (`as`, `is`, `on`, `name`, `from`, `initial`, `combo`, `placement`, `modal`)
are skipped, and the config tags `variants`/`case`/`state` render nothing so they're not checked as
elements.

### How it runs

`ScreenLintTest` (`src/test/…/lint/`) runs the linter over:

- every shipped component in `assets/mcsx/ui/components` (controller `null`), and
- every demo screen in the testmod against its real controller,

mirroring the testmod's file→controller map and its native-tag set (`Set.of("surface")`). It's part of
the `test` task, so a broken class or an unresolvable binding **fails the build** — the build-time
stand-in for `view.*` having no unit tests. Regression tests confirm it catches a missing handler, an
invalid class, a deleted component tag, and an out-of-scope loop variable.

### Linting your own screens

Add a test that lints your documents against your controllers, and pass your native tags so they
aren't reported as unknown:

```java
McsxDocument doc = DocumentLoader.loadFromClasspath("mymod:hello");
List<String> problems = McsxLinter.lint(doc, HelloController.class, Set.of("gauge", "surface"));
assertTrue(String.join("\n", problems), problems.isEmpty());
```

## The verify gate

```
./gradlew compileJava compileTestmodJava test checkstyleMain checkstyleTest checkstyleTestmod
```

- Compiles main + testmod.
- Runs the `core.*` unit tests and `ScreenLintTest`.
- Runs Checkstyle for authored main, test, and testmod code. Vendored packages (`mui/**`, `surface/**`,
  `cursor/**`) are LGPL and excluded — don't reformat them.

## Theme and Tailwind stress screen

The testmod includes a live integration screen covering Obsidian, Frost, and an Aurora theme derived
through selective overrides:

```text
/mcsx ui theme_stress
```

It exercises:

- live token repaint without rebuilding the screen;
- surface, text, accent, border, and status tokens;
- a custom `Theme.withOverrides(...)` palette;
- the reusable `mcsx:components/theme-switch` component;
- flex direction/wrap/alignment/distribution, gap, grow, and shrink;
- fixed, fractional, min/max, and absolute/inset sizing;
- padding, opacity, borders, per-corner radii, typography, and state variants;
- spin/pulse animation, two-way input, and writable signal retention;
- arbitrary hex colors that intentionally remain fixed across themes.

Runtime checks:

1. Type into the input, toggle the switch, and focus/hover/press controls.
2. Cycle Obsidian → Frost → Aurora → Obsidian repeatedly.
3. Confirm text/input state survives and token colors update in place.
4. Confirm arbitrary-hex swatches do not change.
5. Look for loud magenta, which means a theme token is missing.

The headless suite lints this document against `ThemeStressController`, but only an in-game launch can
verify ModernUI state drawables, animation, focus, and rendered contrast.

## What the gate does *not* prove

The binder, styling application, host, and rendering are **compile-verified and lint-verified only** in
the build. Their runtime is proven by launching the client (`testmodClient` → `/mcsx ui <file>`) and
exercising the UI. When you report a change as done, be explicit about which half you verified:

- ✅ *"unit tests + linter green"* — the logic and the document/controller contract hold.
- 🔍 *"needs an in-game launch"* — anything visual/interactive from `view.*` down.

## Gotchas

- **The linter checks the head segment of a binding, not the full path.** `{{task.display}}` verifies
  `task` is in scope but not that `display` exists — a deep-path typo still surfaces in-game.
- **Icon names aren't linted.** An unknown `<icon name="…">` in the selected font descriptor throws
  at bind time, not at lint time.
- **`.mcsx` typos never fail `compileJava`** — they're data. The linter is what turns them into a
  build failure, so keep your screens covered by a `ScreenLintTest`-style test.

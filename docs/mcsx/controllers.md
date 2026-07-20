# Controllers

A **controller** is the Java half of a screen: one `ScreenController` subclass drives one `.mcsx`
document. It holds the reactive state as fields and the event handlers as methods. The binder reaches
into it by name via reflection, so `{count}` in the markup finds a `count` field and `onClick={ping}`
finds a `ping` method.

All types live in `fr.lacaleche.glue.mcsx.core.controller` (pure `java.*`).

## `ScreenController`

```java
public abstract class ScreenController {
    protected final <T> Signal<T>   signal(T initial);            // → new Signal<>(initial)
    protected final <T> Computed<T> computed(Supplier<T> fn);     // → new Computed<>(fn)
}
```

These two `protected final` helpers are the whole base class — conveniences so subclasses don't
import the reactive package directly. There are **no lifecycle hooks and no `effect(...)` helper**;
effects are created by the framework, not by controllers. See [Reactivity](reactivity.md) for the
semantics of `Signal` and `Computed`.

## Declaring state

Reactive state is declared as `Signal`/`Computed` **fields**. Plain (non-reactive) fields are bound by
name too — useful for the list backing a `<for>`. Fields and methods can be `private final`; the
binder calls `setAccessible(true)`.

```java
@UIController("mcsx:demo")
public final class DemoController extends ScreenController {

    private final Signal<Integer> count   = signal(0);
    private final Signal<Boolean> enabled = signal(true);
    private final Signal<Integer> volume  = signal(50);
    private final Signal<String>  choice  = signal("a");
    private final List<String>    choices = List.of("a", "b");

    private final Computed<Float>  volumeFraction = computed(() -> volume.get() / 100f);
    private final Computed<String> label = computed(() ->
            "Count: " + count.get() + "   |   volume " + volume.get()
                    + (enabled.get() ? "   |   on" : "   |   off") + "   |   " + choice.get());

    private void ping() {
        count.update(n -> n + 1);
    }
}
```

- `{{label}}` / `{{count}}` resolve to the field of that name; a `Signal`/`Computed` is unwrapped to
  its current value.
- `each={choices}` feeds the `<for>` from the plain `List` field.
- Handler methods mutate signals — `count.update(...)`, `enabled.set(...)`. Derived display values
  (like `label`) are `Computed`, never expressions in the markup.

## `@UIController`

```java
@Retention(RUNTIME) @Target(TYPE)
public @interface UIController { String value(); }
```

`value()` is the document id `"namespace:path"` this controller drives — e.g. `@UIController("mcsx:demo")`
maps to `assets/mcsx/ui/demo.mcsx`.

> **Note:** `@UIController` is documentation/convention. Nothing in MCSX scans it to auto-open a
> screen — you build the `McsxFragment` with an explicit controller instance (see
> [Getting Started](getting-started.md) and [Runtime](runtime.md)). It exists so the id lives next to
> the controller and so tooling (the [linter](testing.md), the IDE plugin) can pair them.

## `@OnClick`

```java
@Retention(RUNTIME) @Target(METHOD)
public @interface OnClick { String value(); }
```

Marks a method as the click handler for the element whose `id` attribute equals `value()`. This is the
alternative to an inline `onClick={method}` binding — use whichever reads better.

```java
@OnClick("saveButton")
private void save() { … }
```
```xml
<button id="saveButton"><text>Save</text></button>
```

## Binding resolution (the author-facing contract)

When the binder resolves a `{ref}` / `{{ref}}` / `onClick={name}`, it walks:

1. **Scope first** — a `<for>` loop variable or a component prop of that name (see
   [Components](components.md)).
2. **Then the controller** — a field (for state) or a method (for handlers), reached by reflection.

Details worth knowing:

- **Dotted paths** (`task.display`) navigate segment by segment: a zero-arg **method** of that name,
  else a **field**, with `Signal`/`Computed` unwrapped at the leaf.
- **Handlers** are controller methods. A **0-arg** method is preferred; a **1-arg** method receives
  the current `<for>` item, so one handler can serve a whole list (`selectTab(TabEntry entry)`).
- Unresolved references throw **`McsxBindException`** (`core.bind`), a `RuntimeException` carrying an
  optional 1-based line/column:

  ```java
  public final class McsxBindException extends RuntimeException {
      public McsxBindException(String message);
      public McsxBindException(String message, int line, int column); // appends " at line L, column C"
  }
  ```

Because resolution is name/reflection-based, **a typo in the markup is a runtime error, not a compile
error.** The [screen linter](testing.md) catches most of these at build time.

## Nested item classes (per-row state)

A `<for>` row often needs its own derived state. Model each item as a small class exposing **public**
accessors — the binder reads a nested item's members through public getters (top-level controller
fields can stay private, but nested-class members must be reachable):

```java
public final class TabEntry {
    private final String name;
    private final Computed<String>  classes;
    private final Computed<Boolean> active;

    TabEntry(String name, Signal<String> selection) {
        this.name    = name;
        this.classes = computed(() -> name.equals(selection.get())
                ? "bg-accent-subtle text-accent border border-accent"
                : "text-muted hover:bg-hover");
        this.active  = computed(() -> name.equals(selection.get()));
    }

    public String           name()    { return name; }
    public Computed<String> classes() { return classes; }
    public Computed<Boolean> active() { return active; }
}
```

Used as:

```xml
<for each={leftTabs} as="tab">
    <div class="rounded px-3 py-1 {tab.classes}">
        <text>{{tab.name}}</text>
    </div>
</for>
```

`{tab.classes}` reads the per-row `Computed<String>` reactively — when `selection` changes, only the
affected rows restyle.

## Gotchas

- **Bindings are references, not code.** You cannot write `{count > 0}` or `{a + b}`. Move the logic
  into a `Computed` field.
- **State fields can be private; nested-item members can't.** The binder reflects into private
  controller fields, but reads nested item state through public accessor methods.
- **Handler arity matters.** 0-arg wins over 1-arg; a 1-arg handler gets the current loop item.
- **Everything is single-threaded.** Signals are read/written on the UI thread only — see
  [Runtime](runtime.md) §Threading.

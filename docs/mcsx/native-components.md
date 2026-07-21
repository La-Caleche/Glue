# Native Components

A **native component** is a Java ModernUI `View` registered under a custom tag. It's the escape hatch
for anything markup can't express — a 3D viewport, a chart, a curve editor, a live GPU surface. The
`.mcsx` component library covers the standard UI kit; native components cover the rest.

MCSX ships **zero** native components. Everything that used to be a Java widget (checkbox, switch,
radio, select, progress, slider) is now a `.mcsx` file. The registry exists purely as your extension
point.

## `ComponentRegistry`

```java
public final class ComponentRegistry {
    public ComponentRegistry register(String tag, NativeComponent component); // chainable
    public NativeComponent get(String tag);
    public boolean         has(String tag);
}
```

Build one, register your tags, and pass it into the `McsxFragment`. `.mcsx` `<import>` components do
**not** go here — they're resolved by the `DocumentResolver`. Only unknown tags fall through to the
registry.

```java
ComponentRegistry registry = StandardComponents.create()   // an empty registry
        .register("surface", (context, element, binder) -> {
            DemoSurfaceSource source = new DemoSurfaceSource();
            ExternalSurfaceView view = new ExternalSurfaceView(context, source);
            view.setGestureListener(source);
            return view;
        });
```

`StandardComponents.create()` returns an empty registry (and `registerInto(registry)` adds nothing) —
it's a convenience starting point.

## `NativeComponent`

```java
@FunctionalInterface
public interface NativeComponent {
    View create(Context context, McsxElement element, ViewBinder binder);
}
```

You get the ModernUI `Context`, the `McsxElement` for your tag (its attributes and children), and the
`ViewBinder` — which is how you reach bindings, handlers, effects, and child Views.

## The `ViewBinder` surface for natives

These public `ViewBinder` methods are your API inside `create(...)`:

```java
Context         context();                                  // the ModernUI Context
void            addEffect(Effect effect);                   // register an effect tied to this subtree
View            buildView(McsxElement element);             // build a child element into a View

Supplier<Object> resolveBinding(String ref, McsxElement el);// {ref} → live value (dotted paths ok, tracks deps)
Signal<?>        resolveSignal (String ref, McsxElement el);// {ref} → raw writable Signal, or null if read-only
Runnable         resolveHandler(String name, McsxElement el);// onX={name} → controller invocation (throws if none)

StyleSpec       resolveStyle(McsxElement el);               // interpolated class + raw box attrs, merged
View            buildStyledContainer(McsxElement el, StyleSpec base, StyleSpec.Orientation defaultOrientation);
```

- **`resolveBinding` captures the current scope.** The returned supplier can safely run later from an
  effect, including for bindings declared inside components and loops.
- **Register your effects with `addEffect`** so they are disposed with the containing conditional,
  loop, overlay, or screen.

### Example: a reactive native

```java
registry.register("gauge", (context, element, binder) -> {
    GaugeView view = new GaugeView(context);
    Supplier<Object> value = binder.resolveBinding("value", element);   // e.g. value={progress}
    binder.addEffect(Effect.of(() -> {
        Object v = value.get();                                          // tracks the signal
        view.setValue(v instanceof Number n ? n.floatValue() : 0f);
    }));
    return view;
});
```

```xml
<gauge value={progress}/>
```

## Lifecycle & threading

- `create(...)` runs **on the UI thread**, synchronously, during binding. Don't do slow or GPU work
  here — and don't allocate GPU resources in a constructor (a native may be built headlessly, e.g.
  under the [linter](testing.md)); allocate lazily on first render.
- Any `ValueAnimator` or background resource you start must be **cancelled/released on dispose** — tie
  it to an effect's `onDispose`, or it leaks against a detached view. (MCSX does this for `animate-*`
  classes automatically.)
- Tell the linter about your tags: pass them as the `nativeTags` set so `<gauge>` isn't reported as an
  unknown tag. See [Testing](testing.md).

## When to reach for a native vs a `.mcsx` component

| Use a `.mcsx` component | Use a native component |
|---|---|
| Composition of existing tags/classes | Custom drawing / a `Canvas` |
| Variant-driven styling | An external GPU texture ([surfaces](surfaces.md)) |
| Standard UI (buttons, cards, menus) | Bespoke gestures beyond click/drag |
| Anything expressible in markup | 3D viewport, chart, timeline, curve editor |

Prefer a `.mcsx` component whenever the markup can express it — it's overridable, themeable, and needs
no Java. Drop to a native only for what markup genuinely can't do.

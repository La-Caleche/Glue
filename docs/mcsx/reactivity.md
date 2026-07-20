# Reactivity — Signal / Computed / Effect

MCSX's update model is a **synchronous, single-threaded, push-invalidate / pull-recompute** signals
graph (SolidJS/Svelte-flavored). There is no scheduler, no batching, and no thread safety. A signal
write invalidates exactly the observers that read it; each `{{binding}}` / reactive class / two-way
`value={}` is one `Effect` mutating one View property. **There is no diffing and no reconciler.**

All types live in `fr.lacaleche.glue.mcsx.core.reactive` (pure `java.*`, fully unit-tested). As a
controller author you use `Signal` and `Computed` (via `ScreenController`'s `signal(...)` /
`computed(...)`); `Effect` is created by the binder.

## `Signal<T>` — the mutable root

```java
public final class Signal<T> implements Source {
    public Signal(T initial);
    public T    get();                     // reads + tracks a dependency
    public void set(T newValue);           // writes + invalidates observers
    public void update(UnaryOperator<T> f);// set(f.apply(current))
}
```

- **`get()` always tracks.** Reading inside an effect/computed subscribes that observer. There is
  **no `peek()`** — no untracked read.
- **Equal writes are a no-op.** `set` compares with `Objects.equals`; an equal write does not
  propagate. Mutating a collection in place and re-setting the *same reference* will not fire — set a
  new/unequal value.
- `set` notifies observers over a **copy** of its observer set, because re-running observers
  re-subscribe and mutate it.

```java
count.set(5);
count.update(n -> n + 1);
enabled.update(on -> !on);
```

## `Computed<T>` — derived, lazy, dynamic

```java
public final class Computed<T> implements Source, Observer {
    public Computed(Supplier<T> fn);
    public T get();
}
```

- **Lazy recompute.** The supplier does not run until something reads the computed; the value is
  cached until a dependency invalidates it.
- **Eager invalidate.** When a dependency changes, the computed is marked stale and tells its
  downstream observers immediately — but the actual recompute waits for the next `get()`.
- **Dynamic dependencies.** Dependencies are re-tracked on every recompute, so a computed that reads a
  signal on only one branch automatically narrows/widens its subscription set.

```java
private final Computed<Float>  volumeFraction = computed(() -> volume.get() / 100f);
private final Computed<Boolean> canSubmit      = computed(() -> !name.get().isBlank() && agreed.get());
```

## `Effect` — the bridge to the View tree

```java
public final class Effect implements Observer {
    public static Effect of(Runnable body);   // constructs AND runs the body once
    public void invalidate();
    public void onDispose(Runnable callback);
    public void dispose();
}
```

You rarely create effects directly — the binder does. What matters:

- **Runs once on creation**, then **synchronously re-runs** on any dependency change.
- **An effect must not write a signal it read during the same run.** Doing so throws
  `IllegalStateException("effect invalidated while running — an effect must not write its own
  dependencies")`. Derive with a `Computed` instead of writing back.
- **`onDispose(cb)` holds a single callback** (last registration wins), fired exactly once via
  `dispose()`. `<if>`/`<for>`/`<overlay>` use it to tear down nested effects and cancel animators.
- **`dispose()` is idempotent** — unsubscribes from all sources and runs the teardown once.

## `<for>` rows — reused by item value

`<if>`/`<overlay>` rebuild their whole subtree on every toggle. `<for>` does not: rows are **keyed
by item value** (`Objects.equals`). On a list change, each new item claims the first still-unclaimed
old row with an equal item — greedily, in order — and takes over that row's Views, effects and
`<state>` signals wholesale; only unmatched old rows are torn down, and only new items build. List
items are typically records, so value equality is the natural key. There is still **no diffing
inside a row**, and no `key`/`index` syntax.

- **An unchanged item's row survives the mutation.** Appending a row to a list of Selects no longer
  closes every open menu — the untouched rows keep their `<state>` and their View identity.
- **A replaced item rebuilds just its own row.** Value semantics: an edited record is unequal to its
  predecessor, so no old row matches it, and that row's `<state>` resets — the others don't.
- **In-place mutation of a mutable item keeps the row** — the same object is trivially equal. (But
  remember: an equal write to the *list* signal doesn't propagate at all.)
- **Duplicate items degrade to positional matching.** Equal items are indistinguishable, so the
  first duplicate claims the first equal row, the second the next, and so on.
- **Focus does not survive a list change**, even for a reused row: reordering re-adds the same View
  instances, and a removed-then-re-added View loses focus like any other.

## Semantics you must internalize

| Behavior | Consequence |
|---|---|
| Equal-write no-op (`Objects.equals`) | In-place mutation of a stored collection won't propagate. |
| Lazy `Computed` | Never rely on a computed's supplier firing before it's read. |
| Dynamic re-tracking | Conditional reads correctly change the live dependency set. |
| Single-threaded, no locks | All reactive reads/writes must be on one thread (the UI thread). |
| No batching | Multiple writes each propagate immediately and synchronously. |
| No glitch avoidance | A diamond can re-run an effect more than once per write. |

### The diamond glitch (accepted)

If an effect depends on **both** a signal `a` and a `Computed` `b = a * 2`, a single write to `a` can
re-run the effect **more than once**. The **last** run always observes the fully-settled graph, so the
result is correct — the cost is redundant work, never a stale value. Do not rely on effect run counts.

## Gotchas

- **No `peek()`.** Every `get()` tracks. If you need a value without subscribing, you can't — rest
  structure the code so the read happens outside the reactive context.
- **Never write a dependency from within its own effect/computed.** It throws immediately.
- **Equal writes are silent.** Store immutable values in signals, or always `set` a fresh instance.
- **`Computed` is lazy and side-effect-free by contract.** Don't put side effects in a computed — put
  them in a handler.
- **Single thread only.** The runtime is a static stack with no synchronization; mutate signals on the
  UI thread. See [Runtime](runtime.md) §Threading.

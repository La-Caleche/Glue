---
title: Reactivity
description: Bind Probe Inspector state and mirror Minecraft client data without crossing thread ownership.
artifact: glue-mcsx
modId: glue-mcsx
environment: client
---

# Reactivity

MCSX uses a small synchronous value model for native View properties. A `Value<T>` is read-only, a
`Signal<T>` is mutable, and a `Subscription` owns one listener or binding.

## Outcome

The Probe Inspector validates its draft immediately and shows a live player-position snapshot without
reading Minecraft state from ModernUI's UI thread.

## Bind the First Value

Keep mutable form state in signals, derive presentation state with `map(...)`, and pass values to
component properties:

```java
Signal<String> name = Signal.of("");
Value<Boolean> valid = name.map(value -> value.trim().length() >= 3);
Value<String> status = valid.map(ready ->
        ready ? "Probe is ready" : "Enter at least 3 characters");

Column form = ui.column(
        ui.literalField("Probe name").text(name),
        ui.text(status),
        ui.literalButton("Apply", () -> name.set(name.get().trim()))
                .enabled(valid)
);
```

Bindings read the current value immediately, subscribe while attached, and close on detach. Prefer a
component binding over a manual listener for View properties.

## Respect the Two Threads

ModernUI's UI thread and Minecraft's client thread are separate owners. This boundary applies as soon
as the first binding exists:

| Operation | Required thread |
| --- | --- |
| Read, write, map, combine, or subscribe to normal MCSX values | ModernUI UI thread |
| Mutate MCSX components, themes, or styles | ModernUI UI thread |
| Read or mutate Minecraft client state | Minecraft client thread |
| Call `Signal.postSet(...)` | Any thread |
| Call `ClientMirror.get()` or `ClientMirror.set(...)` | Minecraft client thread |
| Bind or subscribe to `ClientMirror.value()` | ModernUI UI thread |

Control handlers already run on the UI thread. Schedule client-owned work with Minecraft's executor:

```java
ui.literalButton("Capture player position", () ->
        Minecraft.getInstance().execute(this::captureOnClientThread));
```

## Mirror Client State

`ClientMirror<T>` is the focused bridge for state owned and updated by Minecraft but displayed by
ModernUI. Publish immutable snapshots, then bind the UI-side `Value`.

```java [ProbePositionMirror.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.mcsx.client.reactive.ClientMirror;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class ProbePositionMirror {

    private final ClientMirror<ProbeSnapshot> snapshot = ClientMirror.of(
            new ProbeSnapshot("No world", 0, 0, 0)
    );

    public void capture(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) return;

        BlockPos position = player.blockPosition();
        this.snapshot.set(new ProbeSnapshot(
                player.level().dimension().location().toString(),
                position.getX(),
                position.getY(),
                position.getZ()
        ));
    }

    public Value<String> label() {
        return this.snapshot.value().map(value ->
                value.dimension() + " at " + value.x() + ", " + value.y() + ", " + value.z());
    }

    public record ProbeSnapshot(String dimension, int x, int y, int z) {
    }
}
```

Use it from the inspector on the two correct threads:

```java
private final ProbePositionMirror position = new ProbePositionMirror();

// Inside create(Ui), on the ModernUI UI thread:
View positionUi = ui.column(
        ui.text(this.position.label()),
        ui.literalButton("Use player position", () -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> this.position.capture(client));
        })
);
```

`get()` and `set(...)` reject non-client callers. `value()` exposes the UI-thread copy. Equal values
are suppressed before crossing, and a changed value crosses once. Both threads receive the same
object reference, so records and immutable collections are appropriate; mutable snapshots are not.

## Lifecycle and Ownership

Let components own subscriptions whenever possible. A manual `Subscription` is for a non-property
side effect and must be closed by its UI-thread owner:

```java
renderStatus(status.get());
Subscription subscription = status.subscribe(this::renderStatus);

// Later, on the UI thread:
subscription.close();
```

Subscriptions close idempotently. For a custom canvas View, extend `ReactiveView` and call
`invalidateOn(values...)`, usually in the constructor. It subscribes on attach, invalidates on each
change, closes on detach, and resubscribes after reparenting. Overrides of
`onAttachedToWindow()` or `onDetachedFromWindow()` must call `super`.

A custom `Value` implementation must also notify listeners and close subscriptions on the UI thread,
and every returned subscription must be idempotent.

::: details Signal mutation and dispatch ordering
Create a signal with `Signal.of(initial)` or `new Signal<>(initial)`. `set(...)` and `update(...)`
apply immediately on the UI thread. Values equal by `Objects.equals(...)` are suppressed.

```java
Signal<Integer> count = Signal.of(0);
count.set(1);
count.update(value -> value + 1);
```

Dispatch is synchronous and FIFO, including writes made reentrantly by a listener. A listener closed
during dispatch does not receive a pending callback. If a listener throws, remaining listeners and
queued notifications still run; the first failure is rethrown with later failures suppressed.

`subscribe(...)` observes later changed values and does not emit the current value initially. Read
`get()` explicitly when a manual consumer needs initialization.

Use `postSet(...)` only when another thread must publish to a UI-owned signal. On the UI thread it acts
like `set(...)`; elsewhere it posts in FIFO order with other UI work. With no UI thread, as in a
headless unit test, it applies immediately.
:::

::: details Map, combine, and boolean helpers
`map(...)` suppresses equal mapped results. `Value.combine(...)` accepts two or three sources:

```java
Value<Boolean> canApply = Value.combine(
        name.map(value -> !value.isBlank()),
        enabled,
        (hasName, isEnabled) -> hasName && isEnabled
);

Value<String> range = Value.combine(
        minimum,
        current,
        maximum,
        (min, value, max) -> min + " / " + value + " / " + max
);
```

A combined value is always readable. While observed, it subscribes once to each distinct source and
recomputes from a current snapshot of every operand. Repeating one source, or combining derived values
with one upstream, does not expose mixed snapshots.

`Values.constant(value)` never notifies. `Values.not(value)`, `Values.all(values...)`, and
`Values.any(values...)` provide boolean folds. Empty `all()` is constantly true; empty `any()` is
constantly false.
:::

::: details Null, rollback, and two-way limits
A generic `Value<T>` may contain null. Component properties, scope values, keyed items, and keyed ids
reject null where their contracts require a concrete value. Mapping is one-way. The provided writable
bindings specifically require `Signal<String>` for `TextField.text(...)` and `Signal<Boolean>` for
`Checkbox.checked(...)`; there is no general writable mapped value.

Subscription acquisition is transactional in component bindings, combined values, and
`ReactiveView`. If a later source fails to subscribe, already acquired subscriptions close before
the failure escapes, allowing a later attachment to retry.
:::

## Next Steps

- [Bind the values to the full form](./components.md) if you started from the thread model.
- [Use reactive theme scopes](./themes-and-styles.md) for a live light/dark preview.
- [Show a mirrored value in a HUD](./overlays.md) with mount-owned cleanup.
- [Retain the inspector in Dockspace](../dockspace/layout-and-panes.md) while tabs move around it.

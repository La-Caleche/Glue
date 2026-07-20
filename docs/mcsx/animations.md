# Animations & Transitions

MCSX has two motion mechanisms, both driven by [utility classes](styling.md#animation-classes):

- **Looping animations** — `animate-spin` / `animate-pulse` on an element.
- **Layout transitions** — `transition` on a container, so children fade and move to new positions.

Both are handled by `view.Animations` and wired by the binder. Overlays additionally get a fixed
entrance animation.

## Looping animations — `animate-*`

| Class | Effect | Duration / curve |
|---|---|---|
| `animate-spin` | rotate 0° → 360°, forever | 900 ms, linear |
| `animate-pulse` | alpha 1 → 0.45 → 1, forever | 1400 ms, accelerate-decelerate |
| `animate-none` | no animation | — |

```xml
<icon name="loader" class="animate-spin text-accent"/>
```

The animator starts **once** from the element's initial style (it is *not* restarted on reactive
re-apply — restarting a spin on every hover would stutter). It is tied to the subtree via an effect's
`onDispose`, so an `<if>`/`<for>` rebuild **cancels** it — important, because a `ValueAnimator` holds
a strong reference to its listener and would otherwise keep animating a detached view.

## Layout transitions — `transition`

`transition` installs a ModernUI `LayoutTransition` on the container with `CHANGING` enabled:

- children that **appear/disappear** fade in/out, and
- children that merely **moved** animate to their new position.

```xml
<div class="w-4 h-4 rounded border transition {checked}">
    <if cond={checked} class="transition">
        <icon name="check" size="12" class="text-contrast"/>
    </if>
</div>
```

This is what makes a checkbox's check mark fade in and a switch thumb slide. `transition-none` clears
it. The transition is installed once — reactive restyles don't replace a live transition (which would
cancel a playing animation).

`animate-*` (a continuous animation on one view) and `transition` (animating layout changes among a
container's children) are **distinct** — you often use both, on different elements.

## Overlay entrance

Every overlay panel plays a fixed entrance when it opens: alpha 0 → 1 plus scale 0.97 → 1.0 over
**180 ms** (decelerate). You don't opt into this — `OverlayHost` applies it. See
[Overlays](overlays.md).

## From native components

If a native component starts its own `ValueAnimator`, **cancel it on dispose** — register an effect
and cancel in `onDispose`, mirroring what MCSX does for `animate-*`:

```java
ValueAnimator anim = /* … */;
anim.start();
Effect e = Effect.of(() -> {});
e.onDispose(anim::cancel);
binder.addEffect(e);
```

Otherwise the animator leaks against a detached view. See [Native Components](native-components.md).

## Gotchas

- **`animate-*` starts once, from the initial spec** — changing the animation class reactively won't
  restart it.
- **Animators are cancelled on subtree teardown** — an `<if>` toggling off stops the animation cleanly.
- **`transition` ≠ `animate-*`.** One animates layout changes across children; the other loops a single
  view's transform/alpha.
- **Overlay entrance is automatic and fixed** at 180 ms — there's no class to tune it per overlay.

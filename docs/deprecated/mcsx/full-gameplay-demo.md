# MCSX Full Gameplay Demo

This document describes the showcase feature that exercises MCSX as part of a complete gameplay loop,
not as a component gallery. The demo combines an interactive expedition planner with a persistent,
non-interactive HUD tracker. All expedition-specific code and resources belong to `glue-showcase`;
only the generic overlay host belongs to the reusable UI infrastructure.

## Player Experience

The player opens an Expedition Planner from a showcase keybinding, configures a set of objectives
and starts the expedition. The screen then closes and a compact HUD panel tracks progress from real
client state while the player moves through the world. Reopening the planner shows the same active
expedition and allows it to be reviewed, aborted or reset.

The objective set uses state Minecraft already owns:

- Travel a configured distance from the expedition's starting position.
- Reach a configured elevation.
- Carry a configured quantity of a fixed showcase item or block.

An expedition requires a non-blank name and at least one enabled objective. Starting captures the
player's current position and the selected thresholds. Progress is never advanced by UI controls;
the tracker derives it from the player and inventory so the loop remains a real gameplay task.

## Interactive Planner

The planner is an ordinary MCSX Fragment screen. It owns configuration controls while a shared
showcase model owns the expedition state.

The screen provides:

- An expedition name field.
- Objective checkboxes and threshold fields.
- Immediate validation and a reactively enabled Start button.
- Active progress when reopened during an expedition.
- Abort and Reset actions with explicit state transitions.
- Keyboard submission and normal focus navigation.
- Root-scoped themes, translated text and a reloadable `.mcss` stylesheet.

The planner and HUD observe the same MCSX signals. Neither copies the other's widget state, and
closing the screen does not destroy expedition progress.

## HUD Tracker

The HUD is a native ModernUI Fragment mounted independently from `Minecraft.screen`. It is visual
only and must not receive pointer, keyboard, focus or character input.

While an expedition is active, it displays:

- The expedition name.
- Completed objectives versus total objectives.
- Each enabled objective with its current and target values.
- A completed state before the panel is reset or dismissed.

The HUD follows vanilla HUD visibility rather than screen behavior:

- It renders during normal in-world HUD rendering.
- It does not replace `Minecraft.screen`.
- It is omitted while any Minecraft screen or loading overlay is open.
- It resumes with the same mounted View identity after returning to gameplay.
- It uses premultiplied alpha and the existing ModernUI composition path.

The implementation uses a compact top-left panel so it does not compete with the hotbar, crosshair,
status bars or right-side scoreboard. Position remains a showcase stylesheet decision, not overlay
host configuration.

## Shared State And Threading

An `ExpeditionSession` in `glue-showcase` owns configuration, lifecycle and progress signals. Client
tick code reads Minecraft state and posts only changed values to ModernUI's UI thread. MCSX Views
read and subscribe to those signals exclusively on that thread.

The session has four explicit states:

1. `DRAFT`: configuration may be edited and no HUD is shown.
2. `ACTIVE`: the start snapshot is fixed and objectives update from gameplay.
3. `COMPLETED`: every enabled objective is complete and the HUD shows completion.
4. `ABORTED`: progress stops until the player resets or starts a new draft.

No server synchronization or persistence is required for the first showcase version. Those are
separate product concerns and should not be hidden inside the UI demonstration.

## Overlay Host

MCSX currently composes Views inside mui-lite Fragment screens. A HUD Fragment needs the same
Fragment lifecycle, `ViewRoot`, Arc3D recording surface and Minecraft texture composition without
installing a `Screen`. Reimplementing those internals in Glue or accessing them reflectively would
create a second rendering host and is rejected.

mui-lite exposes one non-interactive overlay slot, and `glue-mcsx` wraps it in the managed
`UiOverlay.Hud` host so consumers never track the raw handle:

```java
UiOverlay.Hud<HudFragment> hud = UiOverlay.hud(HudFragment::new);
hud.mount();
hud.unmount();
```

`mount()` builds a fresh fragment from the factory and takes the exclusive overlay slot;
`unmount()` releases it; both are idempotent. Mounting while a workspace or another host owns the
slot fails — `UiOverlay.isOccupied()` reports that condition beforehand; applications compose
multiple HUD elements inside one Fragment rather than creating an unmanaged layer stack.

The host mounts through MCSX's overlay host rather than straight through `MuiApi`, so a managed HUD
is laid out against the window like a workspace: a window resize or GUI-scale change reaches its view
root, while it still takes no pointer or keyboard ownership.

Compositing is self-registering: `glue-mcsx` draws the mounted overlay after Minecraft's HUD and
before an open screen through its own thin mixin, so a HUD renders as soon as it is mounted. Applications must not
register `MuiApi.renderOverlay` anywhere — MCSX already composites every mounted overlay, and a
second registration draws it twice per frame.

This separation is intentional:

- mui-lite owns ModernUI lifecycle and composition.
- `glue-mcsx` owns Minecraft GUI timing through its own thin mixin and stays independent from
  `glue-render`.
- `glue-showcase` owns only the expedition feature; no render wiring is required or permitted.

## Overlay Lifecycle Contract

The overlay API must guarantee:

- Mount and close are called on Minecraft's client thread after mui-lite is ready.
- Fragment creation, attachment and removal use mui-lite's existing Fragment controller.
- Overlay Views share the normal ModernUI UI thread and frame exchange.
- Overlay rendering runs on Minecraft's render thread.
- The overlay is not composited while a screen or loading overlay is active.
- Screen input remains gated to an active ModernUI screen; mounting an overlay never changes it.
- A mounted overlay's view root follows the window: HUD and workspace alike are resized from MCSX's
  per-frame host, since only screens are resized by vanilla.
- Shutdown and graphics-resource cleanup reuse the existing host lifecycle.
- A failed or duplicate mount cannot orphan a Fragment or handle.

## Verification

`glue-test:mcsx-demo` covers handle ownership, duplicate mounts, idempotent close and visibility
policy. `glue-test:mcsx-expedition` verifies the complete integration in Minecraft:

1. Mount the HUD without replacing the active screen.
2. Confirm it appears during gameplay and retains View identity.
3. Open the planner and confirm the HUD is not composited through the screen.
4. Start an expedition and change real player state or controlled test inputs.
5. Confirm planner and HUD observe the same progress.
6. Close the overlay and confirm rendering stops and resources remain valid.

The scenario captures the active planner and the active and completed HUD for visual review. Java
compilation cannot validate Arc3D composition, alpha, placement or HUD ordering.

## Showcase Sources

- `ExpeditionSession` owns draft configuration, lifecycle, captured start position and progress.
- `ExpeditionPlanner` is the interactive MCSX Fragment opened with F9.
- `ExpeditionDemo` owns the planner and the `UiOverlay.hud` host that mounts the HUD.
- `ExpeditionGameTest` drives configuration, abort/restart, real player state, completion and reset.
- `expedition-planner.mcss` and `hud-overlay.mcss` keep screen and HUD presentation independent.

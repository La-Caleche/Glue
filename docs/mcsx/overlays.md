# Overlays & Keybindings

**Dialogs, popovers, dropdown menus, tooltips, and toasts are all one primitive:** `<overlay>`. It
renders its panel into a dedicated z-layer above the screen content (`OverlayHost`), optionally modal,
optionally anchored to a trigger. Global shortcuts use `<key>`.

## `<overlay>`

```xml
<overlay open={dialogOpen} modal="true" placement="center" onClose={closeDialog}
         class="flex-col gap-4 rounded-lg border bg-surface-2 p-6">
    <text class="text-lg text-default">Are you sure?</text>
    <div class="flex-row justify-end gap-2">
        <Button variant="ghost" onClick={closeDialog}><text>Cancel</text></Button>
        <Button variant="destructive" onClick={confirm}><text>Delete</text></Button>
    </div>
</overlay>
```

The `<overlay>` tag itself renders an **invisible placeholder** where it's written (it occupies no
space); the panel is built into the overlay host when `open` becomes true.

### Attributes

| Attribute | Meaning |
|---|---|
| `open={signal}` | **Required.** A Boolean binding — the panel shows when it's true. |
| `modal="true"` | Paint a scrim and block clicks to the content below. |
| `placement="…"` | Where the panel sits (see table). Default `center`. |
| `anchor="triggerId"` | Hang the panel below the element with that `id` (dropdown placement). |
| `onClose={handler}` | Called on dismiss (scrim click / Esc). |
| `class` | Styles the **panel**. |

`placement` values → `top`, `bottom`, `start`, `end`, `top-end`, `bottom-end`, and `center` (default).
When `anchor` resolves, the panel is positioned below the anchor with left edges aligned, in
window-space — so it works regardless of how deeply the trigger is nested.

### Dismissal

- With **`onClose`**, dismissal calls it (your handler flips `open`).
- With **no `onClose`**, the overlay **writes `open = false` itself** — because merely removing the
  layer would leave `open` stuck true and it could never reopen.
- If `open` is a **read-only** binding *and* there's no `onClose`, the scrim/Esc do nothing. Provide a
  writable signal or an `onClose`.

### Rebuild semantics

Like `<if>`: a gate effect disposes the panel and closes the layer on every change; when `open` is
true it rebuilds the panel into `OverlayHost` in the captured scope. Full rebuild, no diff.

## OverlayHost — the z-layer

`OverlayHost` sits above the content, fills the window, and is invisible until something opens. Each
open overlay is one **layer** = a scrim (transparent when non-modal — it's what makes an outside click
dismiss a menu; the modal scrim uses the `scrim` theme token) + the panel. Layers **stack in open
order**; the last opened is what `Esc` dismisses. Every panel plays the standard
[entrance animation](animations.md#overlay-entrance) (fade + slight scale, 180 ms).

## Composing the standard overlays

The shipped components are all thin wrappers over `<overlay>`:

- **Dialog** = `modal="true" placement="center"`.
- **Popover** = non-modal + `anchor`.
- **DropdownMenu / Select** = `<state>` for open + `<overlay anchor>` + `<for>` options with
  `select=`/`toggle=`.

```xml
<Dialog open={dialogOpen} onClose={closeDialog}>…</Dialog>
<Popover open={menuOpen} onClose={closeMenu} anchor="menuTrigger" class="w-[200px]">…</Popover>
```

> **Tooltips are separate.** A ModernUI-native tooltip comes from the `tooltip="…"` attribute on any
> element — it is *not* an `<overlay>`. See [Components](components.md).

## Keybindings — `<key>`

Register a global shortcut with an invisible `<key>` element:

```xml
<key combo="ctrl+k" onPress={togglePalette}/>
```

- Both `combo` and `onPress` are **required**. `onPress` resolves through the strict handler lookup —
  a missing handler throws.
- A bad combo throws `McsxBindException`.

### Combo syntax

`combo` is a `+`-joined, case-insensitive string of optional modifiers and **exactly one** key:

- **Modifiers:** `ctrl` / `control` / `cmd` / `meta` (all mean ctrl), `shift`, `alt` / `option`.
- **Key:** a single letter `a`–`z`, a digit `0`–`9`, or a named key:
  `escape`/`esc`, `enter`, `space`, `tab`, `delete`, `backspace`, `up`, `down`, `left`, `right`.

Matching requires an **exact** modifier match on key-down — `ctrl+k` will not fire if Shift is also
held. An unknown key or a combo with no key throws.

### Esc is special

`Esc` first dismisses the topmost open overlay. A declared `combo="esc"` binding only fires when
**nothing is open** — so closing a dialog doesn't also close the screen.

## Gotchas

- **`open={}` is required** on `<overlay>`, and must be writable (or you must supply `onClose`) for the
  overlay to be dismissable.
- **Overlays rebuild fully** on open/close — panel state doesn't persist across a close.
- **Layers stack; Esc pops the top one.** A `<key combo="esc">` won't fire while any overlay is open.
- **Modifier matching is exact** — there's no loose "ctrl-or-cmd" beyond `cmd`/`meta` already aliasing
  to `ctrl`.
- **`anchor` needs a matching `id`** on the trigger element; without it, placement falls back to the
  `placement` gravity.

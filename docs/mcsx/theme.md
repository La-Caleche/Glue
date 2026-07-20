# Theme & Tokens

MCSX colors are **token-first**. Utility classes like `bg-surface` / `text-accent` / `border-strong`
resolve to named design tokens, and each token maps to a packed ARGB color in the **active theme**.
Two themes ship — a dark `OBSIDIAN` (default) and a light `FROST`. Everything lives in
`fr.lacaleche.glue.mcsx.core.theme` (pure `java.*`).

The theme system is **colors only** by design — radii and spacing are fixed constants in the styling
layer, not tokens.

## `Theme`

```java
public record Theme(String name, Map<String, Integer> colors) {
    public int color(String key);   // packed ARGB, or MISSING (0xFFFF00FF) if the token is absent
    public Theme withOverrides(String name, Map<String, Integer> overrides);
}
```

`color(key)` returns the token's ARGB, or the sentinel **`0xFFFF00FF` (loud magenta)** for an unknown
token — so a missing token shows up on screen instead of crashing.

## The token keys (`Tokens`)

`Tokens` holds the canonical string keys. The full set:

| Category | Keys |
|---|---|
| Text | `text.primary`, `text.muted`, `text.subtle` |
| Surface | `surface.base`, `surface.1`, `surface.2`, `surface.3`, `surface.hover`, `surface.active` |
| Chrome | `scrim`, `border`, `border.strong`, `ring` |
| Accent | `accent`, `accent.hover`, `accent.active`, `accent.contrast`, `accent.subtle` |
| Status | `status.success`, `status.warning`, `status.danger`, `status.info` |
| Status (contrast/subtle) | `status.danger.contrast`, `status.success.subtle`, `status.warning.subtle`, `status.danger.subtle`, `status.info.subtle` |

> **`ring` has a value but no utility class maps to it** — it's reachable only from Java, not from
> markup.

Tokens map to classes as follows (see [Styling](styling.md) for the full lists):

- `bg-*` → surface / accent / status / border tokens (`bg-surface` → `surface.1`, `bg-base` →
  `surface.base`).
- `text-*` → text / accent / status tokens.
- `border-*` → `border`, `border.strong`, `accent`, and status tokens.

## Built-in themes (`Themes`)

Both themes define all tokens. Selected values:

| Token | OBSIDIAN (dark, default) | FROST (light) |
|---|---|---|
| `text.primary` | `0xFFEDEDED` | `0xFF0B1220` |
| `surface.base` | `0xFF0B0E12` | `0xFFDFE5EC` |
| `surface.1` | `0xD114191F` | `0xD9FFFFFF` |
| `border` | `0x17FFFFFF` | `0x1A0F172A` |
| `accent` | `0xFF10B981` (emerald) | `0xFF059669` |
| `status.danger` | `0xFFF87171` | `0xFFDC2626` |

Several surface / border / scrim tokens are deliberately **semi-transparent** (alpha in the high byte)
so panels layer over the game world like frosted glass.

## Selecting the active theme

```java
Theme current = Themes.active();        // the theme in effect
Themes.active(Themes.FROST);            // set it
Themes.toggle();                        // flip OBSIDIAN ↔ FROST
```

- Active-theme selection is reactive. Existing markup Views that consume token colors repaint when
  the theme changes; their controller state and View identity are retained.
- The active theme is process-global: every bound MCSX screen observes the same selection.
- Writing an equal theme is a no-op. `Themes.toggle()` is specifically the built-in
  `OBSIDIAN`/`FROST` convenience; application-defined theme cycles should select themes explicitly.
- **The active theme is unsynchronized — set/read it on the render/UI thread only.**

## Adding your own colors

- For one-off colors, skip tokens and use arbitrary hex: `bg-[#151a2e]`, `text-[#5be49b]`.
- For a complete custom palette, construct a `Theme` with the canonical token keys.
- For focused customization, derive from a complete palette:

```java
Theme aurora = Themes.OBSIDIAN.withOverrides("aurora", Map.of(
        Tokens.ACCENT, 0xFF8B5CF6,
        Tokens.SURFACE_BASE, 0xFF100D1C));
Themes.active(aurora);
```

## Theme switch component

`mcsx:components/theme-switch` is presentation-only. The controller owns theme selection and any
persistence policy:

```xml
<import name="ThemeSwitch" from="mcsx:components/theme-switch"/>
<ThemeSwitch theme={themeName} onToggle={cycleTheme}/>
```

The controller exposes a reactive name and a handler:

```java
private final Computed<String> themeName = computed(() -> Themes.active().name());

private void cycleTheme() {
    Themes.toggle();
}
```

The component deliberately contains no persistence or global policy. This keeps it reusable with two
built-in themes, a longer custom cycle, or an application setting. The `theme` value is display-only;
unknown custom names do not require additional component variants.

### Custom theme cycle

```java
private static final Theme AURORA = Themes.OBSIDIAN.withOverrides("aurora", Map.of(
        Tokens.ACCENT, 0xFF8B5CF6,
        Tokens.ACCENT_HOVER, 0xFFA78BFA));

private final Computed<String> themeName = computed(() -> Themes.active().name());

private void cycleTheme() {
    Theme current = Themes.active();
    if (current == Themes.OBSIDIAN) {
        Themes.active(Themes.FROST);
    } else if (current == Themes.FROST) {
        Themes.active(AURORA);
    } else {
        Themes.active(Themes.OBSIDIAN);
    }
}
```

The complete working version is `ThemeStressController` in the testmod.

## Gotchas

- **Missing token = magenta**, not an exception — scan for `0xFFFF00FF` if a color looks wrong.
- **`ring` is unreachable from classes.**
- **Colors only.** Corner radii and spacing are constants in the styling layer, not theme tokens — you
  can't re-theme spacing.
- Consumer-native Views and dock chrome that resolve token colors outside binder-managed style effects
  must subscribe or restyle themselves. Markup styling is live; arbitrary Java drawing is not made
  reactive automatically.

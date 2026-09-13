---
title: Themes and Styles
description: Apply a complete JSON theme and MCSS stylesheet to the Probe Inspector.
artifact: glue-mcsx
modId: glue-mcsx
environment: client
---

# Themes and Styles

Themes provide typed semantic values. MCSS selects MCSX components and assigns paint or layout
properties. This task applies both to the Probe Inspector before presenting their schemas.

## Outcome

The same Java View tree keeps the neutral MCSX design while a resource theme adjusts semantic
surfaces and state colors. F3+T updates a valid resource generation without replacing the mounted
Views.

<DocImage title="Probe Inspector theme comparison" description="Capture the same Probe Inspector before and after applying the Light Workshop JSON theme and MCSS; keep the form state identical so surface, spacing, field, and accent changes are easy to compare." />

## Install the Resource Scope

This complete screen loads `lightworkshop:probe-inspector` as both a theme and a stylesheet:

```java [ProbeInspectorScreen.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.UiScreen;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import icyllis.modernui.view.View;
import net.minecraft.resources.ResourceLocation;

public final class ProbeInspectorScreen extends UiScreen {

    private static final ResourceLocation INTERFACE =
            ResourceLocation.fromNamespaceAndPath("lightworkshop", "probe-inspector");
    private final Signal<String> name = Signal.of("Key light");
    private final Value<Boolean> validName =
            this.name.map(value -> !value.isBlank());

    @Override
    protected View create(Ui ui) {
        return ui.screen(
                Themes.resource(INTERFACE),
                Stylesheets.resource(INTERFACE),
                ui.card(
                        ui.literalHeading("Probe Inspector"),
                        ui.literalCopy("Tune one workshop probe."),
                        ui.literalField("Probe name").text(this.name),
                        ui.actions(
                                ui.literalSecondaryButton("Reset", () -> this.name.set("")),
                                ui.literalButton("Apply", () ->
                                        this.name.set(this.name.get().trim()))
                                        .enabled(this.validName)
                        )
                ).classes("probe-card")
        );
    }
}
```

Create the matching JSON theme:

```json [assets/lightworkshop/mcsx/themes/probe-inspector.json]
{
  "values": {
    "surface": "#191c20",
    "surface-raised": "#262b31",
    "surface-well": "#121518",
    "surface-hover": "#2f353c",
    "text-primary": "#eef1f4",
    "text-muted": "#b6bec5",
    "accent": "#3d464f",
    "accent-hover": "#49535d",
    "control-on": "#cdd4da",
    "danger": "#241715",
    "danger-text": "#c19086",
    "control-height": 32,
    "corner-radius": 3,
    "shell-radius": 4
  }
}
```

Then add the MCSS stylesheet with the same logical id:

```css [assets/lightworkshop/mcsx/styles/probe-inspector.mcss]
.ui-screen {
    padding: 20px;
    gap: 0px;
    align-items: center;
    justify-content: center;
    background: @surface;
}

.probe-card {
    width: 90%;
    max-width: 560px;
    padding: 24px;
    gap: 14px;
    background: @surface;
    corner-radius: @shell-radius;
}

.probe-card > Text.ui-heading {
    color: @text-primary;
    text-size: 22px;
}

Button.secondary {
    background: transparent;
}

Button:hover {
    background: @accent-hover;
}
```

### Expected result

The JSON tokens establish the palette and dimensions. MCSS controls the form's width and layout.
Stylesheets installed through `stylesheet(...)` are layered above the built-in MCSX rules, so controls
retain complete rest, hover, pressed, disabled, secondary, quiet, and destructive states where MCSS
does not override them.

## Understand Scope and Precedence

Theme and stylesheet scope belongs to a `Column` or `Row`, never to process-global state.
`Ui.screen(theme, stylesheet, children...)` installs both on its root; `theme(...)` and
`stylesheet(...)` expose the same operation on either container. A normal stylesheet is layered over
the built-in MCSX rules. Use `rawStylesheet(...)` when the subtree must receive exactly the supplied
rules instead:

```java
Column raw = ui.column(ui.literalButton("Unstyled", () -> {}));
raw.rawStylesheet(Stylesheet.empty());
```

A raw scope also shields its subtree from an outer stylesheet. With an empty raw stylesheet, MCSX
components retain their native functional behavior and values but receive no MCSX palette, spacing,
button elevation, lighting, or semantic variants. Both static and reactive stylesheet overloads are
available. Install only one final scope before attachment; the implicit empty scope created by
`Ui.screen(...)` may be replaced once so a constructed screen can opt into raw styling.

Install each scope only once and before attachment. To switch a Java theme at runtime, install the
reactive source first:

```java
Signal<Theme> selectedTheme = Signal.of(Themes.mcsx());
Column root = ui.screen(ui.literalText("Theme preview"));
root.theme(selectedTheme);

selectedTheme.set(Themes.light());
```

Nested theme or stylesheet scopes shield their subtree from the corresponding outer scope. MCSX
property precedence is the native component baseline, stylesheet declaration, then a local MCSX
property such as `Text.color(...)`. A stylesheet token reference resolves through the nearest theme;
the theme is data for that declaration rather than a separate visual-default origin. Prefer the MCSX
property method over an inherited ModernUI setter when both exist, so later reactive and reload
updates remain predictable.

Moving a View out of a stylesheet scope clears declarations from that scope and restores the next
available cascade origin.

## Lifecycle and Reload Ownership

MCSX registers resource reload listeners automatically. Do not instantiate or register
`McsxThemeLoader`, `McsxStylesheetLoader`, or classes in an `internal` package.

On a successful reload, each handle advances to one complete generation and mounted View trees stay
in place. A missing theme resolves to `Themes.mcsx()`; `Themes.dark()` is a compatibility alias for
that same singleton. A missing stylesheet handle resolves to `Stylesheet.empty()`, which still leaves
the built-in MCSX rules beneath the consumer layer. Minecraft `Component` labels also re-resolve for
the selected language and resources. Publication is queued to ModernUI's UI thread, so it can become
visible shortly after Minecraft's reload future completes. If any candidate theme or stylesheet
fails, the whole candidate generation is rejected and the previous valid generation remains active.

## Validate Shipped Resources

Catch errors during tests instead of during F3+T:

```java [McsxResourcesTest.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.mcsx.client.style.StylesheetParser;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

final class McsxResourcesTest {

    private static final ResourceLocation INTERFACE =
            ResourceLocation.fromNamespaceAndPath("lightworkshop", "probe-inspector");
    private static final Path MCSX =
            Path.of("src/main/resources/assets/lightworkshop/mcsx");

    @Test
    void interfaceResourcesAreValid() throws IOException {
        try (Reader reader = Files.newBufferedReader(
                MCSX.resolve("themes/probe-inspector.json"))) {
            Themes.validate(INTERFACE, reader);
        }

        String source = Files.readString(
                MCSX.resolve("styles/probe-inspector.mcss"));
        StylesheetParser.parse(INTERFACE, source);
    }
}
```

`Themes.validate(...)` checks one document but cannot resolve the resource-pack parent graph; missing
or cyclic parents remain reload-time errors. `StylesheetParseException` exposes `resource()`,
`line()`, and `column()` for structured diagnostics.

::: details Theme tokens and Java themes
`Themes.mcsx()` is the canonical immutable preset. `Themes.dark()` returns the same instance, while
`Themes.light()` remains an optional compatibility preset. A custom Java theme is also immutable:

```java
Theme workshop = Theme.builder()
        .set(ThemeTokens.SURFACE, 0xff191c20)
        .set(ThemeTokens.SURFACE_RAISED, 0xff262b31)
        .set(ThemeTokens.SURFACE_WELL, 0xff121518)
        .set(ThemeTokens.TEXT_PRIMARY, 0xffeef1f4)
        .set(ThemeTokens.ACCENT, 0xff3d464f)
        .set(ThemeTokens.CONTROL_ON, 0xffcdd4da)
        .set(ThemeTokens.CONTROL_HEIGHT, 32)
        .set(ThemeTokens.CORNER_RADIUS, 3)
        .build();
```

| Token | Type | Purpose |
| --- | --- | --- |
| `SURFACE` | packed ARGB `Integer` | Opaque panel body |
| `SURFACE_RAISED` | packed ARGB `Integer` | Raised controls and selected rows |
| `SURFACE_WELL`, `SURFACE_HEADER`, `SURFACE_FOOTER` | packed ARGB `Integer` | Recessed fields and shell chrome |
| `SURFACE_HOVER`, `SURFACE_QUIET_HOVER`, `SURFACE_PRESSED`, `SURFACE_DISABLED` | packed ARGB `Integer` | Interactive surface states |
| `TEXT_PRIMARY` | packed ARGB `Integer` | Primary text |
| `TEXT_MUTED` | packed ARGB `Integer` | Hints and secondary text |
| `TEXT_EXPLANATORY`, `TEXT_SUBTITLE`, `TEXT_META`, `TEXT_LABEL`, `TEXT_DISABLED` | packed ARGB `Integer` | Ranked text roles |
| `TEXT_ON_ACCENT`, `TEXT_ON_LIGHT` | packed ARGB `Integer` | Text on primary and switched-on fills |
| `ACCENT`, `ACCENT_HOVER` | packed ARGB `Integer` | Neutral primary-action states |
| `CONTROL_ON` | packed ARGB `Integer` | Checked controls and active markers |
| `DANGER`, `DANGER_HOVER`, `DANGER_FIELD`, `DANGER_TEXT`, `DANGER_TEXT_HOVER`, `DANGER_LINE` | packed ARGB `Integer` | Destructive and validation roles |
| `HUD_SURFACE`, `HUD_SURFACE_STRONG` | packed ARGB `Integer` | Translucent over-world surfaces |
| `CONTROL_HEIGHT` | non-negative `Integer` | Interactive control height |
| `CHIP_RADIUS`, `CORNER_RADIUS`, `SHELL_RADIUS` | non-negative `Integer` | 2px, 3px, and 4px corner roles |
| `CHECKBOX_INDICATOR` | `ColorStateList` | Native checkbox interaction states |

Missing entries use the token fallback. Lookup uses Token object identity, not its name. Reuse the
same `Token<T>` instance. `CHECKBOX_INDICATOR` is derived from `CONTROL_ON` and text colors for resource
themes; an existing theme that overrides `ACCENT` without declaring `CONTROL_ON` retains its accent as
the checked indicator for compatibility. The compound indicator is not writable through JSON or as an
MCSS scalar. Dock-specific tokens are documented in [Dockspace interaction](../dockspace/interaction.md#theme-the-workspace).
:::

::: details JSON theme grammar
`Themes.resource(id)` maps to `assets/<namespace>/mcsx/themes/<path>.json`. The root permits an
optional `parent` resource location and requires `values`. Without a parent, omitted entries inherit
the built-in MCSX baseline; a child merges its parent before applying overrides.

- Colors accept `#rgb`, `#rrggbb`, `#aarrggbb`, a same-kind `@token`, or an object containing exactly
  `color` and `alpha` (`0..255`). JSON does not accept `transparent`.
- Dimensions accept non-negative JSON integers or same-kind `@token` references.
- Unknown fields or tokens, type mismatches, missing parents, parent cycles, and token-reference
  cycles reject the candidate generation.
- Tokens created with `Token.of(...)` work in Java themes but are not registered for JSON or MCSS.
:::

::: details MCSS grammar and property matrix
MCSS supports component types (`Column`, `Row`, `Text`, `Button`, `Checkbox`, `TextField`), classes,
one part, states, and one direct-child `>` combinator. An owner/part selector such as
`Column::title` matches a View marked `part("title")` whose nearest styled parent matches `Column`.
Unstyled wrappers, including the `ScrollView` created by `Ui.screen(...)`, are transparent to the
styled child combinator.

| Properties | Supported targets | Values |
| --- | --- | --- |
| `color` | `Text`, `Button`, `Checkbox`, `TextField` | hex, `transparent`, color token |
| `background` | `Column`, `Row`, `Button`, `TextField` | hex, `transparent`, color token |
| `hint-color` | `TextField` | hex, `transparent`, color token |
| `corner-radius` | `Column`, `Row`, `Button`, `TextField` | non-negative `px`, dimension token |
| `control-height` | `Button`, `Checkbox`, `TextField` | non-negative `px`, dimension token |
| `text-size` | `Text`, `Button`, `Checkbox`, `TextField` | non-negative `px`, dimension token |
| `font-weight` | `Button` | `normal`, `bold` |
| `elevation`, `top-highlight-height` | `Button` | non-negative `px`, dimension token |
| `top-highlight` | `Button` | hex, `transparent`, color token |
| `indicator-tint` | `Checkbox` | `theme`, `native` |
| `padding-horizontal`, `padding-vertical` | `TextField` | non-negative `px`, dimension token |
| `width`, `max-width` | All six components | non-negative `px` or percentage |
| `flex-grow` | All six components | non-negative number |
| `padding`, `gap` | `Column`, `Row` | non-negative `px` |
| `align-items` | `Column`, `Row` | `start`, `end`, `center`, `stretch` |
| `justify-content` | `Column`, `Row` | `start`, `end`, `center`, `space-between`, `space-around`, `space-evenly` |

Class, state, and part selectors outrank type selectors; later declarations win at equal specificity.
Consumer rules occupy a higher cascade origin than built-in rules, so even a consumer type selector
outranks a built-in state selector for the same property. Declaring `Button { background: ...; }`
therefore owns that background in every state unless the consumer also declares state rules. Class
names have no behavior in Java: `secondary`, `quiet`, and `danger` are ordinary selectors whose
appearance comes entirely from the built-in MCSS. The parser rejects unknown properties, malformed
values, incompatible tokens, and properties known to be incompatible with a concrete target.

Although the parser accepts dimension-token references for `width`, `max-width`, `padding`, and
`gap`, the current Taffy bridge does not resolve those references. Use literal `px` or percentages
for those layout properties. Tokens do work for component-applied dimensions such as
`corner-radius`, `control-height`, `text-size`, `elevation`, and TextField padding.

MCSS has no descendant selectors, selector lists, ids, universal selector, media queries, animation,
or arbitrary ModernUI properties. `Stylesheet.internalRules()` and its parsed AST are implementation
details despite their Java visibility.
:::

## Next Steps

- [Add reactive theme switching](./reactivity.md) if users need a live preview.
- [Apply the same theme to a HUD](./overlays.md) while keeping its root transparent.
- [Theme Dockspace chrome](../dockspace/interaction.md#theme-the-workspace) with dock-specific tokens.
- [Assemble the final interface milestone](../workshop/interface.md) with shared resource ids.

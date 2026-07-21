# Fonts And Icons

MCSX loads fonts from active Minecraft resource packs. A font file at:

```text
assets/example/fonts/ui.ttf
```

is exposed as `example:ui`. TrueType (`.ttf`) and OpenType (`.otf`) files are supported.

## Custom Text Fonts

Set `font` on text, input, or a container:

```xml
<div font="example:ui">
    <text>This text uses the resource-pack font.</text>
    Bare text inherits it too.
</div>
```

The attribute may be a binding (`font={fontName}`). Container fonts are inherited by descendant
text and icons. Missing or malformed fonts fail loudly instead of silently selecting another face.
Custom text fonts include ModernUI's sans face as a fallback for glyphs they do not contain.

Fonts reload with client resources. Existing live views are rebound on the ModernUI thread, so a
resource-pack change does not require reopening the screen.

## Icon Fonts

`<icon>` is a square, centered `TextView` rendering one font glyph:

```xml
<icon name="check" size="16" class="text-muted"/>
<icon font="example:icons" name="save" size="18"/>
<icon font="example:icons" glyph="E001" size="18"/>
```

- `font` defaults to `mcsx:icons`.
- `name` resolves a semantic name from the font's adjacent JSON descriptor.
- `glyph` accepts a hexadecimal Unicode code point (`E001`, `0xE001`, or `U+E001`) and bypasses the
  name map. Exactly one of `name` or `glyph` is required.
- `size` defaults to 16 px.
- Text color, interaction classes, and `animate-*` work like before.

## Font Descriptors

An optional JSON file beside the font defines semantic glyph names:

```text
assets/example/fonts/icons.ttf
assets/example/fonts/icons.json
```

```json
{
    "glyphs": {
        "save": "E001",
        "delete": "E002"
    }
}
```

The resource identifier for both files is `example:icons`. A higher-priority resource pack can
replace either file. Descriptor values may be BMP or supplementary Unicode code points.

A descriptor may select a system family without shipping a font file:

```json
{
    "system": "sans-serif",
    "glyphs": {
        "check": "2713"
    }
}
```

MCSX's default `mcsx:icons` bundles Font Awesome 6 Free Solid and maps the existing semantic names to
its Private Use Area code points. A resource pack may independently override `fonts/icons.ttf`,
`fonts/icons.json`, or both without changing any `.mcsx` document.

## Built-In Names

```text
check          loader         chevron-down   chevron-right  chevron-left
chevron-up     search         close          plus           minus
settings       user           trash          copy           info
alert-triangle check-circle   bell           sun            moon
folder         terminal       sliders        layout         box
download       palette
```

There is no SVG parser or manually flattened vector geometry in MCSX. Font loading, shaping,
rasterization, fallback, tinting, and scaling all use ModernUI's text engine.

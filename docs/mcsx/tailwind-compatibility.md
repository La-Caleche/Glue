# Tailwind Compatibility

MCSX implements a Tailwind-inspired utility subset for a native ModernUI flex renderer. It is not a
CSS engine, and it does not claim source compatibility with arbitrary Tailwind projects.

This audit uses the Tailwind v4 utility-family organization and the MCSX implementation as of July
2026. It measures capability families, not visual fidelity to a browser.

## Measurement

A literal class-count percentage is not meaningful because Tailwind generates open-ended scales and
arbitrary values. Compatibility is therefore measured against 100 Tailwind v4 utility families that
have useful equivalents in a native View renderer. Browser-only families such as tables, columns,
print rules, pseudo-elements, masks, and CSS compositing are excluded.

Each family has equal weight:

- Full: the useful family syntax and semantics are covered end to end.
- Partial: MCSX implements a useful but narrower value or behavior set.
- Unsupported: no rendered equivalent exists.

The weighted score gives partial families half credit:

```text
(full + 0.5 * partial) / applicable families
(1 + 0.5 * 28) / 100 = 15%
```

The strict lower bound is 1%, counting only complete families. The permissive upper bound is 29%,
counting every partial family as supported.

## Coverage By Area

| Area | Full | Partial | Unsupported | Families |
|---|---:|---:|---:|---:|
| Layout | 0 | 2 | 7 | 9 |
| Flexbox and grid | 0 | 8 | 16 | 24 |
| Spacing | 0 | 2 | 0 | 2 |
| Sizing | 0 | 6 | 0 | 6 |
| Typography | 0 | 4 | 16 | 20 |
| Backgrounds | 0 | 1 | 4 | 5 |
| Borders and outlines | 0 | 3 | 5 | 8 |
| Effects | 1 | 0 | 2 | 3 |
| Transitions and animation | 0 | 2 | 4 | 6 |
| Transforms | 0 | 0 | 5 | 5 |
| Interactivity | 0 | 0 | 9 | 9 |
| SVG paint | 0 | 0 | 3 | 3 |
| **Total** | **1** | **28** | **71** | **100** |

## Strongest Coverage

MCSX is most useful for:

- Row and column flex layout, wrapping, alignment, distribution, gap, grow, and shrink.
- Fixed, full, auto, fractional, minimum, and maximum dimensions.
- Padding and axis auto-centering.
- Token backgrounds, text colors, borders, opacity, and corner radii.
- Basic text sizes, weights, and alignment.
- Hover, focus, active, and disabled box/text-color states.
- Basic position/inset support, child layout transitions, spin, and pulse animations.

## Intentional Differences

- Colors use semantic theme tokens rather than Tailwind's standard palette.
- The spacing scale is fixed at 4 pixels per step.
- Utility conflicts use class-string order, with the last utility winning.
- Variants are limited to `hover:`, `focus:`, `active:`, and `disabled:`.
- There are no responsive, dark, group, peer, data, ARIA, or arbitrary variants.
- Grid, transforms, shadows, filters, z-index, and general overflow are not implemented.
- `<scroll>` is a structural element rather than an overflow utility.

The compatibility score should be updated only when a utility family works through parsing,
`StyleSpec`, and ModernUI application. Parser-only acceptance does not count.

package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Align;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.ColorValue;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Justify;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Length;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.TextAlign;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.graphics.text.FontPaint;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies a resolved {@link StyleSpec} onto a ModernUI {@code View}. This is the design-agnostic
 * bridge: {@code core.style} produces the spec (pure, testable); this layer turns it into ModernUI
 * calls and resolves {@link ColorValue} against the {@linkplain Themes#active() active theme}.
 *
 * <p>Containers are {@link FlexLayout}s, so {@code justify-*} (all six), {@code items-*},
 * {@code self-*}, {@code gap-*}, {@code grow}, {@code shrink}, {@code flex-wrap}, min/max sizing and
 * {@code absolute} positioning are real rather than emulated. The remaining gaps vs. CSS are
 * {@code align-content} and {@code flex-basis} as a distinct property. Text size uses ModernUI's
 * default unit (sp) — the numeric scale is an in-game tuning point.
 */
public final class ViewStyles {

    private ViewStyles() {
    }

    /** Resolves a color to a packed ARGB: literals pass through, token refs hit the active theme. */
    public static int resolve(ColorValue color) {
        return switch (color) {
            case ColorValue.Literal literal -> literal.argb();
            case ColorValue.TokenRef ref -> Themes.active().color(ref.key());
        };
    }

    /**
     * Background fill, corner radius, border stroke, padding and opacity — valid for any View. Only
     * the properties the spec declares are written; an absent one is left as the view had it. That is
     * the contract a {@link NativeComponent} relies on: it paints its own box in {@code create} and
     * then calls this to layer the element's {@code class} over it.
     */
    public static void applyBox(View view, StyleSpec spec) {
        applyBox(view, spec, false);
    }

    /**
     * @param clearAbsent when true, a <em>box</em> property the spec does not declare is written as
     *     its default — no background, no padding, fully opaque — instead of being left alone.
     *     Required wherever styling is re-applied reactively: the spec is the whole truth about the
     *     box on every run, so a class string that drops {@code p-4 opacity-50} must stop padding and
     *     stop dimming rather than keep the values of the class string it replaced. Wrong for native
     *     components, whose box is set by their own Java before this runs.
     *
     *     <p>The clickable/focusable flags below are deliberately <em>not</em> part of that truth:
     *     the spec is only one of their two owners (the element's click/drag wiring is the other), so
     *     this method can raise them but never lower them. {@link #applyStateFlags} is what writes
     *     that channel in full, and it is told about the other owner.
     */
    public static void applyBox(View view, StyleSpec spec, boolean clearAbsent) {
        Drawable background = backgroundFor(spec);
        if (background != null || clearAbsent) {
            view.setBackground(background);
        }

        if (needsClickable(spec)) {
            view.setClickable(true);
        }
        if (needsFocusable(spec)) {
            view.setFocusable(true);
        }

        // setBackground above can install the drawable's own padding, so padding is written after it.
        if (spec.hasPadding() || clearAbsent) {
            int[] padding = spec.paddingPx();
            view.setPadding(padding[0], padding[1], padding[2], padding[3]);
        }

        if (spec.opacity() != null) {
            view.setAlpha(spec.opacity());
        } else if (clearAbsent) {
            view.setAlpha(1f);
        }
    }

    /**
     * The clickable/focusable flags in full, for a View whose interaction MCSX entirely owns. A View
     * only enters the hovered/pressed state if it is clickable, and the focused state if it is
     * focusable, so a purely presentational element with {@code hover:}/{@code active:}/{@code focus:}
     * variants needs them set or its {@link StateListDrawable} never switches.
     *
     * <p>Clickable has two owners — the spec's state variants, and the element's own click/drag wiring
     * — which is why {@link #applyBox} may only raise it. Lowering it needs both inputs at once, and
     * that is this method: a reactive class that drops its {@code hover:} utilities from a handler-less
     * element has to give the flag back, or the element stays clickable for the rest of its life and
     * swallows the clicks meant for its ancestors. Focusable has one owner, the spec, so it simply
     * follows {@code focus:}.
     *
     * <p>Wrong for a View that sets these flags in its own constructor — an {@code EditText} is
     * focusable and clickable because it is editable, not because of anything in its class string —
     * and wrong for a native component, for the same reason.
     *
     * @param interactive whether the element wires a click or a drag of its own. A {@code tooltip=}
     *     is not one: ModernUI shows it from the hover <em>dispatch</em>, not the hovered state.
     */
    static void applyStateFlags(View view, StyleSpec spec, boolean interactive) {
        view.setClickable(interactive || needsClickable(spec));
        view.setFocusable(needsFocusable(spec));
    }

    static boolean needsClickable(StyleSpec spec) {
        Map<StyleSpec.Variant, StyleSpec> variants = spec.variants();
        return variants.containsKey(StyleSpec.Variant.HOVER)
                || variants.containsKey(StyleSpec.Variant.ACTIVE);
    }

    private static boolean needsFocusable(StyleSpec spec) {
        return spec.variants().containsKey(StyleSpec.Variant.FOCUS);
    }

    /**
     * The background for a spec. With no variants it's a single {@link ShapeDrawable} (or null if the
     * spec sets no bg/corner/border). With {@code hover:}/{@code focus:}/{@code active:}/{@code
     * disabled:} box variants it's a {@link StateListDrawable} that swaps the box per view state —
     * more specific states first, the base last as the catch-all. Only <em>box</em> properties vary
     * by state here; state-dependent text color is not applied.
     */
    private static Drawable backgroundFor(StyleSpec spec) {
        Map<StyleSpec.Variant, StyleSpec> variants = spec.variants();
        if (variants.isEmpty()) {
            return boxDrawable(spec);
        }
        StateListDrawable states = new StateListDrawable();
        boolean any = false;
        StyleSpec disabled = variants.get(StyleSpec.Variant.DISABLED);
        if (disabled != null) {
            ShapeDrawable drawable = boxDrawable(spec.merged(disabled));
            if (drawable != null) {
                states.addState(new int[]{-R.attr.state_enabled}, drawable);
                any = true;
            }
        }
        any |= addBoxState(states, spec, variants.get(StyleSpec.Variant.ACTIVE), R.attr.state_pressed);
        any |= addBoxState(states, spec, variants.get(StyleSpec.Variant.FOCUS), R.attr.state_focused);
        any |= addBoxState(states, spec, variants.get(StyleSpec.Variant.HOVER), R.attr.state_hovered);
        ShapeDrawable base = boxDrawable(spec);
        if (base != null) {
            states.addState(new int[0], base);
            any = true;
        }
        return any ? states : null;
    }

    private static boolean addBoxState(StateListDrawable states, StyleSpec base, StyleSpec variant, int stateAttr) {
        if (variant == null) {
            return false;
        }
        ShapeDrawable drawable = boxDrawable(base.merged(variant));
        if (drawable == null) {
            return false;
        }
        states.addState(new int[]{R.attr.state_enabled, stateAttr}, drawable);
        return true;
    }

    /** Builds the bg/corner/border {@link ShapeDrawable} for a spec, or null if it sets none of them. */
    private static ShapeDrawable boxDrawable(StyleSpec spec) {
        boolean hasBackground = spec.background() != null;
        boolean hasCorner = spec.hasCorner();
        boolean hasBorder = spec.borderWidthPx() != null || spec.borderColor() != null;
        if (!hasBackground && !hasCorner && !hasBorder) {
            return null;
        }
        ShapeDrawable shape = new ShapeDrawable();
        shape.setShape(ShapeDrawable.RECTANGLE);
        if (hasBackground) {
            shape.setColor(resolve(spec.background()));
        }
        if (hasCorner) {
            int[] radii = spec.cornerRadii();
            shape.setCornerRadii(radii[0], radii[1], radii[2], radii[3]);
        }
        if (hasBorder) {
            int width = spec.borderWidthPx() != null ? spec.borderWidthPx() : 1;
            int color = spec.borderColor() != null
                    ? resolve(spec.borderColor())
                    : Themes.active().color(Tokens.BORDER);
            shape.setStroke(width, color);
        }
        return shape;
    }

    /** DESIGN.md's {@code dur-base}: toggles, expand/collapse, a popover settling in. */
    private static final long TRANSITION_MS = 180;

    /** Orientation (falling back to {@code defaultOrientation}), distribution, alignment and gap. */
    public static void applyContainer(FlexLayout layout, StyleSpec spec, Orientation defaultOrientation) {
        layout.setOrientation(spec.orientation() != null ? spec.orientation() : defaultOrientation);
        layout.setJustify(spec.justify() != null ? spec.justify() : Justify.START);
        layout.setAlignItems(spec.items() != null ? spec.items() : Align.STRETCH);
        layout.setGap(spec.gapPx() != null ? spec.gapPx() : 0);
        layout.setWrap(Boolean.TRUE.equals(spec.wrap()));
        applyTransition(layout, spec);
    }

    /**
     * {@code transition} animates a container's children in and out, and animates the ones that move
     * when the container re-lays out — a check mark fading in, a switch thumb sliding to the other
     * end. Installed once: {@code applyContainer} re-runs on every reactive restyle, and replacing a
     * live {@code LayoutTransition} would cancel the animation it is in the middle of playing.
     */
    private static void applyTransition(FlexLayout layout, StyleSpec spec) {
        boolean wanted = Boolean.TRUE.equals(spec.transition());
        if (!wanted) {
            if (spec.transition() != null) {
                layout.setLayoutTransition(null);
            }
            return;
        }
        if (layout.getLayoutTransition() != null) {
            return;
        }
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(TRANSITION_MS);
        // CHANGING is off by default: it is what animates a child that merely moved.
        transition.enableTransitionType(LayoutTransition.CHANGING);
        layout.setLayoutTransition(transition);
    }

    /** Text color (a single color or a per-state {@link ColorStateList}), size, weight and alignment. */
    public static void applyText(TextView text, StyleSpec spec) {
        applyText(text, spec, false);
    }

    /**
     * @param clearAbsent when true, an alignment the spec does not declare resets the text to
     *     start-aligned instead of being left alone — the reactive-restyle contract, so a class that
     *     drops {@code text-center} stops centring. Wrong for a native component, whose alignment is
     *     set by its own Java before this runs.
     *
     *     <p>Colour, size and weight need no such flag: their reset baseline is not a fixed default
     *     but the subtree's {@linkplain InheritedText inherited} value, which {@code applyTo} writes
     *     in full before this runs. Alignment is not inherited, so it has no baseline but this one.
     */
    public static void applyText(TextView text, StyleSpec spec, boolean clearAbsent) {
        ColorStateList textColor = textColorFor(spec, Color.toArgb(text.getCurrentTextColor()));
        if (textColor != null) {
            text.setTextColor(textColor);
        }
        if (spec.fontSizePx() != null) {
            text.setTextSize(spec.fontSizePx().floatValue());
        }
        if (spec.fontWeight() != null) {
            text.setTextStyle(textStyle(spec.fontWeight()));
        }
        if (spec.textAlign() != null) {
            setHorizontalGravity(text, textGravity(spec.textAlign()));
        } else if (clearAbsent) {
            setHorizontalGravity(text, Gravity.START);
        }
    }

    /** The weight a subtree hands down, {@code null} meaning the UA default — see {@link InheritedText}. */
    static void applyFontWeight(TextView text, StyleSpec.FontWeight weight) {
        text.setTextStyle(weight != null ? textStyle(weight) : FontPaint.NORMAL);
    }

    /**
     * {@code text-left/center/right} owns the horizontal bits of the gravity and nothing else: an
     * {@code EditText} centres its text vertically from its own constructor, and writing the gravity
     * whole would silently drop that.
     */
    private static void setHorizontalGravity(TextView text, int horizontal) {
        text.setGravity((text.getGravity() & Gravity.VERTICAL_GRAVITY_MASK) | horizontal);
    }

    /**
     * ModernUI's {@code FontPaint} exposes only regular and bold, so the four CSS weights collapse
     * onto two: {@code medium} renders regular and {@code semibold} renders bold. The spec keeps the
     * distinction, so this mapping is the only thing to revisit if variable weights land upstream.
     */
    private static int textStyle(StyleSpec.FontWeight weight) {
        return switch (weight) {
            case NORMAL, MEDIUM -> FontPaint.NORMAL;
            case SEMIBOLD, BOLD -> FontPaint.BOLD;
        };
    }

    /**
     * The text color for a spec: a single color, or a {@link ColorStateList} when {@code hover:}/
     * {@code focus:}/{@code active:}/{@code disabled:} <em>text-color</em> variants are present (the
     * text element must be hoverable/focusable for those states to fire — {@link #applyBox} sets that
     * when variants exist). Null if the spec sets no text color at all.
     */
    private static ColorStateList textColorFor(StyleSpec spec, int fallbackColor) {
        Map<StyleSpec.Variant, StyleSpec> variants = spec.variants();
        boolean hasVariantTextColor = false;
        for (StyleSpec variant : variants.values()) {
            if (variant.textColor() != null) {
                hasVariantTextColor = true;
                break;
            }
        }
        if (!hasVariantTextColor) {
            return spec.textColor() != null ? ColorStateList.valueOf(resolve(spec.textColor())) : null;
        }
        List<int[]> states = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        StyleSpec disabled = variants.get(StyleSpec.Variant.DISABLED);
        if (disabled != null && disabled.textColor() != null) {
            states.add(new int[]{-R.attr.state_enabled});
            colors.add(resolve(spec.merged(disabled).textColor()));
        }
        addTextState(states, colors, spec, variants.get(StyleSpec.Variant.ACTIVE), R.attr.state_pressed);
        addTextState(states, colors, spec, variants.get(StyleSpec.Variant.FOCUS), R.attr.state_focused);
        addTextState(states, colors, spec, variants.get(StyleSpec.Variant.HOVER), R.attr.state_hovered);
        states.add(new int[0]);
        colors.add(spec.textColor() != null ? resolve(spec.textColor()) : fallbackColor);

        int[] colorArray = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            colorArray[i] = colors.get(i);
        }
        return new ColorStateList(states.toArray(new int[0][]), colorArray);
    }

    private static void addTextState(List<int[]> states, List<Integer> colors, StyleSpec base,
                                     StyleSpec variant, int stateAttr) {
        if (variant == null || variant.textColor() == null) {
            return;
        }
        states.add(new int[]{R.attr.state_enabled, stateAttr});
        colors.add(resolve(base.merged(variant).textColor()));
    }

    /**
     * Layout params for a child of a {@link FlexLayout}. Both axes default to {@code WRAP_CONTENT}:
     * cross-axis stretch is now the container's job ({@code align-items}), not a {@code MATCH_PARENT}
     * fake on the child. An auto margin on the cross axis is {@code align-self: center} — which is
     * exactly what {@code mx-auto} means for a flex item in CSS.
     *
     * <p>Every channel is written from the spec, so the result is a complete description of the box
     * rather than a patch: a reactive class that drops {@code grow} re-derives {@code grow = 0}. This
     * runs on every restyle, so it must stay allocation-cheap and free of view state.
     */
    public static FlexLayout.LayoutParams layoutParams(StyleSpec spec, Orientation parentOrientation) {
        FlexLayout.LayoutParams params = new FlexLayout.LayoutParams(
                dimension(spec.width(), ViewGroup.LayoutParams.WRAP_CONTENT),
                dimension(spec.height(), ViewGroup.LayoutParams.WRAP_CONTENT));
        params.grow = spec.grow() ? 1f : 0f;
        params.shrink = spec.shrink() != null ? spec.shrink() : 0f;
        params.alignSelf = spec.alignSelf() != null ? spec.alignSelf() : crossAutoMargin(spec, parentOrientation);
        params.widthFraction = fractionOf(spec.width());
        params.heightFraction = fractionOf(spec.height());
        params.minWidth = orZero(spec.minWidthPx());
        params.maxWidth = spec.maxWidthPx() != null ? spec.maxWidthPx() : Integer.MAX_VALUE;
        params.minHeight = orZero(spec.minHeightPx());
        params.maxHeight = spec.maxHeightPx() != null ? spec.maxHeightPx() : Integer.MAX_VALUE;
        params.absolute = spec.absolute();
        params.left = orUnset(spec.insetLeft());
        params.top = orUnset(spec.insetTop());
        params.right = orUnset(spec.insetRight());
        params.bottom = orUnset(spec.insetBottom());
        return params;
    }

    private static int orUnset(Integer value) {
        return value != null ? value : FlexLayout.LayoutParams.INSET_UNSET;
    }

    /** The fraction a {@code w-1/2}-style length declares, or 0 when the axis is not fractional. */
    private static float fractionOf(Length length) {
        return length != null && length.mode() == Length.Mode.FRACTION ? length.fraction() : 0f;
    }

    /** {@code mx-auto} in a column (or {@code my-auto} in a row) centres the item on the cross axis. */
    private static Align crossAutoMargin(StyleSpec spec, Orientation parentOrientation) {
        boolean centred = parentOrientation == Orientation.ROW ? spec.autoMarginY() : spec.autoMarginX();
        return centred ? Align.CENTER : null;
    }

    /**
     * Layout params for the document root inside the host's full-window {@code FrameLayout}. Unlike a
     * flex child the root has no parent box to derive from, so each axis defaults to filling the
     * window (the {@code <body>} analogue) and an explicit {@code w-*}/{@code h-*} shrinks it. A
     * shrunk root sits top-left unless {@code m-auto}/{@code mx-auto}/{@code my-auto} centers it.
     */
    public static FrameLayout.LayoutParams rootLayoutParams(StyleSpec spec) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dimension(spec.width(), ViewGroup.LayoutParams.MATCH_PARENT),
                dimension(spec.height(), ViewGroup.LayoutParams.MATCH_PARENT));
        params.gravity = autoMarginGravity(spec);
        return params;
    }

    /**
     * Layout params for an overlay panel inside {@link OverlayHost}. Unlike the document root a panel
     * hugs its content by default — a dialog is as tall as what it holds — and {@code placement}
     * positions it in the window rather than against an anchor rect.
     */
    public static FrameLayout.LayoutParams overlayLayoutParams(StyleSpec spec, String placement) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dimension(spec.width(), ViewGroup.LayoutParams.WRAP_CONTENT),
                dimension(spec.height(), ViewGroup.LayoutParams.WRAP_CONTENT));
        params.gravity = placementGravity(placement);
        return params;
    }

    /** {@code <overlay placement="top-end">} → the gravity bits that put the panel there. */
    private static int placementGravity(String placement) {
        return switch (placement == null ? "" : placement) {
            case "top" -> Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            case "bottom" -> Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            case "start" -> Gravity.START | Gravity.CENTER_VERTICAL;
            case "end" -> Gravity.END | Gravity.CENTER_VERTICAL;
            case "top-end" -> Gravity.TOP | Gravity.END;
            case "bottom-end" -> Gravity.BOTTOM | Gravity.END;
            case "", "center" -> Gravity.CENTER;
            default -> throw new IllegalArgumentException("unknown overlay placement '" + placement + "'");
        };
    }

    /**
     * A {@code w={signal}} / {@code h={signal}} value onto a child's layout params: a {@code Float} is
     * a fraction of the parent (0..1), an {@code Integer} is pixels. FlexLayout reads the fraction
     * channel only on a bounded axis and only when it is {@code > 0}, so the declared dimension has to
     * carry the rest: an exact 0 for a zero fraction (a progress fill at 0% must vanish, not hug its
     * padding), and otherwise a WRAP_CONTENT placeholder that degrades to content size on an unbounded
     * axis — the same choice {@link #dimension} makes for a static {@code w-1/2}.
     */
    public static void applySize(FlexLayout.LayoutParams params, boolean horizontal, Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("size must be numeric");
        }
        boolean fractional = value instanceof Float || value instanceof Double;
        float fraction = fractional ? Math.max(0f, Math.min(1f, number.floatValue())) : 0f;
        int pixels;
        if (!fractional) {
            pixels = Math.max(0, number.intValue());
        } else if (fraction == 0f) {
            pixels = 0;
        } else {
            pixels = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        if (horizontal) {
            params.widthFraction = fraction;
            params.width = pixels;
        } else {
            params.heightFraction = fraction;
            params.height = pixels;
        }
    }

    /**
     * Gravity bits for the auto-margin axes only — an unset axis contributes nothing, so both hosts
     * fall back to their own default (top / start) for it.
     */
    private static int autoMarginGravity(StyleSpec spec) {
        return (spec.autoMarginX() ? Gravity.CENTER_HORIZONTAL : 0)
                | (spec.autoMarginY() ? Gravity.CENTER_VERTICAL : 0);
    }

    private static int dimension(Length length, int fallback) {
        if (length == null) {
            return fallback;
        }
        return switch (length.mode()) {
            case FILL -> ViewGroup.LayoutParams.MATCH_PARENT;
            // A fraction needs the container's measured size; FlexLayout resolves it from the
            // fraction carried on the LayoutParams, so the declared dimension is a placeholder.
            case WRAP, FRACTION -> ViewGroup.LayoutParams.WRAP_CONTENT;
            case PIXELS -> length.px();
        };
    }

    private static int textGravity(TextAlign align) {
        return switch (align) {
            case LEFT -> Gravity.START;
            case CENTER -> Gravity.CENTER_HORIZONTAL;
            case RIGHT -> Gravity.END;
        };
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }
}

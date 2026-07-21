package fr.lacaleche.glue.mcsx.core.style;

import java.util.Map;

/**
 * The normalized, immutable result of resolving a {@code class="…"} string — a backend-agnostic
 * description of styling that a {@code view.*} applier turns into ModernUI {@code View} state.
 * Every property is optional ({@code null} = "not set by any utility"); an applier only touches the
 * properties that were set.
 *
 * <p>Internally the ~40 properties are grouped into small nested records ({@link Flex},
 * {@link Box}, {@link Corners}, {@link Sizing}, {@link Text}), each with its own merge — so a
 * cross-group transposition is a type error and adding a property touches one group. The public
 * surface stays flat: consumers read {@code padLeft()}, never {@code sizing().padLeft()}.
 *
 * <p>Colors are {@link ColorValue}s: either an arbitrary literal ARGB (from {@code bg-[#hex]})
 * or a {@link ColorValue.TokenRef} (from {@code bg-surface}) resolved against the theme later.
 *
 * <p>{@link #variants()} holds per-state overrides ({@code hover:} / {@code focus:} / …), parsed
 * separately from the base state; the view layer renders them as a {@code StateListDrawable}.
 */
public record StyleSpec(Flex flex, Box box, Corners corners, Sizing sizing, Text text,
                        Map<Variant, StyleSpec> variants) {

    public StyleSpec {
        flex = flex == null ? Flex.EMPTY : flex;
        box = box == null ? Box.EMPTY : box;
        corners = corners == null ? Corners.EMPTY : corners;
        sizing = sizing == null ? Sizing.EMPTY : sizing;
        text = text == null ? Text.EMPTY : text;
        variants = variants == null ? Map.of() : Map.copyOf(variants);
    }

    /** Main-axis direction of a container ({@code flex-row} / {@code flex-col}). */
    public enum Orientation { ROW, COLUMN }

    /** Cross-axis alignment of a container's children ({@code items-*}). */
    public enum Align { START, CENTER, END, STRETCH }

    /** Main-axis distribution ({@code justify-*}), including the three distributed values. */
    public enum Justify { START, CENTER, END, BETWEEN, AROUND, EVENLY }

    /**
     * Whether an element takes part in its container's flex line ({@code STATIC}) or is placed
     * against the container's content box by its insets ({@code ABSOLUTE}), contributing nothing to
     * the container's own size. Every container is a positioning context, so CSS's {@code relative}
     * has no separate meaning here and parses to {@code STATIC}.
     */
    public enum Position { STATIC, ABSOLUTE }

    /** Horizontal text alignment ({@code text-left|center|right}). */
    public enum TextAlign { LEFT, CENTER, RIGHT }

    /**
     * CSS font weight ({@code font-normal|medium|semibold|bold}). The model keeps all four because
     * the design language distinguishes them; a backend that only has regular and bold collapses
     * them at apply time.
     */
    public enum FontWeight { NORMAL, MEDIUM, SEMIBOLD, BOLD }

    /**
     * A looping animation. {@code null} means no utility set one; {@code NONE} means one explicitly
     * turned it off — the distinction is what lets {@code animate-none} override an inherited
     * {@code animate-spin} through {@link #merged}.
     */
    public enum Animation { NONE, SPIN, PULSE }

    /** An interactive state a utility can be scoped to via a {@code hover:}-style prefix. */
    public enum Variant { HOVER, FOCUS, ACTIVE, DISABLED }

    /**
     * Container line behaviour and the element's own flex-item traits. The flags are {@link Boolean}
     * and not {@code boolean} so that "no utility set this" ({@code null}) stays distinct from "a
     * utility turned it off" ({@code FALSE}) — the distinction {@code grow-0} needs to beat an
     * inherited {@code grow} through {@link StyleSpec#merged}.
     */
    public record Flex(Orientation orientation, Align items, Justify justify, Integer gapPx,
                       Boolean wrap, Boolean grow, Float shrink, Align alignSelf,
                       Boolean autoMarginX, Boolean autoMarginY) {

        static final Flex EMPTY = new Flex(null, null, null, null, null, null, null, null,
                null, null);

        Flex merged(Flex overlay) {
            return new Flex(
                    overlay.orientation != null ? overlay.orientation : orientation,
                    overlay.items != null ? overlay.items : items,
                    overlay.justify != null ? overlay.justify : justify,
                    overlay.gapPx != null ? overlay.gapPx : gapPx,
                    overlay.wrap != null ? overlay.wrap : wrap,
                    overlay.grow != null ? overlay.grow : grow,
                    overlay.shrink != null ? overlay.shrink : shrink,
                    overlay.alignSelf != null ? overlay.alignSelf : alignSelf,
                    overlay.autoMarginX != null ? overlay.autoMarginX : autoMarginX,
                    overlay.autoMarginY != null ? overlay.autoMarginY : autoMarginY);
        }
    }

    /** The painted surface: background, border stroke, opacity, motion. */
    public record Box(ColorValue background, Integer borderWidthPx, ColorValue borderColor,
                      Float opacity, Animation animation, Boolean transition) {

        static final Box EMPTY = new Box(null, null, null, null, null, null);

        Box merged(Box overlay) {
            return new Box(
                    overlay.background != null ? overlay.background : background,
                    overlay.borderWidthPx != null ? overlay.borderWidthPx : borderWidthPx,
                    overlay.borderColor != null ? overlay.borderColor : borderColor,
                    overlay.opacity != null ? overlay.opacity : opacity,
                    overlay.animation != null ? overlay.animation : animation,
                    overlay.transition != null ? overlay.transition : transition);
        }
    }

    /** Corner radii: the uniform {@code rounded-*} value and the per-corner overrides. */
    public record Corners(Integer cornerPx, Integer topLeftPx, Integer topRightPx,
                          Integer bottomRightPx, Integer bottomLeftPx) {

        static final Corners EMPTY = new Corners(null, null, null, null, null);

        Corners merged(Corners overlay) {
            return new Corners(
                    overlay.cornerPx != null ? overlay.cornerPx : cornerPx,
                    overlay.topLeftPx != null ? overlay.topLeftPx : topLeftPx,
                    overlay.topRightPx != null ? overlay.topRightPx : topRightPx,
                    overlay.bottomRightPx != null ? overlay.bottomRightPx : bottomRightPx,
                    overlay.bottomLeftPx != null ? overlay.bottomLeftPx : bottomLeftPx);
        }
    }

    /** Declared size, its min/max bounds, padding, and absolute positioning. */
    public record Sizing(Length width, Length height, Integer minWidthPx, Integer maxWidthPx,
                         Integer minHeightPx, Integer maxHeightPx, Position position,
                         Integer insetLeft, Integer insetTop, Integer insetRight, Integer insetBottom,
                         Integer padLeft, Integer padTop, Integer padRight, Integer padBottom) {

        static final Sizing EMPTY = new Sizing(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        Sizing merged(Sizing overlay) {
            return new Sizing(
                    overlay.width != null ? overlay.width : width,
                    overlay.height != null ? overlay.height : height,
                    overlay.minWidthPx != null ? overlay.minWidthPx : minWidthPx,
                    overlay.maxWidthPx != null ? overlay.maxWidthPx : maxWidthPx,
                    overlay.minHeightPx != null ? overlay.minHeightPx : minHeightPx,
                    overlay.maxHeightPx != null ? overlay.maxHeightPx : maxHeightPx,
                    overlay.position != null ? overlay.position : position,
                    overlay.insetLeft != null ? overlay.insetLeft : insetLeft,
                    overlay.insetTop != null ? overlay.insetTop : insetTop,
                    overlay.insetRight != null ? overlay.insetRight : insetRight,
                    overlay.insetBottom != null ? overlay.insetBottom : insetBottom,
                    overlay.padLeft != null ? overlay.padLeft : padLeft,
                    overlay.padTop != null ? overlay.padTop : padTop,
                    overlay.padRight != null ? overlay.padRight : padRight,
                    overlay.padBottom != null ? overlay.padBottom : padBottom);
        }
    }

    /** Typography. */
    public record Text(Integer fontSizePx, FontWeight fontWeight, ColorValue textColor,
                       TextAlign textAlign) {

        static final Text EMPTY = new Text(null, null, null, null);

        Text merged(Text overlay) {
            return new Text(
                    overlay.fontSizePx != null ? overlay.fontSizePx : fontSizePx,
                    overlay.fontWeight != null ? overlay.fontWeight : fontWeight,
                    overlay.textColor != null ? overlay.textColor : textColor,
                    overlay.textAlign != null ? overlay.textAlign : textAlign);
        }
    }

    public Orientation orientation() {
        return flex.orientation();
    }

    public Align items() {
        return flex.items();
    }

    public Justify justify() {
        return flex.justify();
    }

    public Integer gapPx() {
        return flex.gapPx();
    }

    public Boolean wrap() {
        return flex.wrap();
    }

    /** Whether the element grows; unset reads as off. {@code flex().grow()} keeps the tri-state. */
    public boolean grow() {
        return Boolean.TRUE.equals(flex.grow());
    }

    public Float shrink() {
        return flex.shrink();
    }

    public Align alignSelf() {
        return flex.alignSelf();
    }

    public boolean autoMarginX() {
        return Boolean.TRUE.equals(flex.autoMarginX());
    }

    public boolean autoMarginY() {
        return Boolean.TRUE.equals(flex.autoMarginY());
    }

    public ColorValue background() {
        return box.background();
    }

    public Integer borderWidthPx() {
        return box.borderWidthPx();
    }

    public ColorValue borderColor() {
        return box.borderColor();
    }

    public Float opacity() {
        return box.opacity();
    }

    public Animation animation() {
        return box.animation();
    }

    public Boolean transition() {
        return box.transition();
    }

    public Integer cornerPx() {
        return corners.cornerPx();
    }

    public Integer cornerTopLeftPx() {
        return corners.topLeftPx();
    }

    public Integer cornerTopRightPx() {
        return corners.topRightPx();
    }

    public Integer cornerBottomRightPx() {
        return corners.bottomRightPx();
    }

    public Integer cornerBottomLeftPx() {
        return corners.bottomLeftPx();
    }

    public Length width() {
        return sizing.width();
    }

    public Length height() {
        return sizing.height();
    }

    public Integer minWidthPx() {
        return sizing.minWidthPx();
    }

    public Integer maxWidthPx() {
        return sizing.maxWidthPx();
    }

    public Integer minHeightPx() {
        return sizing.minHeightPx();
    }

    public Integer maxHeightPx() {
        return sizing.maxHeightPx();
    }

    public Position position() {
        return sizing.position();
    }

    public Integer insetLeft() {
        return sizing.insetLeft();
    }

    public Integer insetTop() {
        return sizing.insetTop();
    }

    public Integer insetRight() {
        return sizing.insetRight();
    }

    public Integer insetBottom() {
        return sizing.insetBottom();
    }

    public Integer padLeft() {
        return sizing.padLeft();
    }

    public Integer padTop() {
        return sizing.padTop();
    }

    public Integer padRight() {
        return sizing.padRight();
    }

    public Integer padBottom() {
        return sizing.padBottom();
    }

    public Integer fontSizePx() {
        return text.fontSizePx();
    }

    public FontWeight fontWeight() {
        return text.fontWeight();
    }

    public ColorValue textColor() {
        return text.textColor();
    }

    public TextAlign textAlign() {
        return text.textAlign();
    }

    /** The four corner radii, each falling back to the uniform {@code cornerPx}, then to zero. */
    public int[] cornerRadii() {
        int uniform = cornerPx() != null ? cornerPx() : 0;
        return new int[]{
                cornerTopLeftPx() != null ? cornerTopLeftPx() : uniform,
                cornerTopRightPx() != null ? cornerTopRightPx() : uniform,
                cornerBottomRightPx() != null ? cornerBottomRightPx() : uniform,
                cornerBottomLeftPx() != null ? cornerBottomLeftPx() : uniform};
    }

    /**
     * The four padding values ({@code left, top, right, bottom}), each falling back to zero. An
     * applier that re-runs on every restyle needs the resolved value, not the declared one: a class
     * string that dropped its {@code p-4} has to pad nothing, not keep padding by four.
     */
    public int[] paddingPx() {
        return new int[]{
                padLeft() != null ? padLeft() : 0,
                padTop() != null ? padTop() : 0,
                padRight() != null ? padRight() : 0,
                padBottom() != null ? padBottom() : 0};
    }

    /** True when any padding edge is set, whether uniformly or per-edge. */
    public boolean hasPadding() {
        return padLeft() != null || padTop() != null || padRight() != null || padBottom() != null;
    }

    /** True when any corner is rounded, whether uniformly or per-corner. */
    public boolean hasCorner() {
        return cornerPx() != null || cornerTopLeftPx() != null || cornerTopRightPx() != null
                || cornerBottomRightPx() != null || cornerBottomLeftPx() != null;
    }

    /** True if either axis carries an auto margin — the element centers itself in its parent. */
    public boolean hasAutoMargin() {
        return autoMarginX() || autoMarginY();
    }

    /** True when the element is taken out of its container's flex line and placed by its insets. */
    public boolean absolute() {
        return position() == Position.ABSOLUTE;
    }

    /**
     * A width/height value: fill the parent ({@code w-full}), hug the content ({@code w-auto}), a
     * fixed pixel size, or a fraction of the parent ({@code w-1/2}). A fraction cannot be resolved
     * here — it needs the container's measured size — so the layout pass carries it through.
     */
    public record Length(Mode mode, int px, float fraction) {

        /** How a {@link Length} sizes its axis; only one of {@code px}/{@code fraction} is meaningful. */
        public enum Mode { FILL, WRAP, PIXELS, FRACTION }

        public static Length fillParent() {
            return new Length(Mode.FILL, 0, 0f);
        }

        public static Length wrapContent() {
            return new Length(Mode.WRAP, 0, 0f);
        }

        public static Length pixels(int px) {
            return new Length(Mode.PIXELS, px, 0f);
        }

        /**
         * The shared keyword lengths — {@code "full"} → fill, {@code "auto"} → wrap, anything
         * else → null. The single home for these keywords: both the class engine ({@code w-full})
         * and raw attributes ({@code w="full"}) resolve through here so a keyword added later
         * cannot exist in one and silently miss the other.
         */
        public static Length named(String value) {
            return switch (value) {
                case "full" -> fillParent();
                case "auto" -> wrapContent();
                default -> null;
            };
        }

        /** {@code w-1/2} → 0.5 of the parent's inner size on that axis. */
        public static Length fractionOf(float fraction) {
            if (!(fraction > 0f) || fraction > 1f) {
                throw new IllegalArgumentException("fraction must be in (0, 1], got " + fraction);
            }
            return new Length(Mode.FRACTION, 0, fraction);
        }

        public boolean fill() {
            return mode == Mode.FILL;
        }

        public boolean wrap() {
            return mode == Mode.WRAP;
        }
    }

    /** A resolved color: an arbitrary literal ARGB, or a reference to a theme token key. */
    public sealed interface ColorValue permits ColorValue.Literal, ColorValue.TokenRef {

        record Literal(int argb) implements ColorValue {
        }

        record TokenRef(String key) implements ColorValue {
        }
    }

    /**
     * Returns a spec where every property set on {@code overlay} wins over this one — including a
     * flag the overlay explicitly turns off, so {@code grow-0} clears an inherited {@code grow}.
     * Used by the binder to layer raw box attributes ({@code bg=}/{@code pad=}) over a
     * {@code class="…"} spec. If {@code overlay} declares no variants, this spec's are kept.
     */
    public StyleSpec merged(StyleSpec overlay) {
        return new StyleSpec(
                flex.merged(overlay.flex),
                box.merged(overlay.box),
                corners.merged(overlay.corners),
                sizing.merged(overlay.sizing),
                text.merged(overlay.text),
                overlay.variants.isEmpty() ? variants : overlay.variants);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable accumulator used by {@link TailwindParser}; last write per property wins. */
    public static final class Builder {

        private Orientation orientation;
        private Align items;
        private Justify justify;
        private Integer gapPx;
        private Boolean wrap;
        private Boolean grow;
        private Float shrink;
        private Align alignSelf;
        private Integer padLeft;
        private Integer padTop;
        private Integer padRight;
        private Integer padBottom;
        private Integer cornerPx;
        private Integer cornerTopLeftPx;
        private Integer cornerTopRightPx;
        private Integer cornerBottomRightPx;
        private Integer cornerBottomLeftPx;
        private Integer borderWidthPx;
        private ColorValue borderColor;
        private ColorValue background;
        private Float opacity;
        private Length width;
        private Length height;
        private Integer minWidthPx;
        private Integer maxWidthPx;
        private Integer minHeightPx;
        private Integer maxHeightPx;
        private Position position;
        private Integer insetLeft;
        private Integer insetTop;
        private Integer insetRight;
        private Integer insetBottom;
        private Boolean autoMarginX;
        private Boolean autoMarginY;
        private Integer fontSizePx;
        private FontWeight fontWeight;
        private ColorValue textColor;
        private TextAlign textAlign;
        private Animation animation;
        private Boolean transition;

        public Builder orientation(Orientation v) {
            this.orientation = v;
            return this;
        }

        public Builder items(Align v) {
            this.items = v;
            return this;
        }

        public Builder justify(Justify v) {
            this.justify = v;
            return this;
        }

        public Builder gapPx(int v) {
            this.gapPx = v;
            return this;
        }

        public Builder wrap(boolean v) {
            this.wrap = v;
            return this;
        }

        public Builder grow(boolean v) {
            this.grow = v;
            return this;
        }

        public Builder shrink(float v) {
            this.shrink = v;
            return this;
        }

        public Builder alignSelf(Align v) {
            this.alignSelf = v;
            return this;
        }

        public Builder padLeft(int v) {
            this.padLeft = v;
            return this;
        }

        public Builder padTop(int v) {
            this.padTop = v;
            return this;
        }

        public Builder padRight(int v) {
            this.padRight = v;
            return this;
        }

        public Builder padBottom(int v) {
            this.padBottom = v;
            return this;
        }

        public Builder cornerPx(int v) {
            this.cornerPx = v;
            return this;
        }

        public Builder cornerTopLeftPx(int v) {
            this.cornerTopLeftPx = v;
            return this;
        }

        public Builder cornerTopRightPx(int v) {
            this.cornerTopRightPx = v;
            return this;
        }

        public Builder cornerBottomRightPx(int v) {
            this.cornerBottomRightPx = v;
            return this;
        }

        public Builder cornerBottomLeftPx(int v) {
            this.cornerBottomLeftPx = v;
            return this;
        }

        public Builder borderWidthPx(int v) {
            this.borderWidthPx = v;
            return this;
        }

        public Builder borderColor(ColorValue v) {
            this.borderColor = v;
            return this;
        }

        public Builder background(ColorValue v) {
            this.background = v;
            return this;
        }

        public Builder opacity(float v) {
            this.opacity = v;
            return this;
        }

        public Builder width(Length v) {
            this.width = v;
            return this;
        }

        public Builder height(Length v) {
            this.height = v;
            return this;
        }

        public Builder minWidthPx(int v) {
            this.minWidthPx = v;
            return this;
        }

        public Builder maxWidthPx(int v) {
            this.maxWidthPx = v;
            return this;
        }

        public Builder minHeightPx(int v) {
            this.minHeightPx = v;
            return this;
        }

        public Builder maxHeightPx(int v) {
            this.maxHeightPx = v;
            return this;
        }

        public Builder position(Position v) {
            this.position = v;
            return this;
        }

        public Builder insetLeft(int v) {
            this.insetLeft = v;
            return this;
        }

        public Builder insetTop(int v) {
            this.insetTop = v;
            return this;
        }

        public Builder insetRight(int v) {
            this.insetRight = v;
            return this;
        }

        public Builder insetBottom(int v) {
            this.insetBottom = v;
            return this;
        }

        public Builder autoMarginX(boolean v) {
            this.autoMarginX = v;
            return this;
        }

        public Builder autoMarginY(boolean v) {
            this.autoMarginY = v;
            return this;
        }

        public Builder fontSizePx(int v) {
            this.fontSizePx = v;
            return this;
        }

        public Builder fontWeight(FontWeight v) {
            this.fontWeight = v;
            return this;
        }

        public Builder textColor(ColorValue v) {
            this.textColor = v;
            return this;
        }

        public Builder textAlign(TextAlign v) {
            this.textAlign = v;
            return this;
        }

        public Builder animation(Animation v) {
            this.animation = v;
            return this;
        }

        public Builder transition(boolean v) {
            this.transition = v;
            return this;
        }

        public StyleSpec build() {
            return build(Map.of());
        }

        public StyleSpec build(Map<Variant, StyleSpec> variants) {
            return new StyleSpec(
                    new Flex(orientation, items, justify, gapPx, wrap, grow, shrink, alignSelf,
                            autoMarginX, autoMarginY),
                    new Box(background, borderWidthPx, borderColor, opacity, animation, transition),
                    new Corners(cornerPx, cornerTopLeftPx, cornerTopRightPx,
                            cornerBottomRightPx, cornerBottomLeftPx),
                    new Sizing(width, height, minWidthPx, maxWidthPx, minHeightPx, maxHeightPx,
                            position, insetLeft, insetTop, insetRight, insetBottom,
                            padLeft, padTop, padRight, padBottom),
                    new Text(fontSizePx, fontWeight, textColor, textAlign),
                    variants);
        }
    }
}

package fr.lacaleche.glue.mcsx.core.style;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Align;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.ColorValue;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.FontWeight;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Justify;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Length;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.TextAlign;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Variant;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A small, faithful clone of Tailwind's utility-class <em>mechanics</em> — not a CSS engine.
 * Parses a whitespace-separated {@code class="…"} string into a {@link StyleSpec}, supporting
 * only the utility families a ModernUI {@code LinearLayout}/{@code View} can express (flex, box,
 * sizing, typography), the numeric scale (each step = 4px), arbitrary values ({@code bg-[#hex]},
 * {@code w-[120px]}), and the {@code hover:}/{@code focus:}/{@code active:}/{@code disabled:}
 * variant prefixes.
 *
 * <p>Design decisions baked in:
 * <ul>
 *   <li><b>token-first colors</b> — {@code bg-*}/{@code text-*}/{@code border-*} resolve to theme
 *       tokens (see {@link Tokens}); the standard Tailwind palette ({@code bg-slate-800}) is not
 *       supported and throws. Arbitrary {@code [#hex]} literals are allowed.</li>
 *   <li><b>variants parsed, base applied</b> — variant utilities are collected into
 *       {@link StyleSpec#variants()}, which the view layer renders as a
 *       {@code StateListDrawable}.</li>
 *   <li><b>fail loud in authored documents</b> — bind-time callers use {@link #parseStrict};
 *       {@link #parse} remains available for programmatic best-effort parsing.</li>
 * </ul>
 * CSS-only families (grid, z-index, floats, transforms) are intentionally absent.
 * {@code selection-*} / {@code scrollbar-*} are accepted-but-ignored (no ModernUI analogue).
 */
public final class TailwindParser {

    private static final Map<String, Integer> TEXT_SIZES = Map.of(
            "xs", 11, "sm", 12, "base", 13, "lg", 14, "xl", 16, "2xl", 20);

    private static final Map<String, String> BG_TOKENS = Map.ofEntries(
            Map.entry("base", Tokens.SURFACE_BASE),
            Map.entry("surface", Tokens.SURFACE_1),
            Map.entry("surface-2", Tokens.SURFACE_2),
            Map.entry("surface-3", Tokens.SURFACE_3),
            Map.entry("hover", Tokens.SURFACE_HOVER),
            Map.entry("active", Tokens.SURFACE_ACTIVE),
            Map.entry("scrim", Tokens.SCRIM),
            Map.entry("border", Tokens.BORDER),
            Map.entry("border-strong", Tokens.BORDER_STRONG),
            Map.entry("accent", Tokens.ACCENT),
            Map.entry("accent-hover", Tokens.ACCENT_HOVER),
            Map.entry("accent-active", Tokens.ACCENT_ACTIVE),
            Map.entry("accent-subtle", Tokens.ACCENT_SUBTLE),
            Map.entry("success", Tokens.STATUS_SUCCESS),
            Map.entry("warning", Tokens.STATUS_WARNING),
            Map.entry("danger", Tokens.STATUS_DANGER),
            Map.entry("info", Tokens.STATUS_INFO),
            Map.entry("success-subtle", Tokens.STATUS_SUCCESS_SUBTLE),
            Map.entry("warning-subtle", Tokens.STATUS_WARNING_SUBTLE),
            Map.entry("danger-subtle", Tokens.STATUS_DANGER_SUBTLE),
            Map.entry("info-subtle", Tokens.STATUS_INFO_SUBTLE));

    private static final Map<String, String> TEXT_TOKENS = Map.ofEntries(
            Map.entry("default", Tokens.TEXT_PRIMARY),
            Map.entry("muted", Tokens.TEXT_MUTED),
            Map.entry("subtle", Tokens.TEXT_SUBTLE),
            Map.entry("accent", Tokens.ACCENT),
            Map.entry("accent-hover", Tokens.ACCENT_HOVER),
            Map.entry("contrast", Tokens.ACCENT_CONTRAST),
            Map.entry("danger-contrast", Tokens.STATUS_DANGER_CONTRAST),
            Map.entry("success", Tokens.STATUS_SUCCESS),
            Map.entry("warning", Tokens.STATUS_WARNING),
            Map.entry("danger", Tokens.STATUS_DANGER),
            Map.entry("info", Tokens.STATUS_INFO));

    private static final Map<String, String> BORDER_TOKENS = Map.of(
            "default", Tokens.BORDER,
            "strong", Tokens.BORDER_STRONG,
            "accent", Tokens.ACCENT,
            "success", Tokens.STATUS_SUCCESS,
            "warning", Tokens.STATUS_WARNING,
            "danger", Tokens.STATUS_DANGER,
            "info", Tokens.STATUS_INFO);

    private static final Map<String, Variant> VARIANTS = Map.of(
            "hover", Variant.HOVER,
            "focus", Variant.FOCUS,
            "active", Variant.ACTIVE,
            "disabled", Variant.DISABLED);

    /** Spacing/sizing scale: one step is 4px (Tailwind's 0.25rem at a 16px root). */
    private static final int SCALE = 4;

    private static final System.Logger LOGGER = System.getLogger("mcsx.style");

    /** Warn once per distinct token — a bad class in a component would otherwise log on every bind. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * Memo for the lenient path, keyed on the raw (post-interpolation) class string. StyleSpec is
     * deeply immutable, so sharing one instance per distinct string is safe. Bounded by a hard cap
     * past which new entries are simply not inserted — interpolated class strings are the only
     * unbounded source and eviction churn would cost more than the occasional cold parse.
     */
    private static final int CACHE_LIMIT = 4096;
    private static final ConcurrentHashMap<String, StyleSpec> CACHE = new ConcurrentHashMap<>();

    private static final StyleSpec EMPTY = parse(null, false);

    private TailwindParser() {
    }

    /**
     * Parses a {@code class} string, <em>skipping</em> any token this subset does not support and
     * warning once per distinct token. One unsupported utility must not take down a whole screen —
     * an author who types {@code w-1/2} should get a nudge and a laid-out screen, not a stack trace.
     * Use {@link #parseStrict} where a typo should fail loudly (the shipped-component guard test).
     */
    public static StyleSpec parse(String classes) {
        if (classes == null) {
            return EMPTY;
        }
        StyleSpec cached = CACHE.get(classes);
        if (cached != null) {
            return cached;
        }
        StyleSpec spec = parse(classes, false);
        if (CACHE.size() < CACHE_LIMIT) {
            CACHE.putIfAbsent(classes, spec);
        }
        return spec;
    }

    /** Parses a {@code class} string, throwing {@link TailwindException} on the first bad token. */
    public static StyleSpec parseStrict(String classes) {
        return parse(classes, true);
    }

    private static StyleSpec parse(String classes, boolean strict) {
        StyleSpec.Builder base = StyleSpec.builder();
        Map<Variant, StyleSpec.Builder> variantBuilders = new EnumMap<>(Variant.class);

        if (classes != null) {
            for (String token : classes.trim().split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                try {
                    applyToken(token, base, variantBuilders);
                } catch (TailwindException e) {
                    if (strict) {
                        throw e;
                    }
                    warnOnce(token, e);
                }
            }
        }

        Map<Variant, StyleSpec> variants = new EnumMap<>(Variant.class);
        for (Map.Entry<Variant, StyleSpec.Builder> entry : variantBuilders.entrySet()) {
            variants.put(entry.getKey(), entry.getValue().build());
        }
        return base.build(variants);
    }

    /**
     * Applies one token. A failing variant token must not register an empty {@code hover:} variant
     * that would make a presentational element clickable for nothing, so a freshly created variant
     * builder is inserted into the map only after the utility applied cleanly. This relies on
     * {@link #applyUtility}'s contract that every branch finishes its throwing parse before
     * mutating the builder — a utility that mutates first would leave a half-written builder.
     */
    private static void applyToken(String token, StyleSpec.Builder base,
                                   Map<Variant, StyleSpec.Builder> variantBuilders) {
        String utility = token;
        Variant variant = null;
        int colon = token.indexOf(':');
        if (colon >= 0) {
            String prefix = token.substring(0, colon);
            variant = VARIANTS.get(prefix);
            if (variant == null) {
                throw new TailwindException("unknown variant prefix '" + prefix + "' in '" + token + "'");
            }
            utility = token.substring(colon + 1);
        }
        if (variant == null) {
            applyUtility(utility, base);
            return;
        }
        StyleSpec.Builder target = variantBuilders.get(variant);
        boolean created = target == null;
        if (created) {
            target = StyleSpec.builder();
        }
        applyUtility(utility, target);
        if (created) {
            variantBuilders.put(variant, target);
        }
    }

    private static void warnOnce(String token, TailwindException cause) {
        if (WARNED.add(token)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "ignoring unsupported class \"" + token + "\": " + cause.getMessage());
        }
    }

    private static void applyUtility(String u, StyleSpec.Builder b) {
        switch (u) {
            case "flex-row" -> b.orientation(Orientation.ROW);
            case "flex-col" -> b.orientation(Orientation.COLUMN);
            case "items-start" -> b.items(Align.START);
            case "items-center" -> b.items(Align.CENTER);
            case "items-end" -> b.items(Align.END);
            case "items-stretch" -> b.items(Align.STRETCH);
            case "justify-start" -> b.justify(Justify.START);
            case "justify-center" -> b.justify(Justify.CENTER);
            case "justify-end" -> b.justify(Justify.END);
            case "justify-between" -> b.justify(Justify.BETWEEN);
            case "justify-around" -> b.justify(Justify.AROUND);
            case "justify-evenly" -> b.justify(Justify.EVENLY);
            case "self-start" -> b.alignSelf(Align.START);
            case "self-center" -> b.alignSelf(Align.CENTER);
            case "self-end" -> b.alignSelf(Align.END);
            case "self-stretch" -> b.alignSelf(Align.STRETCH);
            case "animate-spin" -> b.animation(StyleSpec.Animation.SPIN);
            case "animate-pulse" -> b.animation(StyleSpec.Animation.PULSE);
            case "animate-none" -> b.animation(StyleSpec.Animation.NONE);
            case "transition" -> b.transition(true);
            case "transition-none" -> b.transition(false);
            case "flex-wrap" -> b.wrap(true);
            case "flex-nowrap" -> b.wrap(false);
            case "absolute" -> b.position(StyleSpec.Position.ABSOLUTE);
            case "relative", "static" -> b.position(StyleSpec.Position.STATIC);
            case "grow" -> b.grow(true);
            case "grow-0" -> b.grow(false);
            case "shrink" -> b.shrink(1f);
            case "shrink-0" -> b.shrink(0f);
            case "font-normal" -> b.fontWeight(FontWeight.NORMAL);
            case "font-medium" -> b.fontWeight(FontWeight.MEDIUM);
            case "font-semibold" -> b.fontWeight(FontWeight.SEMIBOLD);
            case "font-bold" -> b.fontWeight(FontWeight.BOLD);
            case "m-auto" -> b.autoMarginX(true).autoMarginY(true);
            case "mx-auto" -> b.autoMarginX(true);
            case "my-auto" -> b.autoMarginY(true);
            case "border" -> b.borderWidthPx(1);
            case "rounded" -> b.cornerPx(RADII.get("sm"));
            default -> applyPrefixed(u, b);
        }
    }

    private static void applyPrefixed(String u, StyleSpec.Builder b) {
        if (u.startsWith("gap-")) {
            b.gapPx(scaled(after(u, "gap-"), u));
        } else if (u.startsWith("rounded-[")) {
            b.cornerPx(arbitraryLengthPx(after(u, "rounded-"), u));
        } else if (CORNER_SIDES.keySet().stream().anyMatch(side -> u.startsWith("rounded-" + side + "-"))) {
            applyCornerSide(u, b);
        } else if (u.startsWith("rounded-")) {
            b.cornerPx(namedRadius(after(u, "rounded-"), u));
        } else if (u.startsWith("p-")) {
            int v = scaled(after(u, "p-"), u);
            b.padLeft(v).padTop(v).padRight(v).padBottom(v);
        } else if (u.startsWith("px-")) {
            int v = scaled(after(u, "px-"), u);
            b.padLeft(v).padRight(v);
        } else if (u.startsWith("py-")) {
            int v = scaled(after(u, "py-"), u);
            b.padTop(v).padBottom(v);
        } else if (u.startsWith("pt-")) {
            b.padTop(scaled(after(u, "pt-"), u));
        } else if (u.startsWith("pr-")) {
            b.padRight(scaled(after(u, "pr-"), u));
        } else if (u.startsWith("pb-")) {
            b.padBottom(scaled(after(u, "pb-"), u));
        } else if (u.startsWith("pl-")) {
            b.padLeft(scaled(after(u, "pl-"), u));
        } else if (u.startsWith("min-w-")) {
            b.minWidthPx(lengthPx(after(u, "min-w-"), u));
        } else if (u.startsWith("max-w-")) {
            b.maxWidthPx(lengthPx(after(u, "max-w-"), u));
        } else if (u.startsWith("min-h-")) {
            b.minHeightPx(lengthPx(after(u, "min-h-"), u));
        } else if (u.startsWith("max-h-")) {
            b.maxHeightPx(lengthPx(after(u, "max-h-"), u));
        } else if (u.startsWith("inset-")) {
            int v = lengthPx(after(u, "inset-"), u);
            b.insetLeft(v).insetTop(v).insetRight(v).insetBottom(v);
        } else if (u.startsWith("left-")) {
            b.insetLeft(lengthPx(after(u, "left-"), u));
        } else if (u.startsWith("top-")) {
            b.insetTop(lengthPx(after(u, "top-"), u));
        } else if (u.startsWith("right-")) {
            b.insetRight(lengthPx(after(u, "right-"), u));
        } else if (u.startsWith("bottom-")) {
            b.insetBottom(lengthPx(after(u, "bottom-"), u));
        } else if (u.startsWith("w-")) {
            b.width(length(after(u, "w-"), u));
        } else if (u.startsWith("h-")) {
            b.height(length(after(u, "h-"), u));
        } else if (u.startsWith("opacity-")) {
            b.opacity(opacity(after(u, "opacity-"), u));
        } else if (u.startsWith("border-")) {
            applyBorder(after(u, "border-"), u, b);
        } else if (u.startsWith("bg-")) {
            b.background(color(after(u, "bg-"), BG_TOKENS, u));
        } else if (u.startsWith("text-")) {
            applyText(after(u, "text-"), u, b);
        } else if (u.startsWith("selection-") || u.startsWith("scrollbar-")) {
            // accepted, but no ModernUI analogue yet — deliberately ignored
        } else {
            throw new TailwindException("unknown utility class '" + u + "'");
        }
    }

    /** Which corners each {@code rounded-<side>} prefix touches, in TL, TR, BR, BL order. */
    private static final Map<String, boolean[]> CORNER_SIDES = Map.ofEntries(
            Map.entry("t", new boolean[]{true, true, false, false}),
            Map.entry("b", new boolean[]{false, false, true, true}),
            Map.entry("l", new boolean[]{true, false, false, true}),
            Map.entry("r", new boolean[]{false, true, true, false}),
            Map.entry("tl", new boolean[]{true, false, false, false}),
            Map.entry("tr", new boolean[]{false, true, false, false}),
            Map.entry("br", new boolean[]{false, false, true, false}),
            Map.entry("bl", new boolean[]{false, false, false, true}));

    /** {@code rounded-t-md}, {@code rounded-bl-lg} — a radius applied to a subset of the corners. */
    private static void applyCornerSide(String u, StyleSpec.Builder b) {
        String rest = after(u, "rounded-");
        int dash = rest.indexOf('-');
        String side = rest.substring(0, dash);
        int radius = namedRadius(rest.substring(dash + 1), u);
        boolean[] corners = CORNER_SIDES.get(side);
        if (corners[0]) {
            b.cornerTopLeftPx(radius);
        }
        if (corners[1]) {
            b.cornerTopRightPx(radius);
        }
        if (corners[2]) {
            b.cornerBottomRightPx(radius);
        }
        if (corners[3]) {
            b.cornerBottomLeftPx(radius);
        }
    }

    /**
     * The corner scale, shared by {@code rounded-md} and {@code rounded-t-md} so the two forms can
     * never drift apart. Bare {@code rounded} is {@code sm}.
     */
    private static final Map<String, Integer> RADII = Map.of(
            "none", 0,
            "sm", 6,
            "md", 10,
            "lg", 14,
            "full", 999);

    private static int namedRadius(String name, String util) {
        Integer named = RADII.get(name);
        if (named != null) {
            return named;
        }
        return isArbitrary(name) ? arbitraryLengthPx(name, util) : requireInt(name, util);
    }

    private static void applyBorder(String suffix, String util, StyleSpec.Builder b) {
        if (isArbitrary(suffix)) {
            b.borderColor(new ColorValue.Literal(parseHex(unwrap(suffix), util)));
        } else if (isInteger(suffix)) {
            b.borderWidthPx(requireInt(suffix, util));
        } else {
            String key = BORDER_TOKENS.get(suffix);
            if (key == null) {
                throw new TailwindException("unknown border utility 'border-" + suffix + "'");
            }
            b.borderColor(new ColorValue.TokenRef(key));
        }
    }

    private static void applyText(String suffix, String util, StyleSpec.Builder b) {
        Integer size = TEXT_SIZES.get(suffix);
        if (size != null) {
            b.fontSizePx(size);
            return;
        }
        switch (suffix) {
            case "left" -> b.textAlign(TextAlign.LEFT);
            case "center" -> b.textAlign(TextAlign.CENTER);
            case "right" -> b.textAlign(TextAlign.RIGHT);
            default -> b.textColor(color(suffix, TEXT_TOKENS, util));
        }
    }

    private static ColorValue color(String suffix, Map<String, String> tokens, String util) {
        if (isArbitrary(suffix)) {
            return new ColorValue.Literal(parseHex(unwrap(suffix), util));
        }
        String key = tokens.get(suffix);
        if (key == null) {
            throw new TailwindException("unknown color token in '" + util
                    + "' (the standard Tailwind palette is not supported; use a theme token or [#hex])");
        }
        return new ColorValue.TokenRef(key);
    }

    private static Length length(String suffix, String util) {
        Length named = Length.named(suffix);
        if (named != null) {
            return named;
        }
        if (suffix.equals("px")) {
            return Length.pixels(1);
        }
        int slash = suffix.indexOf('/');
        if (slash > 0) {
            int numerator = requireInt(suffix.substring(0, slash), util);
            int denominator = requireInt(suffix.substring(slash + 1), util);
            if (denominator <= 0 || numerator <= 0 || numerator > denominator) {
                throw new TailwindException("bad fraction in '" + util + "'");
            }
            return Length.fractionOf((float) numerator / denominator);
        }
        if (isArbitrary(suffix)) {
            return Length.pixels(arbitraryLengthPx(suffix, util));
        }
        return Length.pixels(scaled(suffix, util));
    }

    /** A pixel length from either an arbitrary {@code [12px]} literal or a step on the 4px scale. */
    private static int lengthPx(String suffix, String util) {
        if (isArbitrary(suffix)) {
            return arbitraryLengthPx(suffix, util);
        }
        return scaled(suffix, util);
    }

    private static float opacity(String suffix, String util) {
        float value;
        if (isArbitrary(suffix)) {
            try {
                value = Float.parseFloat(unwrap(suffix));
            } catch (NumberFormatException e) {
                throw new TailwindException("invalid arbitrary opacity in '" + util + "'");
            }
        } else {
            int pct = requireInt(suffix, util);
            value = pct / 100f;
        }
        if (!Float.isFinite(value) || value < 0 || value > 1) {
            throw new TailwindException("opacity must be between 0 and 1 in '" + util + "'");
        }
        return value;
    }

    private static int scaled(String suffix, String util) {
        return requireInt(suffix, util) * SCALE;
    }

    /** Parses an arbitrary length like {@code [120px]} / {@code [8]} (the {@code px} unit is optional). */
    private static int arbitraryLengthPx(String bracketed, String util) {
        String inner = unwrap(bracketed);
        String digits = inner.endsWith("px") ? inner.substring(0, inner.length() - 2) : inner;
        try {
            return Integer.parseInt(digits.trim());
        } catch (NumberFormatException e) {
            throw new TailwindException("invalid arbitrary length in '" + util + "'");
        }
    }

    private static int parseHex(String hex, String util) {
        return Colors.parseHex(hex, util);
    }

    private static String after(String util, String prefix) {
        return util.substring(prefix.length());
    }

    private static boolean isArbitrary(String s) {
        return s.startsWith("[") && s.endsWith("]");
    }

    private static String unwrap(String bracketed) {
        return bracketed.substring(1, bracketed.length() - 1);
    }

    private static boolean isInteger(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int requireInt(String s, String util) {
        if (!isInteger(s)) {
            throw new TailwindException("expected a number in '" + util + "'");
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new TailwindException("number is out of range in '" + util + "'");
        }
    }
}

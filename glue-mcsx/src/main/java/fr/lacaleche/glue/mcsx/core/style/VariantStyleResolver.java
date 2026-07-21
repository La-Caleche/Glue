package fr.lacaleche.glue.mcsx.core.style;

import fr.lacaleche.glue.mcsx.core.bind.BindingResolver;
import fr.lacaleche.glue.mcsx.core.bind.McsxBindException;
import fr.lacaleche.glue.mcsx.core.bind.Scope;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxContent;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.ColorValue;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Length;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;

import java.util.HashMap;
import java.util.Map;

/**
 * The class-string half of the binder: resolves an element's styling ({@code class} + raw box
 * attrs), expanding {@code {name}} interpolation holes — the mechanism behind {@code .mcsx}
 * component variants (the ShadCN {@code cva} analogue) and caller-class merging. Stateless: the
 * {@link Scope} is always passed in, so one resolver serves the whole bind and every deferred
 * re-resolution inside an effect.
 */
public final class VariantStyleResolver {

    private final BindingResolver bindings;

    public VariantStyleResolver(BindingResolver bindings) {
        this.bindings = bindings;
    }

    /** An element's styling ({@code class} interpolated + parsed, then raw box attrs on top). */
    public StyleSpec resolveStyle(McsxElement element, Scope scope) {
        StyleSpec classSpec;
        try {
            classSpec = TailwindParser.parseStrict(
                    interpolateClass(element.attribute("class"), element, scope));
        } catch (TailwindException e) {
            throw new McsxBindException(e.getMessage(), element.line(), element.column());
        }
        return classSpec.merged(rawAttributeStyle(element));
    }

    /**
     * Expands {@code {name}} tokens in a {@code class} string. A {@code {name}} resolves to: the
     * classes of a matching {@code <variants on="name">} block declared as a child of
     * {@code element} (see {@link #resolveVariants}); else the string value of scope prop
     * {@code name} (e.g. {@code {class}} → the caller's class); else empty. Returns the input
     * unchanged when it has no {@code '{'} (the fast path for every base tag).
     */
    private String interpolateClass(String raw, McsxElement element, Scope scope) {
        if (raw == null || raw.indexOf('{') < 0) {
            return raw;
        }
        Map<String, String> variantClasses = resolveVariants(element, scope);
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        while (i < raw.length()) {
            char ch = raw.charAt(i);
            if (ch != '{') {
                out.append(ch);
                i++;
                continue;
            }
            int end = raw.indexOf('}', i);
            if (end < 0) {
                out.append(raw, i, raw.length());
                break;
            }
            String name = raw.substring(i + 1, end).trim();
            String replacement = variantClasses.containsKey(name)
                    ? variantClasses.get(name) : interpolatedValue(name, element, scope);
            if (replacement != null && !replacement.isEmpty()) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
                out.append(replacement);
            }
            i = end + 1;
        }
        return out.toString();
    }

    /**
     * Builds the variant → classes map for an element from its {@code <variants on="dim"
     * default="d">} children: for each, the current value of prop {@code dim} (or {@code d} if
     * unset) selects the matching {@code <case is="…" class="…"/>}. Empty when the element
     * declares no {@code <variants>}.
     */
    private Map<String, String> resolveVariants(McsxElement element, Scope scope) {
        Map<String, String> map = null;
        for (McsxContent child : element.children()) {
            if (!(child instanceof McsxElement block) || !block.tag().equals("variants")) {
                continue;
            }
            String dimension = block.attribute("on");
            String current = softScopeString(dimension, scope);
            String value = current != null ? current : block.attribute("default");
            if (map == null) {
                map = new HashMap<>();
            }
            map.put(dimension, selectCase(block, value));
        }
        return map == null ? Map.of() : map;
    }

    private String selectCase(McsxElement variants, String value) {
        for (McsxContent child : variants.children()) {
            if (child instanceof McsxElement option && option.tag().equals("case")
                    && value != null && value.equals(option.attribute("is"))) {
                String classes = option.attribute("class");
                return classes != null ? classes : "";
            }
        }
        throw new McsxBindException("no <case is=\"" + value + "\"> for <variants on=\""
                + variants.attribute("on") + "\">", variants.line(), variants.column());
    }

    /**
     * The classes a {@code {name}} hole expands to. A bare name is an optional scope prop (absent
     * → contributes nothing, so an unpassed {@code {class}} is silent). A dotted name is a
     * binding on the controller or a {@code <for>} item — typically a {@code Computed<String>} of
     * classes, which is where conditional styling belongs. Reading it inside an effect makes it
     * live.
     */
    public String interpolatedValue(String name, McsxElement element, Scope scope) {
        if (name.indexOf('.') >= 0) {
            Object value = bindings.evaluate(name, scope, element);
            return value == null ? null : value.toString();
        }
        return softScopeString(name, scope);
    }

    /** The string value of a scope prop, or null if not in scope (used for optional variant props). */
    private String softScopeString(String name, Scope scope) {
        for (Scope s = scope; s != null; s = s.parent()) {
            if (s.name().equals(name)) {
                Object value = BindingResolver.unwrap(s.value());
                return value == null ? null : value.toString();
            }
        }
        return null;
    }

    private StyleSpec rawAttributeStyle(McsxElement element) {
        StyleSpec.Builder builder = StyleSpec.builder();
        String bg = element.attribute("bg");
        if (bg != null) {
            builder.background(new ColorValue.Literal(Colors.parseHex(bg, "bg")));
        }
        Integer pad = optionalInt(element.attribute("pad"));
        if (pad != null) {
            builder.padLeft(pad).padTop(pad).padRight(pad).padBottom(pad);
        }
        String color = element.attribute("color");
        if (color != null) {
            builder.textColor(new ColorValue.Literal(Colors.parseHex(color, "color")));
        }
        Integer size = optionalInt(element.attribute("size"));
        if (size != null) {
            builder.fontSizePx(size);
        }
        Length width = optionalLength(element.attribute("w"));
        if (width != null) {
            builder.width(width);
        }
        Length height = optionalLength(element.attribute("h"));
        if (height != null) {
            builder.height(height);
        }
        if (element.attribute("grow") != null) {
            builder.grow(true);
        }
        if ("row".equals(element.attribute("dir"))) {
            builder.orientation(Orientation.ROW);
        }
        return builder.build();
    }

    /**
     * A width/height style attr ({@code w}/{@code h}): a shared keyword or an integer, else null.
     * The {@code .mcsx} attribute namespace is shared between styling and component props, so an
     * uninterpretable value is silently skipped (it belongs to the component, not the style
     * layer) rather than failing the screen.
     */
    private static Length optionalLength(String value) {
        if (value == null) {
            return null;
        }
        Length named = Length.named(value);
        if (named != null) {
            return named;
        }
        Integer px = optionalInt(value);
        return px != null ? Length.pixels(px) : null;
    }

    /** An integer style attr ({@code size}/{@code pad}), or null if absent or non-numeric — a
     *  non-numeric value is a component prop (e.g. {@code size="sm"}), not a font size, so it's skipped. */
    public static Integer optionalInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

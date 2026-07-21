package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.ColorValue;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.FontWeight;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import icyllis.modernui.widget.TextView;

import java.util.function.Supplier;

/**
 * The CSS-inherited text defaults (colour / size / weight / font) in force for a subtree: what a
 * {@code <text>} or {@code <icon>} renders as when it declares none of its own. A container overlays
 * its own text classes onto the value it received and passes the result to its children, so an icon
 * inside a destructive menu item turns red with its label and needs no styling of its own.
 *
 * <p>One immutable value swapped under try/finally — never three parallel save/restore locals, whose
 * protocol is exactly what leaked a component root style once before.
 */
record InheritedText(ColorValue textColor, Float fontSize, FontWeight fontWeight, String font) {

    /** UA-stylesheet-level default text size (sp) — legibility, not design; classes override it. */
    private static final float DEFAULT_TEXT_SIZE_SP = 13f;

    static final InheritedText NONE = new InheritedText(null, null, null, null);

    /** The root of a document's inheritance chain: nothing inherited, and nothing to re-read. */
    static final Supplier<InheritedText> ROOT = () -> NONE;

    /** The defaults a child sees: this subtree's, with the element's own text classes on top. */
    InheritedText overlaidBy(StyleSpec spec) {
        return overlaidBy(spec, null);
    }

    InheritedText overlaidBy(StyleSpec spec, String ownFont) {
        if (spec.textColor() == null && spec.fontSizePx() == null
                && spec.fontWeight() == null && ownFont == null) {
            return this;
        }
        return new InheritedText(
                spec.textColor() != null ? spec.textColor() : textColor,
                spec.fontSizePx() != null ? Float.valueOf(spec.fontSizePx()) : fontSize,
                spec.fontWeight() != null ? spec.fontWeight() : fontWeight,
                ownFont != null ? ownFont : font);
    }

    /**
     * The colour to fall back to: the subtree's, else the theme's primary text token — so untouched
     * text tracks the active theme rather than a hardcoded white.
     */
    int resolvedColor() {
        return textColor != null
                ? ViewStyles.resolve(textColor)
                : Themes.active().color(Tokens.TEXT_PRIMARY);
    }

    /**
     * Seeds a text view with the subtree's colour, size, weight and font before its own values go on.
     * Every channel is written, present or absent: this is the reset baseline a reactive restyle
     * re-runs, so a class that drops {@code font-bold} falls back to the weight it <em>inherits</em>
     * rather than staying bold — and writing the weight only when one is inherited is exactly what
     * left it stale.
     */
    void applyTo(TextView text) {
        text.setTextColor(resolvedColor());
        text.setTextSize(fontSize != null ? fontSize : DEFAULT_TEXT_SIZE_SP);
        ViewStyles.applyFontWeight(text, fontWeight);
        FontRegistry.getInstance().bindText(text, font);
    }
}

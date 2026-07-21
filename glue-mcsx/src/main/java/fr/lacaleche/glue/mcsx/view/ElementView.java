package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec;
import icyllis.modernui.view.View;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A bound View and the style it presents to its parent for layout. The style is a supplier rather
 * than a snapshot because a reactive {@code class} must be able to change how the parent lays the
 * element out — the parent re-resolves it inside the effect that owns the child's layout params.
 */
record ElementView(View view, Supplier<StyleSpec> layoutStyle) {

    ElementView {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(layoutStyle, "layoutStyle");
    }
}

package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.view.FontRegistry;
import fr.lacaleche.glue.mcsx.view.IconView;
import icyllis.modernui.core.Context;

/** Chrome shared by the tab strip and the floating window, so the two cannot drift apart. */
final class DockChrome {

    /** The glyph name every close affordance uses. */
    private static final String CLOSE_GLYPH = "close";

    /** Matches the tab title's size; the glyph fills its em box, so it reads larger than text would. */
    static final int CLOSE_ICON_SIZE = 9;

    private DockChrome() {
    }

    /**
     * The × that closes a tab or a floating window.
     *
     * <p>Deliberately an {@link IconView} bound to the icon font rather than a {@code TextView}
     * holding a literal {@code ×}: U+00D7 is itself mapped by the icon font, so a literal rendered
     * through whatever typeface won resolution came out as a full-em Font Awesome glyph — the close
     * button was several times its intended size. Asking for the glyph by name pins both the font
     * and the size.
     */
    static IconView closeButton(Context context, Runnable onClose) {
        IconView close = new IconView(
                context, FontRegistry.DEFAULT_ICONS, CLOSE_GLYPH, null, CLOSE_ICON_SIZE);
        close.setPadding(5, 0, 5, 0);
        close.setClickable(true);
        close.setOnClickListener(v -> onClose.run());
        return close;
    }
}

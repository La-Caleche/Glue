package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockLeaf;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Align;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import fr.lacaleche.glue.mcsx.view.FlexLayout;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

/**
 * One leaf of the dock tree on screen: a glass card holding the tab strip and the active pane's
 * content. Content views are owned by the host and only ever <em>reparented</em> here, so a pane
 * keeps its state (scroll offsets, text, bound signals) as it moves around the workspace.
 */
final class LeafPaneView extends FlexLayout {

    private final DockHostView host;
    private final TabStripView strip;
    private final FrameLayout content;
    private DockLeaf leaf;

    /**
     * @param framed  docked leaves paint their own card; a leaf inside a floating window does not,
     *                the window chrome already frames it
     * @param floatId the window this leaf lives in, or null when docked
     */
    LeafPaneView(Context context, DockHostView host, DockLeaf leaf, String floatId, boolean framed) {
        super(context);
        this.host = host;
        setOrientation(Orientation.COLUMN);
        setAlignItems(Align.STRETCH);
        if (framed) {
            ShapeDrawable card = new ShapeDrawable();
            card.setShape(ShapeDrawable.RECTANGLE);
            card.setCornerRadius(10);
            setBackground(card);
            Themed.onTheme(this, theme -> {
                card.setColor(theme.color(Tokens.SURFACE_1));
                card.setStroke(1, theme.color(Tokens.BORDER));
            });
        }
        strip = new TabStripView(context, host, floatId);
        addView(strip, new LayoutParams(LayoutParams.MATCH_PARENT, TabStripView.HEIGHT));
        content = new FrameLayout(context);
        LayoutParams contentParams = new LayoutParams(LayoutParams.MATCH_PARENT, 0);
        contentParams.grow = 1f;
        addView(content, contentParams);
        update(leaf);
    }

    /** Refreshes strip styling and the shown content; safe to call mid-press (views are reused). */
    void update(DockLeaf newLeaf) {
        // records compare by value: during a splitter or float drag no leaf changes, and this
        // early-return spares the whole restyle chain on every pointer move
        if (newLeaf.equals(leaf)) {
            return;
        }
        String previous = leaf != null ? leaf.active() : null;
        leaf = newLeaf;
        strip.setLeaf(newLeaf);
        if (!java.util.Objects.equals(previous, newLeaf.active())) {
            content.removeAllViews();
            if (newLeaf.active() != null) {
                View pane = host.contentView(newLeaf.active());
                if (pane.getParent() instanceof ViewGroup parent) {
                    parent.removeView(pane);
                }
                content.addView(pane, new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
        }
    }

    DockLeaf leaf() {
        return leaf;
    }
}

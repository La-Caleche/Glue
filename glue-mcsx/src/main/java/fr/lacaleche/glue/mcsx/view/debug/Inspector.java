package fr.lacaleche.glue.mcsx.view.debug;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Align;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import fr.lacaleche.glue.mcsx.view.FlexLayout;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

/**
 * Mounts the {@link InspectorView} picker and its report panel over an existing UI.
 *
 * <p>Wrap the screen's root before handing it to the host:
 * {@snippet : View root = Inspector.wrap(context, myRoot); }
 * The picker sits above the inspected tree and swallows input while mounted, so the UI underneath
 * cannot change shape while it is being measured.
 */
public final class Inspector {

    /** Wide enough for the dump's longest lines without hiding the UI being inspected. */
    private static final int PANEL_W = 380;

    private Inspector() {
    }

    /**
     * @return a container holding {@code content}, the picker, and the report panel; {@code content}
     *     keeps its own layout params and is not modified
     */
    public static View wrap(Context context, View content) {
        FrameLayout root = new FrameLayout(context);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView report = new TextView(context);
        InspectorView picker = new InspectorView(context, content, report);
        picker.stylePanel();

        root.addView(picker, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(panel(context, report), new FrameLayout.LayoutParams(
                PANEL_W, ViewGroup.LayoutParams.MATCH_PARENT, icyllis.modernui.view.Gravity.RIGHT));
        return root;
    }

    private static View panel(Context context, TextView report) {
        FlexLayout column = new FlexLayout(context);
        column.setOrientation(Orientation.COLUMN);
        column.setAlignItems(Align.STRETCH);

        ShapeDrawable background = new ShapeDrawable();
        background.setShape(ShapeDrawable.RECTANGLE);
        background.setColor(Themes.active().color(Tokens.SURFACE_2));
        background.setStroke(1, Themes.active().color(Tokens.BORDER_STRONG));
        column.setBackground(background);

        TextView title = new TextView(context);
        title.setText("MCSX Inspector");
        title.setTextSize(24f);
        title.setTextColor(Themes.active().color(Tokens.ACCENT));
        title.setPadding(8, 8, 8, 4);
        column.addView(title, new FlexLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(report, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FlexLayout.LayoutParams scrollParams = new FlexLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        scrollParams.grow = 1f;
        column.addView(scroll, scrollParams);
        return column;
    }
}

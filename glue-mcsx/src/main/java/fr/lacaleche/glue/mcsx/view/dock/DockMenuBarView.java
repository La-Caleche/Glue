package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Align;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import fr.lacaleche.glue.mcsx.view.FlexLayout;
import fr.lacaleche.glue.mcsx.view.KeyBindings;
import fr.lacaleche.glue.mcsx.view.OverlayHost;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The workspace's built-in menu bar (ImGui-style), on by default and removable per
 * {@link DockConfig.Builder#menuBar}: <b>File</b> (Close) and <b>Views</b>, which lists every
 * registered pane with its open state — clicking one toggles it through the place-remembering
 * {@link McsxDockspace#togglePane}.
 *
 * <p>The bar also claims the workspace shortcuts it advertises: {@code Ctrl+Q} closes the
 * workspace and {@code Ctrl+1}…{@code Ctrl+9} toggle the first nine panes in registration order.
 * They live on the shared {@link KeyBindings}, so they fire wherever dock focus happens to be —
 * but never while the game holds input.</p>
 *
 * <p>Dropdowns are overlay layers, so an outside click or Esc dismisses them like any other menu.
 * The item list is rebuilt on every click from {@link McsxDockspace#openPanes()} — the bar itself
 * holds no state to go stale.</p>
 */
final class DockMenuBarView extends FlexLayout {

    /** The number of panes that get a {@code Ctrl+digit} toggle. */
    private static final int SHORTCUT_PANES = 9;

    private record Item(String label, String shortcut, Runnable action) {
    }

    private final McsxDockspace dockspace;
    private final List<DockPane> panes;
    private final OverlayHost overlays;

    DockMenuBarView(Context context, McsxDockspace dockspace, List<DockPane> panes,
                    OverlayHost overlays) {
        super(context);
        this.dockspace = dockspace;
        this.panes = panes;
        this.overlays = overlays;
        setOrientation(Orientation.ROW);
        setAlignItems(Align.CENTER);
        setGap(4);
        setPadding(8, 3, 8, 3);
        ColorDrawable background = new ColorDrawable();
        setBackground(background);
        Themed.onTheme(this, theme -> background.setColor(theme.color(Tokens.SURFACE_2)));

        addEntry("File", this::fileItems);
        addEntry("Views", this::viewItems);
        registerShortcuts();
    }

    private void registerShortcuts() {
        KeyBindings keys = overlays.keyBindings();
        keys.register("ctrl+q", this::closeWorkspace);
        for (int i = 0; i < Math.min(panes.size(), SHORTCUT_PANES); i++) {
            String paneId = panes.get(i).id();
            keys.register("ctrl+" + (i + 1), () -> dockspace.togglePane(paneId));
        }
    }

    private void closeWorkspace() {
        Minecraft.getInstance().schedule(dockspace::close);
    }

    private void addEntry(String label, Supplier<List<Item>> items) {
        TextView entry = new TextView(getContext());
        entry.setText(label);
        entry.setTextSize(13f);
        entry.setPadding(8, 2, 8, 2);
        entry.setClickable(true);
        Themed.onTheme(entry, theme -> entry.setTextColor(theme.color(Tokens.TEXT_PRIMARY)));
        entry.setOnClickListener(v -> openDropdown(entry, items.get()));
        addView(entry, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    private List<Item> fileItems() {
        return List.of(new Item("Close", "Ctrl+Q", this::closeWorkspace));
    }

    private List<Item> viewItems() {
        List<Item> items = new ArrayList<>(panes.size());
        for (int i = 0; i < panes.size(); i++) {
            DockPane pane = panes.get(i);
            boolean open = dockspace.openPanes().contains(pane.id());
            items.add(new Item((open ? "✓  " : "    ") + pane.title(),
                    i < SHORTCUT_PANES ? "Ctrl+" + (i + 1) : null,
                    () -> dockspace.togglePane(pane.id())));
        }
        return items;
    }

    private void openDropdown(View anchor, List<Item> items) {
        FlexLayout panel = new FlexLayout(getContext());
        panel.setOrientation(Orientation.COLUMN);
        panel.setPadding(4, 4, 4, 4);
        ShapeDrawable background = new ShapeDrawable();
        background.setShape(ShapeDrawable.RECTANGLE);
        background.setCornerRadius(6);
        panel.setBackground(background);
        Themed.onTheme(panel, theme -> {
            background.setColor(theme.color(Tokens.SURFACE_2));
            background.setStroke(1, theme.color(Tokens.BORDER_STRONG));
        });
        for (Item item : items) {
            panel.addView(dropdownRow(item),
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        // The overlay host fills the window, so the anchor's window position is the panel's margin.
        int[] location = new int[2];
        anchor.getLocationInWindow(location);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT);
        params.leftMargin = location[0];
        params.topMargin = location[1] + anchor.getHeight();
        overlays.open(this, panel, params, false, () -> overlays.close(this));
    }

    private View dropdownRow(Item item) {
        FlexLayout row = new FlexLayout(getContext());
        row.setOrientation(Orientation.ROW);
        row.setAlignItems(Align.CENTER);
        row.setGap(24);
        row.setPadding(10, 4, 10, 4);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            overlays.close(this);
            item.action().run();
        });

        TextView label = new TextView(getContext());
        label.setText(item.label());
        label.setTextSize(13f);
        Themed.onTheme(label, theme -> label.setTextColor(theme.color(Tokens.TEXT_PRIMARY)));
        LayoutParams grow = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        grow.grow = 1f;
        row.addView(label, grow);

        if (item.shortcut() != null) {
            TextView shortcut = new TextView(getContext());
            shortcut.setText(item.shortcut());
            shortcut.setTextSize(12f);
            Themed.onTheme(shortcut,
                    theme -> shortcut.setTextColor(theme.color(Tokens.TEXT_MUTED)));
            row.addView(shortcut, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        return row;
    }
}

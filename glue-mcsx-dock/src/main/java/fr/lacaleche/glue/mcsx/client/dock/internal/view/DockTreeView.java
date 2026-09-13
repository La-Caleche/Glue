package fr.lacaleche.glue.mcsx.client.dock.internal.view;

import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockGeometry;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockRect;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockNode;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.ViewGroup;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class DockTreeView extends ViewGroup {

    private final DockHostView host;
    private final DockWindowView ownerWindow;
    private final DockThemeBinding theme;
    private final Map<DockTabs, DockLeafView> leaves = new IdentityHashMap<>();
    private final List<SplitterView> splitters = new ArrayList<>();
    private DockNode node;
    private DockGeometry.Solved solved = DockGeometry.solve(null, new DockRect(0, 0, 0, 0), 0);
    private int splitterSize;

    DockTreeView(Context context, DockHostView host, DockWindowView ownerWindow) {
        super(context);
        this.host = host;
        this.ownerWindow = ownerWindow;
        this.theme = new DockThemeBinding(this, host.theme(), value -> {
            this.splitterSize = value.get(ThemeTokens.DOCK_METRICS).splitterSize();
            this.requestLayout();
        });
    }

    void setNode(DockNode node, boolean structural) {
        if (!structural && this.node == node) return;

        this.node = node;
        List<DockTabs> tabs = new ArrayList<>();
        collect(node, tabs);
        if (structural || !this.updateExisting(tabs)) {
            this.rebuild(tabs);
        }
        this.requestLayout();
    }

    DockGeometry.Solved solved() {
        return this.solved;
    }

    DockWindowView ownerWindow() {
        return this.ownerWindow;
    }

    /**
     * The leaf presenting this group. A just-replaced group — activation swaps the node within one
     * event batch — is still keyed under its predecessor until the next measure, so an identity miss
     * falls back to the strip's tab list, the same rule {@code updateExisting} retains leaves by.
     */
    DockLeafView leaf(DockTabs group) {
        DockLeafView view = this.leaves.get(group);
        if (view != null) return view;

        DockTabs previous = findSameTabs(this.leaves, group);
        return previous == null ? null : this.leaves.get(previous);
    }

    boolean remountPane(String paneId) {
        for (DockLeafView leaf : this.leaves.values()) {
            if (leaf.remountPane(paneId)) return true;
        }
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.theme.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        this.theme.detach();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        this.solved = DockGeometry.solve(this.node, new DockRect(0, 0, width, height), this.splitterSize);
        for (Map.Entry<DockTabs, DockRect> entry : this.solved.tabs().entrySet()) {
            DockLeafView view = this.leaves.get(entry.getKey());
            DockRect rect = entry.getValue();
            view.measure(exact(rect.width()), exact(rect.height()));
        }
        for (int index = 0; index < this.splitters.size(); index++) {
            DockGeometry.Splitter splitter = this.solved.splitters().get(index);
            this.splitters.get(index).setSplitter(splitter);
            this.splitters.get(index).measure(exact(splitter.rect().width()), exact(splitter.rect().height()));
        }
        this.setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (Map.Entry<DockTabs, DockRect> entry : this.solved.tabs().entrySet()) {
            DockRect rect = entry.getValue();
            this.leaves.get(entry.getKey()).layout(rect.x(), rect.y(), rect.right(), rect.bottom());
        }
        for (int index = 0; index < this.splitters.size(); index++) {
            DockRect rect = this.solved.splitters().get(index).rect();
            this.splitters.get(index).layout(rect.x(), rect.y(), rect.right(), rect.bottom());
        }
    }

    private boolean updateExisting(List<DockTabs> groups) {
        if (groups.size() != this.leaves.size()) return false;

        Map<DockTabs, DockLeafView> available = new IdentityHashMap<>(this.leaves);
        Map<DockTabs, DockLeafView> replacements = new IdentityHashMap<>();
        for (DockTabs group : groups) {
            DockLeafView view = available.remove(group);
            if (view == null) {
                DockTabs previous = findSameTabs(available, group);
                if (previous == null) return false;
                view = available.remove(previous);
            }
            view.update(group);
            replacements.put(group, view);
        }
        this.leaves.clear();
        this.leaves.putAll(replacements);
        return true;
    }

    private void rebuild(List<DockTabs> groups) {
        Map<DockTabs, DockLeafView> previous = new IdentityHashMap<>(this.leaves);
        this.removeAllViews();
        this.leaves.clear();
        this.splitters.clear();
        for (DockTabs group : groups) {
            DockLeafView view = previous.remove(group);
            if (view == null) view = new DockLeafView(this.getContext(), this.host, this.ownerWindow);
            view.update(group);
            this.leaves.put(group, view);
            this.addView(view);
        }
        int count = countSplitters(this.node);
        for (int index = 0; index < count; index++) {
            SplitterView splitter = new SplitterView(this.getContext(), this.host);
            this.splitters.add(splitter);
            this.addView(splitter);
        }
    }

    private static void collect(DockNode node, List<DockTabs> tabs) {
        if (node instanceof DockTabs group) tabs.add(group);
        else if (node instanceof DockSplit split) {
            for (DockNode child : split.children()) collect(child, tabs);
        }
    }

    private static int countSplitters(DockNode node) {
        if (!(node instanceof DockSplit split)) return 0;

        int count = split.children().size() - 1;
        for (DockNode child : split.children()) count += countSplitters(child);
        return count;
    }

    private static DockTabs findSameTabs(Map<DockTabs, DockLeafView> candidates, DockTabs group) {
        for (DockTabs candidate : candidates.keySet()) {
            if (candidate.tabs().equals(group.tabs())) return candidate;
        }
        return null;
    }

    private static int exact(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }
}

package fr.lacaleche.glue.mcsx.client.dock.internal.layout;

import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Objects;

/**
 * A resolved destination for dragged dock content. Resolution itself lives in {@link DockGuides},
 * which derives every accepted region from the same rectangles it paints.
 *
 * @param index for a tab-group center drop, the strip position to insert at, or {@link #APPEND};
 *              always {@link #APPEND} for every other target
 */
@Environment(EnvType.CLIENT)
public record DropTarget(Kind kind, DockTabs tabs, Zone zone, int index) {

    public enum Kind {
        ROOT,
        TABS
    }

    public enum Zone {
        CENTER,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    /** The insertion index meaning "after the destination strip's last tab". */
    public static final int APPEND = -1;

    public DropTarget {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(zone, "zone");
        if ((kind == Kind.ROOT) == (tabs != null)) {
            throw new IllegalArgumentException("Only tab targets identify a tab group");
        }
        if (index < APPEND) throw new IllegalArgumentException("Invalid insertion index: " + index);
        if (index != APPEND && (kind != Kind.TABS || zone != Zone.CENTER)) {
            throw new IllegalArgumentException("Only tab-group center drops carry an insertion index");
        }
    }

    public DropTarget(Kind kind, DockTabs tabs, Zone zone) {
        this(kind, tabs, zone, APPEND);
    }

    /** One leaf eligible for docking, top-most first, with its rectangle in stage coordinates. */
    public record TabsHit(DockTabs tabs, DockWindow window, DockRect rect) {

        public TabsHit {
            Objects.requireNonNull(tabs, "tabs");
            Objects.requireNonNull(rect, "rect");
        }
    }
}

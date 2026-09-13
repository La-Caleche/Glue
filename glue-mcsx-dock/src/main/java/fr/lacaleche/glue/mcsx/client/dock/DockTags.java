package fr.lacaleche.glue.mcsx.client.dock;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Stable View tags on dock chrome. Tools and tests locate dock parts through these tags and
 * {@code findViewWithTag}/{@code findViewByPredicate} instead of the internal view types. A pane's
 * tab carries the pane id itself as its tag.
 */
@Environment(EnvType.CLIENT)
public final class DockTags {

    public static final String HOST = "dock-host";
    public static final String SPLITTER = "dock-splitter";
    public static final String WINDOW = "dock-window";
    public static final String MOVE_AREA = "dock-move-area";
    public static final String MAXIMIZE = "dock-maximize-pane";

    private DockTags() {
    }
}

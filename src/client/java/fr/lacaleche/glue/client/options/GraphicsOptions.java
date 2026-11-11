package fr.lacaleche.glue.client.options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The graphics settings pages mods add beside the game's video settings: one button each in the video settings,
 * and one page each in Sodium's settings screen while Sodium is installed. Pages show in the order they were
 * registered.
 */
public final class GraphicsOptions {

    private static final List<GraphicsOptionsPage<?>> PAGES = new ArrayList<>();

    private GraphicsOptions() {
    }

    /** Adds {@code page}. Call it from a client initializer, once per page. */
    public static void register(GraphicsOptionsPage<?> page) {
        PAGES.add(Objects.requireNonNull(page));
    }

    /** The registered pages, in order. */
    public static List<GraphicsOptionsPage<?>> pages() {
        return Collections.unmodifiableList(PAGES);
    }
}

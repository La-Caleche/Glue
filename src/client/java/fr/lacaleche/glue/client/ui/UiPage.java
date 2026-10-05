package fr.lacaleche.glue.client.ui;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One page of a {@link UiScreen}. A page is built each time it is shown, including after the window
 * is resized, and closed each time it is hidden, so it costs nothing while it is not on screen. Every
 * method runs on the client render thread.
 */
public interface UiPage {

    /** The page's name in the sidebar. */
    Component title();

    /** Builds this page's widgets into the content area; called each time the page is shown. */
    void build(UiPageBuilder builder);

    /** Called when the page is hidden or its screen closes; releases what {@link #build} took. */
    default void close() {
    }

    /**
     * Why the page cannot be shown now, such as needing a world, or null when it can. An unavailable
     * page is listed muted and shows this reason in place of its content.
     */
    default @Nullable Component unavailableReason() {
        return null;
    }
}

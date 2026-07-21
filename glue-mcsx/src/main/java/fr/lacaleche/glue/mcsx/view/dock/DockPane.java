package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxDocument;
import fr.lacaleche.glue.mcsx.view.ComponentRegistry;
import fr.lacaleche.glue.mcsx.view.OverlayHost;
import fr.lacaleche.glue.mcsx.view.ViewBinder;
import fr.lacaleche.glue.mcsx.view.ViewInstance;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;

import java.util.function.Supplier;

/**
 * One dockable pane: an id the layout refers to, the chrome shown in its tab, and the factory for
 * its content view. Content is created lazily the first time the pane becomes visible and then
 * reparented — never rebuilt — as the pane moves between leaves, windows and drops, so pane state
 * survives every layout mutation.
 *
 * @param icon a name from the {@code mcsx:icons} font descriptor, or null
 */
public record DockPane(String id, String title, String icon, PaneContent content) {

    /** The reserved pane id whose content is the embedded game viewport. */
    public static final String VIEWPORT_ID = "viewport";

    /** Builds (once) and later disposes the pane's content view. */
    public interface PaneContent {

        View create(Context context);

        /** Builds into a dockspace-wide transient layer when the content uses MCSX overlays. */
        default View create(Context context, OverlayHost overlays) {
            return create(context);
        }

        /** Tears down whatever {@link #create} allocated; called when the dockspace closes. */
        default void dispose() {
        }
    }

    /**
     * A pane whose content is a bound {@code .mcsx} document. The bind happens on first show, on
     * the UI thread; the created effects are disposed with the dockspace.
     */
    public static DockPane ofDocument(String id, String title, String icon,
                                      McsxDocument document, Supplier<ScreenController> controller,
                                      ComponentRegistry registry, ViewBinder.DocumentResolver resolver) {
        return new DockPane(id, title, icon, new PaneContent() {
            private ViewInstance instance;

            @Override
            public View create(Context context) {
                return create(context, null);
            }

            @Override
            public View create(Context context, OverlayHost overlays) {
                instance = ViewBinder.bind(document, controller.get(), context, registry, resolver, overlays);
                return instance.root();
            }

            @Override
            public void dispose() {
                if (instance != null) {
                    instance.close();
                    instance = null;
                }
            }
        });
    }
}

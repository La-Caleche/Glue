package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockIds;
import fr.lacaleche.glue.mcsx.core.dock.DockLayout;
import fr.lacaleche.glue.mcsx.core.dock.DockLeaf;
import fr.lacaleche.glue.mcsx.core.theme.Theme;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import fr.lacaleche.glue.mcsx.view.OverlayHost;
import icyllis.modernui.ModernUI;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.Looper;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.ViewRoot;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The dock chrome bakes no colour: every themed view subscribes to {@link Themes} while attached
 * (via {@link Themed}), so toggling the theme over an open dockspace repaints it, and a detached
 * workspace's chrome holds no effect on the global theme signal — the leak the overlay host's
 * detach teardown already guards against.
 */
class DockThemeRepaintTest {

    /** {@link ViewGroup}'s constructor reads a styleable off the theme, so the stub must supply one. */
    private static final class HeadlessContext extends Context {

        private final Resources resources = Resources.getSystem();
        private final Resources.Theme theme = resources.newTheme();

        @Override
        public Resources getResources() {
            return resources;
        }

        @Override
        public void setTheme(ResourceId resId) {
        }

        @Override
        public Resources.Theme getTheme() {
            return theme;
        }

        @Override
        public Object getSystemService(String name) {
            return null;
        }
    }

    /**
     * A window that never draws. {@code setView} runs the real attach dispatch, and children
     * added/removed under it get the real attach/detach lifecycle — what arms and disarms the
     * chrome's theme subscriptions.
     */
    private static final class HeadlessRoot extends ViewRoot {

        @Override
        protected Canvas beginDrawLocked(int width, int height) {
            return null;
        }

        @Override
        protected void endDrawLocked(@NonNull Canvas canvas) {
        }

        @Override
        public void playSoundEffect(int effectId) {
        }

        @Override
        public boolean performHapticFeedback(int effectId, boolean always) {
            return false;
        }
    }

    /**
     * {@code ViewRoot} preloads text/widget classes that reach for the singleton, and its layout
     * paths run {@code Core.checkUiThread()}, so the test thread must register as the UI thread.
     * {@code initUiThread} claims the thread before it prepares the looper, so when an earlier
     * suite already prepared this thread's looper the claim still sticks — swallow the re-prepare.
     */
    @BeforeEach
    void prepareRuntime() {
        if (ModernUI.getInstance() == null) {
            new ModernUI();
        }
        if (Core.getUiThread() == null) {
            try {
                Core.initUiThread();
            } catch (RuntimeException alreadyPrepared) {
                assertEquals(Thread.currentThread(), Core.getUiThread());
            }
        }
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Themes.active(Themes.OBSIDIAN);
    }

    @AfterEach
    void restoreTheme() {
        Themes.active(Themes.OBSIDIAN);
    }

    @Test
    void attachedDockChromeRepaintsOnAThemeSwitch() {
        Context context = new HeadlessContext();
        FrameLayout window = attachedWindow(context);
        DockHostView host = newHost(context);
        window.addView(host);

        assertEquals(backdrop(Themes.OBSIDIAN), backgroundColor(host));
        assertEquals(textColor(context, Themes.OBSIDIAN), activeTabTitle(host).getCurrentTextColor());

        Themes.toggle();

        assertEquals(backdrop(Themes.FROST), backgroundColor(host),
                "the workspace backdrop must follow a live theme switch");
        assertEquals(textColor(context, Themes.FROST), activeTabTitle(host).getCurrentTextColor(),
                "a tab title deep in the chrome must follow a live theme switch");
    }

    @Test
    void detachedDockChromeStopsTrackingTheTheme() {
        Context context = new HeadlessContext();
        FrameLayout window = attachedWindow(context);
        DockHostView host = newHost(context);
        window.addView(host);
        Themes.toggle();
        assertEquals(backdrop(Themes.FROST), backgroundColor(host));

        window.removeView(host);
        Themes.toggle();

        assertEquals(backdrop(Themes.FROST), backgroundColor(host),
                "a detached chrome view's theme effect is disposed — the write must not reach it");
        assertEquals(textColor(context, Themes.FROST), activeTabTitle(host).getCurrentTextColor());

        window.addView(host);
        assertEquals(backdrop(Themes.OBSIDIAN), backgroundColor(host),
                "re-attaching subscribes afresh and catches the chrome up to the active theme");
    }

    private static FrameLayout attachedWindow(Context context) {
        FrameLayout window = new FrameLayout(context);
        new HeadlessRoot().setView(window);
        return window;
    }

    private static DockHostView newHost(Context context) {
        DockPane pane = new DockPane("demo", "Demo", null, View::new);
        DockLayout layout = new DockLayout(new DockLeaf("leaf-1", List.of("demo"), "demo"), List.of());
        return new DockHostView(context, List.of(pane), layout, new DockIds(),
                mutated -> {
                }, new OverlayHost(context));
    }

    private static long backgroundColor(View view) {
        return assertInstanceOf(ColorDrawable.class, view.getBackground()).getColor();
    }

    /** host → docked tree → leaf card → tab strip → active tab → its title label. */
    private static TextView activeTabTitle(DockHostView host) {
        ViewGroup tree = (ViewGroup) host.getChildAt(0);
        ViewGroup leaf = (ViewGroup) tree.getChildAt(0);
        ViewGroup strip = (ViewGroup) leaf.getChildAt(0);
        ViewGroup tab = (ViewGroup) strip.getChildAt(0);
        return assertInstanceOf(TextView.class, tab.getChildAt(0));
    }

    /** {@code ColorDrawable} packs its colour, so the expectation goes through the same constructor. */
    private static long backdrop(Theme theme) {
        return new ColorDrawable(theme.color(Tokens.SURFACE_BASE)).getColor();
    }

    /** {@code TextView} packs its colour too; round-trip the expectation through a probe view. */
    private static long textColor(Context context, Theme theme) {
        TextView probe = new TextView(context);
        probe.setTextColor(theme.color(Tokens.TEXT_PRIMARY));
        return probe.getCurrentTextColor();
    }
}

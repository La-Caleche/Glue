package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockIds;
import fr.lacaleche.glue.mcsx.core.dock.DockLayout;
import fr.lacaleche.glue.mcsx.core.dock.DockOps;
import fr.lacaleche.glue.mcsx.mui.MuiModApi;
import fr.lacaleche.glue.mcsx.mui.MuiScreen;
import fr.lacaleche.glue.mcsx.view.OverlayHost;
import fr.lacaleche.glue.mcsx.view.debug.Inspector;
import fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding;
import fr.lacaleche.glue.mcsx.viewport.ViewportInput;
import icyllis.modernui.core.Core;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A dockspace: the game embedded in a central viewport pane with dockable panes around it, over
 * the running world — split tree, tab strips, drag-to-dock, floating windows, persisted layout.
 * The public handle: one call opens the whole thing, {@link #close()} tears it down and restores
 * the window.
 *
 * <p>One dockspace at a time; opening while an MCSX screen is up is refused (the screen path and
 * the embedded present path cannot share a frame — vanilla screens are fine, they render inside
 * the viewport pane).
 */
public final class McsxDockspace {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcsx-dock");

    private static volatile McsxDockspace current;

    private final DockConfig config;
    private final List<DockPane> panes;
    private final DockIds ids = new DockIds();
    private volatile DockHostView host;
    private volatile OverlayHost overlays;
    private volatile View root;
    private volatile Set<String> openPanes = Set.of();
    private volatile boolean closed;

    private McsxDockspace(DockConfig config, List<DockPane> panes) {
        this.config = config;
        this.panes = panes;
    }

    /** The open dockspace, or null. */
    public static McsxDockspace current() {
        return current;
    }

    public static McsxDockspace open(DockConfig config) {
        return open(config, false);
    }

    /** @param inspect mounts the {@link Inspector} over the workspace for picking its chrome apart */
    public static McsxDockspace open(DockConfig config, boolean inspect) {
        if (Minecraft.getInstance().screen instanceof MuiScreen) {
            throw new IllegalStateException("cannot open a dockspace while an MCSX screen is open");
        }
        McsxDockspace existing = current;
        if (existing != null && !existing.closed) {
            LOGGER.warn("A dockspace ('{}') is already open; close it first.", existing.config.id());
            return existing;
        }

        List<DockPane> panes = new ArrayList<>(config.panes());
        if (config.gameViewport()
                && panes.stream().noneMatch(p -> DockPane.VIEWPORT_ID.equals(p.id()))) {
            panes.add(new DockPane(DockPane.VIEWPORT_ID, "Viewport", "box", GameViewportView::new));
        }

        McsxDockspace dockspace = new McsxDockspace(config, panes);
        DockLayout initial = DockLayoutStore.load(config, dockspace.ids, panes);
        dockspace.openPanes = Set.copyOf(DockOps.openSet(initial));

        boolean accepted = MuiModApi.openOverlay(() -> {
            try {
                OverlayHost overlays = new OverlayHost(dockspace.hostContext());
                DockHostView view = new DockHostView(dockspace.hostContext(), panes, initial,
                        dockspace.ids, dockspace::layoutChanged, overlays);
                dockspace.host = view;
                dockspace.overlays = overlays;
                View root = dockspace.createRoot(view, overlays);
                if (inspect) {
                    root = Inspector.wrap(dockspace.hostContext(), root);
                }
                dockspace.root = root;
                // Fired here — on the UI thread, with host set — and not from open()'s game-thread
                // stack: consumers write reactive signals and call togglePane from this callback,
                // and the reactive graph is single-threaded (UI).
                config.onOpenPanesChanged().accept(dockspace.openPanes);
                return root;
            } catch (RuntimeException | Error failure) {
                Minecraft.getInstance().schedule(dockspace::close);
                throw failure;
            }
        });
        if (!accepted) {
            dockspace.closed = true;
            throw new IllegalStateException("cannot open a dockspace while another overlay is mounted");
        }
        current = dockspace;
        if (config.gameViewport()) {
            ViewportEmbedding.activate();
        }
        ViewportInput.Mode inputMode = config.gameViewport()
                ? config.inputMode() : ViewportInput.Mode.NONE;
        ViewportInput.begin(inputMode, config.releaseKey());
        MuiModApi.setOverlayInteractive(true);
        return dockspace;
    }

    /**
     * Layered Esc, mirroring the screen path's unfocus-before-back: a focused text field
     * unfocuses, else the topmost overlay layer (dropdown, dialog) dismisses, and only a press
     * with nothing left to dismiss closes the workspace. Callable from any thread; the decision
     * runs on the UI thread, where focus and overlay state live.
     */
    public void onEscape() {
        Core.postOnUiThread(() -> {
            View rootView = root;
            View focus = rootView != null ? rootView.findFocus() : null;
            if (focus instanceof EditText) {
                focus.clearFocus();
                return;
            }
            OverlayHost layers = overlays;
            if (layers != null && layers.dismissTop()) {
                return;
            }
            Minecraft.getInstance().schedule(this::close);
        });
    }

    private View createRoot(DockHostView view, OverlayHost overlays) {
        View header = config.header() != null
                ? config.header().create(hostContext(), overlays) : null;
        View footer = config.footer() != null
                ? config.footer().create(hostContext(), overlays) : null;
        return new DockRootView(hostContext(), header, view, footer, overlays);
    }

    private icyllis.modernui.core.Context hostContext() {
        return icyllis.modernui.ModernUI.getInstance();
    }

    /** Unmounts the workspace, disposes pane contents, and restores the game's real window. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (current == this) {
            current = null;
        }
        ViewportInput.end();
        if (config.gameViewport()) {
            ViewportEmbedding.deactivate();
        }
        MuiModApi.closeOverlay();
        Core.postOnUiThread(() -> {
            for (DockPane pane : panes) {
                dispose(pane.content(), "pane '" + pane.id() + "'");
            }
            dispose(config.header(), "header");
            dispose(config.footer(), "footer");
            config.onClose().run();
        });
    }

    /** One pane's failure to tear down must not strand the rest, or the workspace's onClose. */
    private void dispose(DockPane.PaneContent content, String name) {
        if (content == null) {
            return;
        }
        try {
            content.dispose();
        } catch (RuntimeException e) {
            LOGGER.warn("Dockspace {} failed to dispose", name, e);
        }
    }

    /**
     * Discards the persisted layout and re-applies the configured default — without saving: the
     * deleted file stays absent until the next user mutation, so a default shipped in a later
     * version is still picked up on the next open.
     */
    public void resetLayout() {
        DockLayoutStore.reset(config);
        DockHostView view = host;
        if (view != null) {
            view.post(() -> {
                DockLayout fallback = DockOps.sanitize(
                        DockLayoutStore.defaultLayout(config, ids, panes), DockLayoutStore.knownPanes(panes));
                view.apply(fallback, true);
                publishOpenPanes(fallback);
            });
        }
    }

    public void saveLayout() {
        save();
    }

    /**
     * Opens the pane as a floating window, or closes it if already open — the panels-menu toggle.
     * Posted to the UI looper, which also runs the overlay mount: a toggle issued before the
     * mount has executed (e.g. from the open-time {@code onOpenPanesChanged}) queues behind it
     * instead of being dropped.
     */
    public void togglePane(String paneId) {
        Core.postOnUiThread(() -> {
            DockHostView view = host;
            if (view != null) {
                view.toggleFloatPane(paneId);
            }
        });
    }

    public boolean isGameCaptured() {
        return ViewportInput.isGameCaptured();
    }

    public Set<String> openPanes() {
        return openPanes;
    }

    private void layoutChanged(DockLayout layout) {
        publishOpenPanes(layout);
        save();
    }

    private void publishOpenPanes(DockLayout layout) {
        openPanes = Set.copyOf(DockOps.openSet(layout));
        config.onOpenPanesChanged().accept(openPanes);
    }

    private void save() {
        DockHostView view = host;
        if (view != null) {
            view.post(() -> DockLayoutStore.save(config, view.layout()));
        }
    }

    /** Full-window chrome wrapper; unlike a generic linear layout it cannot collapse to wrap-content. */
    private static final class DockRootView extends ViewGroup {

        private final View header;
        private final View host;
        private final View footer;
        private final OverlayHost overlays;

        DockRootView(icyllis.modernui.core.Context context, View header, View host, View footer,
                     OverlayHost overlays) {
            super(context);
            this.header = header;
            this.host = host;
            this.footer = footer;
            this.overlays = overlays;
            setFocusable(true);
            setFocusableInTouchMode(true);
            setOnKeyListener(overlays.keyBindings());
            if (header != null) {
                addView(header);
            }
            addView(host);
            if (footer != null) {
                addView(footer);
            }
            addView(overlays);
            requestFocus();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int chromeHeight = measureChrome(header, width, height) + measureChrome(footer, width, height);
            host.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.max(0, height - chromeHeight), MeasureSpec.EXACTLY));
            overlays.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            setMeasuredDimension(width, height);
        }

        private static int measureChrome(View chrome, int width, int height) {
            if (chrome == null) {
                return 0;
            }
            chrome.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
            return chrome.getMeasuredHeight();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int y = 0;
            if (header != null) {
                header.layout(0, y, getWidth(), y + header.getMeasuredHeight());
                y += header.getMeasuredHeight();
            }
            host.layout(0, y, getWidth(), y + host.getMeasuredHeight());
            y += host.getMeasuredHeight();
            if (footer != null) {
                footer.layout(0, y, getWidth(), y + footer.getMeasuredHeight());
            }
            overlays.layout(0, 0, getWidth(), getHeight());
        }
    }
}

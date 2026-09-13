package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.UiOverlay;
import fr.lacaleche.glue.mcsx.client.component.Column;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockOperations;
import fr.lacaleche.glue.mcsx.client.dock.internal.persistence.DockLayoutStore;
import fr.lacaleche.glue.mcsx.client.dock.internal.view.DockHostView;
import fr.lacaleche.glue.mcsx.client.dock.internal.view.DockRootView;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.Stylesheet;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import fr.lacaleche.glue.mcsx.dock.McsxDock;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Overlay-hosted native ModernUI dock workspace with no process-global instance. */
@Environment(EnvType.CLIENT)
public final class Dockspace extends Fragment {

    private final Map<String, DockPane> panes;
    private final DockLayoutStore layouts;
    private final boolean persistent;
    private final Value<? extends Theme> theme;
    private final Value<? extends Stylesheet> stylesheet;
    private final DockContent header;
    private final DockContent footer;
    private final Consumer<DockLayout> onLayoutChanged;
    private final Consumer<Set<String>> onOpenPanesChanged;
    private final Runnable onMount;
    private final Runnable onUnmount;
    private final Runnable onClose;
    private final Object lifecycleLock = new Object();
    private volatile UiOverlay.Mount mount;
    private volatile ResourceLocation workspace;
    private volatile DockLayout javaDefault;
    private volatile ResourceLocation resourceDefault;
    private volatile DockLayout layout;
    private volatile Set<String> openPanes = Set.of();
    private volatile String maximizedPane;
    private DockHostView host;
    private View headerView;
    private View footerView;
    private volatile boolean opened;
    private volatile boolean disposed;
    private boolean mountingCallback;
    private boolean closeRequested;

    private Dockspace(Builder builder) {
        this.workspace = builder.id;
        this.panes = validatedPanes(builder.panes);
        this.layouts = builder.layouts != null ? builder.layouts : new DockLayoutStore();
        this.persistent = builder.persistent;
        this.theme = builder.theme;
        this.stylesheet = builder.stylesheet;
        this.header = builder.header;
        this.footer = builder.footer;
        this.javaDefault = builder.javaDefault;
        this.resourceDefault = builder.resourceDefault;
        this.onLayoutChanged = builder.onLayoutChanged;
        this.onOpenPanesChanged = builder.onOpenPanesChanged;
        this.onMount = builder.onMount;
        this.onUnmount = builder.onUnmount;
        this.onClose = builder.onClose;
        this.layout = this.initialLayout();
        this.openPanes = DockOperations.openSet(this.layout);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    /**
     * Mounts the workspace beside the game rather than in front of it.
     *
     * <p>A workspace is not a screen. It surrounds a running game instead of replacing it, so it is
     * hosted as an overlay and {@code Minecraft#screen} stays null — which is what leaves gameplay
     * ticking and keybinds live underneath it. Escape may clear transient UI state or reach Minecraft,
     * but never dismisses the workspace; closure is controlled exclusively through {@link #close()}.</p>
     *
     * <p>Client thread only, and the workspace counts as opened only once the overlay host has been
     * acquired: a rejected open — another workspace is already mounted — throws without changing this
     * instance, which stays closeable as a no-op and openable again later.</p>
     */
    public void open() {
        synchronized (this.lifecycleLock) {
            if (this.disposed) throw new IllegalStateException("Dockspace is closed");
            if (this.opened) throw new IllegalStateException("Dockspace has already been opened");

            try {
                this.mount = UiOverlay.mount(this, this::unmounted);
                this.opened = true;
                this.mountingCallback = true;
                try {
                    if (this.onMount != null) this.onMount.run();
                } finally {
                    this.mountingCallback = false;
                }
                if (this.closeRequested) this.mount.close();
            } catch (RuntimeException | Error failure) {
                UiOverlay.Mount mounted = this.mount;
                if (mounted != null) mounted.close();
                throw failure;
            }
        }
    }

    /**
     * Dismisses the workspace, saving its layout. Idempotent, and safe from any thread: the complete
     * teardown runs as one operation on Minecraft's client thread, and only the mount this instance
     * owns is closed — a never-opened or already-closed workspace does nothing.
     */
    public void close() {
        synchronized (this.lifecycleLock) {
            if (this.mountingCallback) {
                this.closeRequested = true;
                return;
            }

            UiOverlay.Mount mounted = this.mount;
            if (mounted != null) mounted.close();
        }
    }

    private void unmounted() {
        try {
            if (this.onUnmount != null) this.onUnmount.run();
        } finally {
            if (this.onClose != null) this.onClose.run();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        try {
            this.layout = this.layouts.load(this.workspace, this.persistent, this.javaDefault,
                    this.resourceDefault, this.paneIds());
            this.openPanes = DockOperations.openSet(this.layout);
            this.host = new DockHostView(this.requireContext(), this.panes, this.layout, this.theme,
                    this::completedMutation, maximized -> this.maximizedPane = maximized.orElse(null));
            this.headerView = this.createChrome(this.header, "header");
            this.footerView = this.createChrome(this.footer, "footer");
            DockRootView root = new DockRootView(
                    this.requireContext(),
                    this.headerView,
                    this.host,
                    this.footerView
            );
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            Column scope = Ui.with(this.requireContext()).column(root).classes("dockspace-scope");
            scope.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            scope.theme(this.theme);
            if (this.stylesheet == null) {
                scope.stylesheet(Stylesheet.empty());
            } else {
                scope.stylesheet(this.stylesheet);
            }
            DockHostView createdHost = this.host;
            Minecraft.getInstance().execute(() -> {
                UiOverlay.Mount mounted = this.mount;
                if (mounted != null) mounted.setEscapeHandler(createdHost::escapePressed);
            });
            this.onOpenPanesChanged.accept(this.openPanes);
            return scope;
        } catch (Error failure) {
            this.rollbackFailedMount(failure);
            throw failure;
        } catch (RuntimeException failure) {
            this.rollbackFailedMount(failure);
            McsxDock.LOGGER.error("Failed to mount dockspace {}", this.workspace, failure);
            return null;
        }
    }

    @Override
    public void onDestroy() {
        RuntimeException failure = null;
        if (!this.disposed) {
            this.disposed = true;
            if (this.host != null) {
                try {
                    this.layouts.save(this.workspace, this.persistent, this.host.layout());
                } catch (RuntimeException exception) {
                    failure = exception;
                }
                try {
                    this.host.disposeContent();
                } catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
            failure = this.disposeChrome(this.header, this.headerView, failure, "header");
            failure = this.disposeChrome(this.footer, this.footerView, failure, "footer");
        }
        super.onDestroy();
        if (failure != null) throw failure;
    }

    /**
     * Whether this workspace owns the overlay slot right now: true from the moment {@link #open}
     * acquires it — well before ModernUI has created the View — and false again as soon as a close is
     * requested. A dockspace is single-use, so this never becomes true a second time; ask
     * {@link UiOverlay#isOccupied()} before opening a replacement, since the slot may also be held by
     * an unrelated overlay.
     */
    public boolean isOpen() {
        return this.activeMount() != null;
    }

    public DockLayout layout() {
        return this.layout;
    }

    public Set<String> openPanes() {
        return this.openPanes;
    }

    public ResourceLocation workspace() {
        return this.workspace;
    }

    public Optional<String> maximizedPane() {
        return Optional.ofNullable(this.maximizedPane);
    }

    public void maximizePane(String paneId) {
        this.requirePane(paneId);
        UiOverlay.Mount mounted = this.requireActiveMount();
        this.postWhileMounted(mounted, () -> this.requireDockHost().maximizePane(paneId));
    }

    public void restoreMaximizedPane() {
        UiOverlay.Mount mounted = this.requireActiveMount();
        this.postWhileMounted(mounted, () -> this.requireDockHost().restoreMaximizedPane());
    }

    public void togglePane(String paneId) {
        this.requirePane(paneId);
        UiOverlay.Mount mounted = this.requireActiveMount();
        this.postWhileMounted(mounted, () -> this.requireDockHost().togglePane(paneId));
    }

    public void focusPane(String paneId) {
        this.requirePane(paneId);
        UiOverlay.Mount mounted = this.requireActiveMount();
        this.postWhileMounted(mounted, () -> this.requireDockHost().focusPane(paneId));
    }

    public void resetLayout() {
        UiOverlay.Mount mounted = this.requireActiveMount();
        this.postWhileMounted(mounted, () -> {
            DockHostView currentHost = this.requireDockHost();
            if (this.persistent) this.layouts.reset(this.workspace);
            DockLayout fallback = this.layouts.load(this.workspace, false, this.javaDefault,
                    this.resourceDefault, this.paneIds());
            currentHost.setLayout(fallback);
            this.publish(fallback, true);
        });
    }

    public void saveLayout() {
        UiOverlay.Mount mounted = this.requireActiveMount();
        this.postWhileMounted(mounted, () -> this.layouts.save(
                this.workspace,
                this.persistent,
                this.requireDockHost().layout()
        ));
    }

    public void switchWorkspace(ResourceLocation id) {
        this.switchWorkspace(id, null, null);
    }

    public void switchWorkspace(ResourceLocation id, ResourceLocation resourceDefault) {
        this.switchWorkspace(id, null, resourceDefault);
    }

    public void switchWorkspace(ResourceLocation id, DockLayout javaDefault) {
        this.switchWorkspace(id, javaDefault, null);
    }

    public void switchWorkspace(ResourceLocation id, DockLayout javaDefault,
                                ResourceLocation resourceDefault) {
        Objects.requireNonNull(id, "id");
        UiOverlay.Mount mounted = this.requireActiveMount();
        this.postWhileMounted(mounted, () -> {
            DockHostView currentHost = this.requireDockHost();
            this.layouts.saveLater(this.workspace, this.persistent, currentHost.layout());
            DockLayout target = this.layouts.load(id, this.persistent, javaDefault, resourceDefault, this.paneIds());
            this.workspace = id;
            this.javaDefault = javaDefault;
            this.resourceDefault = resourceDefault;
            currentHost.setLayout(target);
            this.publish(target, true);
        });
    }

    private void completedMutation(DockLayout layout) {
        try {
            this.publish(layout, true);
        } finally {
            this.layouts.saveLater(this.workspace, this.persistent, layout);
        }
    }

    private void publish(DockLayout layout, boolean notifyLayout) {
        Set<String> previousPanes = this.openPanes;
        this.layout = layout;
        this.openPanes = DockOperations.openSet(layout);
        if (notifyLayout) this.onLayoutChanged.accept(layout);
        if (!this.openPanes.equals(previousPanes)) this.onOpenPanesChanged.accept(this.openPanes);
    }

    private DockHostView requireDockHost() {
        if (this.disposed) throw new IllegalStateException("Dockspace is closed");
        if (this.host == null) throw new IllegalStateException("Dockspace is not mounted");
        return this.host;
    }

    private UiOverlay.Mount requireActiveMount() {
        UiOverlay.Mount mounted = this.activeMount();
        if (mounted == null) throw new IllegalStateException("Dockspace is not open");
        return mounted;
    }

    private UiOverlay.Mount activeMount() {
        UiOverlay.Mount mounted = this.mount;
        return mounted != null && mounted.acceptsWork() && !this.disposed ? mounted : null;
    }

    private void postWhileMounted(UiOverlay.Mount expected, Runnable operation) {
        Core.postOnUiThread(() -> {
            if (this.mount != expected || !expected.acceptsWork() || this.disposed) return;

            operation.run();
        });
    }

    private DockPane requirePane(String paneId) {
        DockPane pane = this.panes.get(Objects.requireNonNull(paneId, "paneId"));
        if (pane == null) throw new IllegalArgumentException("Unknown dock pane: " + paneId);
        return pane;
    }

    private View createChrome(DockContent content, String name) {
        if (content == null) return null;

        View view = content.create(this.requireContext());
        if (view == null) throw new IllegalStateException("Dockspace " + name + " content returned null");
        return view;
    }

    private RuntimeException disposeChrome(DockContent content, View view, RuntimeException failure, String name) {
        if (content == null || view == null) return failure;

        try {
            content.dispose(view);
        } catch (RuntimeException exception) {
            McsxDock.LOGGER.warn("Failed to dispose dockspace {}", name, exception);
            if (failure == null) return exception;
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private List<String> paneIds() {
        return List.copyOf(this.panes.keySet());
    }

    /**
     * The value reported before the dockspace mounts. Resolving the configured default properly
     * belongs to the store and reads resources, so an explicit resource default remains unresolved.
     */
    private DockLayout initialLayout() {
        if (this.javaDefault != null) {
            return DockOperations.sanitize(this.javaDefault, Set.copyOf(this.panes.keySet()));
        }
        if (this.resourceDefault != null || this.panes.isEmpty()) return DockLayouts.empty();
        return DockLayouts.layout(DockLayouts.tabs(this.paneIds()));
    }

    private void rollbackFailedMount(Throwable failure) {
        this.disposed = true;
        if (this.host != null) {
            try {
                this.host.disposeContent();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        RuntimeException cleanup = this.disposeChrome(this.header, this.headerView, null, "header");
        cleanup = this.disposeChrome(this.footer, this.footerView, cleanup, "footer");
        if (cleanup != null) failure.addSuppressed(cleanup);
        // A failed Fragment mount must not keep the overlay slot: the owning mount is released on the
        // client thread, which by then has completed open() and sees its own store of the handle.
        Minecraft.getInstance().execute(this::close);
    }

    private static Map<String, DockPane> validatedPanes(List<DockPane> panes) {
        Map<String, DockPane> result = new LinkedHashMap<>();
        for (DockPane pane : panes) {
            Objects.requireNonNull(pane, "pane");
            if (result.putIfAbsent(pane.id(), pane) != null) {
                throw new IllegalArgumentException("Duplicate dock pane id: " + pane.id());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public static final class Builder {

        private final ResourceLocation id;
        private final List<DockPane> panes = new ArrayList<>();
        private DockLayoutStore layouts;
        private boolean persistent = true;
        private Value<? extends Theme> theme = Signal.of(Themes.mcsx());
        private Value<? extends Stylesheet> stylesheet;
        private DockContent header;
        private DockContent footer;
        private DockLayout javaDefault;
        private ResourceLocation resourceDefault;
        private Consumer<DockLayout> onLayoutChanged = ignored -> {
        };
        private Consumer<Set<String>> onOpenPanesChanged = ignored -> {
        };
        private Runnable onMount;
        private Runnable onUnmount;
        private Runnable onClose;

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder pane(DockPane pane) {
            this.panes.add(Objects.requireNonNull(pane, "pane"));
            return this;
        }

        public Builder defaultLayout(DockLayout layout) {
            this.javaDefault = Objects.requireNonNull(layout, "layout");
            this.resourceDefault = null;
            return this;
        }

        public Builder defaultLayout(ResourceLocation resource) {
            this.resourceDefault = Objects.requireNonNull(resource, "resource");
            this.javaDefault = null;
            return this;
        }

        public Builder persistence(boolean persistent) {
            this.persistent = persistent;
            return this;
        }

        public Builder theme(Theme theme) {
            return this.theme(Signal.of(Objects.requireNonNull(theme, "theme")));
        }

        public Builder theme(Value<? extends Theme> theme) {
            this.theme = Objects.requireNonNull(theme, "theme");
            Objects.requireNonNull(theme.get(), "theme value");
            return this;
        }

        public Builder stylesheet(Stylesheet stylesheet) {
            return this.stylesheet(Signal.of(Objects.requireNonNull(stylesheet, "stylesheet")));
        }

        public Builder stylesheet(Value<? extends Stylesheet> stylesheet) {
            this.stylesheet = Objects.requireNonNull(stylesheet, "stylesheet");
            Objects.requireNonNull(stylesheet.get(), "stylesheet value");
            return this;
        }

        public Builder header(DockContent header) {
            this.header = Objects.requireNonNull(header, "header");
            return this;
        }

        public Builder footer(DockContent footer) {
            this.footer = Objects.requireNonNull(footer, "footer");
            return this;
        }

        public Builder onLayoutChanged(Consumer<DockLayout> listener) {
            this.onLayoutChanged = Objects.requireNonNull(listener, "listener");
            return this;
        }

        public Builder onOpenPanesChanged(Consumer<Set<String>> listener) {
            this.onOpenPanesChanged = Objects.requireNonNull(listener, "listener");
            return this;
        }

        /**
         * Runs on Minecraft's client thread after this workspace acquires its overlay mount. A close
         * requested from inside this callback is deferred until the callback returns.
         */
        public Builder onMount(@Nullable Runnable callback) {
            this.onMount = callback;
            return this;
        }

        /**
         * Runs on Minecraft's client thread inside the close operation, right after the overlay
         * stops mounting and before the cursor returns to the game. The place to synchronously
         * release per-frame world claims — a game viewport, an input hole — so they end on the same
         * frame as the workspace; View disposal still follows later on the UI thread, so detach-time
         * cleanup should stay in place as the idempotent fallback.
         */
        public Builder onUnmount(@Nullable Runnable callback) {
            this.onUnmount = callback;
            return this;
        }

        /**
         * Runs once on Minecraft's client thread after {@link #onUnmount}, before another workspace
         * can acquire the overlay slot. Native View disposal follows later on ModernUI's UI thread.
         */
        public Builder onClose(@Nullable Runnable callback) {
            this.onClose = callback;
            return this;
        }

        Builder layoutStore(DockLayoutStore layouts) {
            this.layouts = Objects.requireNonNull(layouts, "layouts");
            return this;
        }

        public Dockspace build() {
            return new Dockspace(this);
        }
    }
}

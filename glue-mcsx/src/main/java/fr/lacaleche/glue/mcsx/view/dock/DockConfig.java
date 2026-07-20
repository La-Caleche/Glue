package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockLayout;
import fr.lacaleche.glue.mcsx.viewport.ViewportInput;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Everything a dockspace is opened with.
 *
 * @param id                 names the workspace; the persisted layout file is keyed by it
 * @param panes              the dockable panes
 * @param defaultLayout      the layout used when nothing is persisted, or null
 * @param defaultLayoutAsset classpath fallback, {@code "ns:name"} →
 *                           {@code assets/ns/dock/name.json}, or null
 * @param inputMode          how the embedded game viewport takes the pointer; null defaults to
 *                           {@link ViewportInput.Mode#CLICK}
 * @param releaseKey         the key that hands input back to the dock while the game is captured
 * @param persist            whether layout mutations are written to the config dir
 * @param gameViewport       whether the workspace embeds the game as a dockable pane
 * @param header             chrome above the workspace, or null
 * @param footer             chrome below the workspace, or null
 * @param onOpenPanesChanged fired with the open pane ids whenever they change; null defaults to
 *                           a no-op
 * @param onClose            fired when the workspace closes; null defaults to a no-op
 */
public record DockConfig(String id, List<DockPane> panes, DockLayout defaultLayout,
                         String defaultLayoutAsset, ViewportInput.Mode inputMode,
                         int releaseKey, boolean persist, boolean gameViewport,
                         DockPane.PaneContent header, DockPane.PaneContent footer,
                         Consumer<Set<String>> onOpenPanesChanged, Runnable onClose) {

    public DockConfig {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("dockspace id must contain only letters, numbers, '_' or '-'");
        }
        panes = List.copyOf(Objects.requireNonNull(panes, "panes"));
        Set<String> paneIds = new HashSet<>();
        for (DockPane pane : panes) {
            Objects.requireNonNull(pane, "pane");
            if (pane.id() == null || pane.id().isBlank()) {
                throw new IllegalArgumentException("pane id must not be blank");
            }
            if (!paneIds.add(pane.id())) {
                throw new IllegalArgumentException("duplicate pane id '" + pane.id() + "'");
            }
            Objects.requireNonNull(pane.content(), "pane content");
        }
        // the canonical constructor is public API; a consumer bypassing the Builder must not be
        // able to construct a config the dockspace NPEs on at open/close
        inputMode = inputMode != null ? inputMode : ViewportInput.Mode.CLICK;
        onOpenPanesChanged = onOpenPanesChanged != null ? onOpenPanesChanged : ignored -> {
        };
        onClose = onClose != null ? onClose : () -> {
        };
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {

        private final String id;
        private final List<DockPane> panes = new ArrayList<>();
        private DockLayout defaultLayout;
        private String defaultLayoutAsset;
        // inputMode, onOpenPanesChanged and onClose are left null: the canonical constructor
        // defaults them, and spelling the same default out twice is how the two drift apart
        private ViewportInput.Mode inputMode;
        private int releaseKey = GLFW.GLFW_KEY_ESCAPE;
        private boolean persist = true;
        private boolean gameViewport = true;
        private DockPane.PaneContent header;
        private DockPane.PaneContent footer;
        private Consumer<Set<String>> onOpenPanesChanged;
        private Runnable onClose;

        private Builder(String id) {
            this.id = id;
        }

        public Builder pane(DockPane pane) {
            panes.add(pane);
            return this;
        }

        public Builder defaultLayout(DockLayout layout) {
            this.defaultLayout = layout;
            return this;
        }

        public Builder defaultLayoutAsset(String asset) {
            this.defaultLayoutAsset = asset;
            return this;
        }

        public Builder inputMode(ViewportInput.Mode mode) {
            this.inputMode = mode;
            return this;
        }

        public Builder releaseKey(int key) {
            this.releaseKey = key;
            return this;
        }

        public Builder persist(boolean value) {
            this.persist = value;
            return this;
        }

        public Builder gameViewport(boolean value) {
            this.gameViewport = value;
            return this;
        }

        public Builder header(DockPane.PaneContent content) {
            this.header = content;
            return this;
        }

        public Builder footer(DockPane.PaneContent content) {
            this.footer = content;
            return this;
        }

        public Builder onOpenPanesChanged(Consumer<Set<String>> listener) {
            this.onOpenPanesChanged = listener;
            return this;
        }

        public Builder onClose(Runnable callback) {
            this.onClose = callback;
            return this;
        }

        public DockConfig build() {
            return new DockConfig(id, panes, defaultLayout, defaultLayoutAsset,
                    inputMode, releaseKey, persist, gameViewport, header, footer,
                    onOpenPanesChanged, onClose);
        }
    }
}

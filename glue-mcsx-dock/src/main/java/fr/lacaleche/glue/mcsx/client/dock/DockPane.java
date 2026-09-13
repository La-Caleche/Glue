package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.Ui;
import icyllis.modernui.view.View;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Function;

/** Describes the immutable presentation and retained content of one dock pane. */
@Environment(EnvType.CLIENT)
public final class DockPane {

    private static final String VALID_ID = "[a-z0-9][a-z0-9._-]*";

    private final String id;
    private final Component title;
    private final Component icon;
    private final boolean closable;
    private final boolean transparent;
    private final DockContent trailingHeader;
    private final DockContent content;

    private DockPane(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.icon = builder.icon;
        this.closable = builder.closable;
        this.transparent = builder.transparent;
        this.trailingHeader = builder.trailingHeader;
        this.content = builder.content;
    }

    public static Builder builder(String id, Component title, DockContent content) {
        return new Builder(id, title, content);
    }

    public static Builder builderLiteral(String id, String title, Function<? super Ui, ? extends View> content) {
        return builder(id, Component.literal(title), DockContent.ui(content));
    }

    public static Builder builderTranslatable(String id, String titleKey,
                                               Function<? super Ui, ? extends View> content) {
        return builder(id, Component.translatable(titleKey), DockContent.ui(content));
    }

    public static DockPane literal(String id, String title, Function<? super Ui, ? extends View> content) {
        return builderLiteral(id, title, content).build();
    }

    public static DockPane translatable(String id, String titleKey, Function<? super Ui, ? extends View> content) {
        return builderTranslatable(id, titleKey, content).build();
    }

    public String id() {
        return this.id;
    }

    public Component title() {
        return this.title;
    }

    public Component icon() {
        return this.icon;
    }

    public boolean closable() {
        return this.closable;
    }

    /**
     * Whether the leaf hosting this pane leaves its content area unpainted, so whatever the dockspace
     * is hosted over shows through. With a transparent {@code DOCK_BACKGROUND} token this turns a
     * pane into a viewport onto the running game.
     */
    public boolean transparent() {
        return this.transparent;
    }

    public DockContent trailingHeader() {
        return this.trailingHeader;
    }

    public DockContent content() {
        return this.content;
    }

    public static final class Builder {

        private final String id;
        private final Component title;
        private final DockContent content;
        private Component icon;
        private boolean closable = true;
        private boolean transparent;
        private DockContent trailingHeader;

        private Builder(String id, Component title, DockContent content) {
            this.id = validateId(id);
            this.title = Objects.requireNonNull(title, "title");
            this.content = Objects.requireNonNull(content, "content");
        }

        public Builder icon(Component icon) {
            this.icon = Objects.requireNonNull(icon, "icon");
            return this;
        }

        public Builder closable(boolean closable) {
            this.closable = closable;
            return this;
        }

        public Builder transparent(boolean transparent) {
            this.transparent = transparent;
            return this;
        }

        public Builder trailingHeader(DockContent trailingHeader) {
            this.trailingHeader = Objects.requireNonNull(trailingHeader, "trailingHeader");
            return this;
        }

        public Builder trailingHeaderUi(Function<? super Ui, ? extends View> trailingHeader) {
            this.trailingHeader = DockContent.ui(trailingHeader);
            return this;
        }

        public DockPane build() {
            return new DockPane(this);
        }
    }

    private static String validateId(String id) {
        Objects.requireNonNull(id, "id");
        if (!id.matches(VALID_ID)) {
            throw new IllegalArgumentException(
                    "Pane id must start with a lowercase letter or number and contain only lowercase letters, numbers, '.', '_' or '-': " + id
            );
        }
        return id;
    }
}

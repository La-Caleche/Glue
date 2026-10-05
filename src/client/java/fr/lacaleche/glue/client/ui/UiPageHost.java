package fr.lacaleche.glue.client.ui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shows one {@link UiPage} at a time in a screen: builds it into an area, and when it is hidden removes
 * its widgets, releases its {@link UiTextureView}s' texture locations and closes it.
 */
final class UiPageHost {

    private final Function<AbstractWidget, AbstractWidget> addWidget;
    private final Consumer<AbstractWidget> removeWidget;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private @Nullable UiPage shown;
    private @Nullable UiRowList rows;
    private @Nullable Component unavailable;

    UiPageHost(Function<AbstractWidget, AbstractWidget> addWidget, Consumer<AbstractWidget> removeWidget) {
        this.addWidget = addWidget;
        this.removeWidget = removeWidget;
    }

    void show(UiPage page, ScreenRectangle area, boolean descriptionTooltips) {
        this.hide();
        this.shown = page;
        this.unavailable = page.unavailableReason();
        if (this.unavailable != null) return;

        UiPageBuilder builder = new UiPageBuilder(area, descriptionTooltips,
                widget -> this.widgets.add(this.addWidget.apply(widget)));
        page.build(builder);
        this.rows = builder.builtRows();
    }

    void hide() {
        if (this.shown == null) return;

        releaseTextures(this.widgets);
        this.widgets.forEach(this.removeWidget);
        this.widgets.clear();
        this.rows = null;
        this.unavailable = null;
        UiPage hidden = this.shown;
        this.shown = null;
        hidden.close();
    }

    @Nullable UiRowList rows() {
        return this.rows;
    }

    /** Why the shown page is unavailable, or null when it is built or none is shown. */
    @Nullable Component unavailable() {
        return this.unavailable;
    }

    private static void releaseTextures(List<? extends GuiEventListener> listeners) {
        for (GuiEventListener listener : listeners) {
            if (listener instanceof UiTextureView view) {
                view.release();
            } else if (listener instanceof ContainerEventHandler container) {
                releaseTextures(container.children());
            }
        }
    }
}

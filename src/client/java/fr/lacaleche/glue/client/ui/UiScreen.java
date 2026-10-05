package fr.lacaleche.glue.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A screen of {@link UiPage}s: the pages listed by group in a sidebar when there is more than one, the
 * shown page's content, the description of the row under the pointer or focus, and a Done button that
 * returns to the screen it was opened from. It dims what is behind it rather than blurring it.
 *
 * <p>One page is shown at a time. Switching pages, resizing the window and closing the screen close
 * the shown page and release the texture locations its {@link UiTextureView}s registered.</p>
 */
public class UiScreen extends Screen {

    private final @Nullable Screen parent;
    private final List<Group> groups;
    private final List<AbstractWidget> pageWidgets = new ArrayList<>();
    private UiPage page;
    private @Nullable UiPage shown;
    private @Nullable UiRowList rows;
    private @Nullable Component unavailable;
    private UiLayout layout;

    public UiScreen(Component title, @Nullable Screen parent, List<Group> groups) {
        super(title);
        this.parent = parent;
        this.groups = List.copyOf(groups);
        this.page = this.groups.stream()
                .flatMap(group -> group.pages().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A screen needs at least one page"));
    }

    public UiScreen(Component title, @Nullable Screen parent, UiPage page) {
        this(title, parent, List.of(new Group(title, List.of(page))));
    }

    @Override
    protected void init() {
        boolean sidebar = this.groups.stream().mapToInt(group -> group.pages().size()).sum() > 1;
        this.layout = UiLayout.of(UiStyle.DEFAULT, this.width, this.height, sidebar);
        if (this.layout.sidebar() != null) {
            UiRowList tabs = this.addRenderableWidget(new UiRowList(this.layout.sidebar(), false));
            for (Group group : this.groups) {
                tabs.section(group.title());
                group.pages().forEach(groupPage -> tabs.wide(new PageTab(groupPage)));
            }
        }

        UiButton done = this.addRenderableWidget(new UiButton(CommonComponents.GUI_DONE, this::onClose));
        ScreenRectangle doneArea = this.layout.done();
        done.setRectangle(doneArea.width(), doneArea.height(), doneArea.left(), doneArea.top());
        this.showPage();
    }

    @Override
    protected void rebuildWidgets() {
        this.hidePage();
        super.rebuildWidgets();
    }

    @Override
    public void removed() {
        this.hidePage();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft.level == null) this.renderPanorama(graphics, partialTick);

        graphics.fill(0, 0, this.width, this.height, UiStyle.DEFAULT.backdrop());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiStyle style = UiStyle.DEFAULT;
        fillPanel(graphics, this.layout.sidebar());
        fillPanel(graphics, this.layout.content());
        fillPanel(graphics, this.layout.description());
        graphics.drawString(this.font, this.title, style.padding(), this.layout.titleY(), style.text());
        super.render(graphics, mouseX, mouseY, partialTick);

        int padding = style.padding();
        ScreenRectangle content = this.layout.content();
        if (this.unavailable != null) {
            graphics.drawWordWrap(this.font, this.unavailable, content.left() + padding, content.top() + padding,
                    content.width() - 2 * padding, style.muted());
        }

        ScreenRectangle panel = this.layout.description();
        Component description = this.rows != null ? this.rows.description() : null;
        if (panel != null && description != null) {
            graphics.drawWordWrap(this.font, description, panel.left() + padding, panel.top() + padding,
                    panel.width() - 2 * padding, style.muted());
        }
    }

    private void select(UiPage selected) {
        if (selected.equals(this.page)) return;

        this.hidePage();
        this.page = selected;
        this.showPage();
    }

    private void showPage() {
        this.shown = this.page;
        this.unavailable = this.page.unavailableReason();
        if (this.unavailable != null) return;

        UiPageBuilder builder = new UiPageBuilder(this.layout.content(), this.layout.description() == null,
                widget -> this.pageWidgets.add(this.addRenderableWidget(widget)));
        this.page.build(builder);
        this.rows = builder.builtRows();
    }

    private void hidePage() {
        if (this.shown == null) return;

        releaseTextures(this.pageWidgets);
        this.pageWidgets.forEach(this::removeWidget);
        this.pageWidgets.clear();
        this.rows = null;
        this.unavailable = null;
        UiPage hidden = this.shown;
        this.shown = null;
        hidden.close();
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

    private static void fillPanel(GuiGraphics graphics, @Nullable ScreenRectangle area) {
        if (area == null) return;

        graphics.fill(area.left(), area.top(), area.right(), area.bottom(), UiStyle.DEFAULT.panel());
    }

    /** Pages listed together in the sidebar under a title, such as a mod's name. */
    public record Group(Component title, List<UiPage> pages) {

        public Group {
            Objects.requireNonNull(title, "title");
            pages = List.copyOf(pages);
        }
    }

    private final class PageTab extends AbstractButton {

        private final UiPage target;

        private PageTab(UiPage target) {
            super(0, 0, UiStyle.DEFAULT.sidebarWidth(), UiStyle.DEFAULT.controlHeight(), target.title());
            this.target = target;
        }

        @Override
        public void onPress() {
            UiScreen.this.select(this.target);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            UiStyle style = UiStyle.DEFAULT;
            boolean selected = this.target.equals(UiScreen.this.page);
            if (selected || this.isHoveredOrFocused()) {
                graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(),
                        selected ? style.control() : style.hover());
            }
            if (selected) graphics.fill(this.getX(), this.getY(), this.getX() + 2, this.getBottom(), style.accent());
            if (this.isFocused()) {
                graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), style.focus());
            }
            int color = this.target.unavailableReason() != null ? style.muted() : style.text();
            int textX = this.getX() + 6;
            // Centred on its left edge, the label is clamped to start there: it reads left-aligned.
            renderScrollingString(graphics, UiScreen.this.font, this.getMessage(), textX, textX, this.getY(),
                    this.getRight() - 2, this.getBottom(), color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}

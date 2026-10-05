package fr.lacaleche.glue.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A scrolling list of rows, each a label on the left and one control on the right, separated by
 * section headers. It is vanilla's {@link ContainerObjectSelectionList}, so focus, the keyboard,
 * scrolling and narration are vanilla's; only its drawing is the kit's.
 */
public final class UiRowList extends ContainerObjectSelectionList<UiRowList.Entry> {

    private final boolean descriptionTooltips;

    /**
     * @param descriptionTooltips whether rows show their description as a tooltip on their control,
     *        for a screen without a description panel
     */
    public UiRowList(ScreenRectangle area, boolean descriptionTooltips) {
        super(Minecraft.getInstance(), area.width(), area.height(), area.top(), UiStyle.DEFAULT.rowHeight());
        this.setX(area.left());
        this.centerListVertically = false;
        this.descriptionTooltips = descriptionTooltips;
    }

    @Override
    public int getRowLeft() {
        return this.getX() + UiStyle.DEFAULT.padding();
    }

    @Override
    public int getRowWidth() {
        return this.getWidth() - 2 * UiStyle.DEFAULT.padding() - SCROLLBAR_WIDTH;
    }

    @Override
    protected int scrollBarX() {
        return this.getRight() - SCROLLBAR_WIDTH;
    }

    @Override
    protected void renderListBackground(GuiGraphics graphics) {
    }

    @Override
    protected void renderListSeparators(GuiGraphics graphics) {
    }

    @Override
    protected void renderScrollbar(GuiGraphics graphics) {
        if (!this.scrollbarVisible()) return;

        int x = this.scrollBarX();
        int y = this.scrollBarY();
        graphics.fill(x, this.getY(), x + SCROLLBAR_WIDTH, this.getBottom(), UiStyle.DEFAULT.control());
        graphics.fill(x + 1, y, x + SCROLLBAR_WIDTH - 1, y + this.scrollerHeight(), UiStyle.DEFAULT.muted());
    }

    /** Offers the wheel to the control under the pointer first, such as a focused {@link UiNumberField}. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Entry entry = this.isMouseOver(mouseX, mouseY) ? this.getEntryAtPosition(mouseX, mouseY) : null;
        if (entry != null && entry.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Adds a row of a label and a control, and returns the control. */
    public <T extends AbstractWidget> T row(Component label, @Nullable Component description, T control) {
        this.addEntry(new Row(Objects.requireNonNull(label, "label"), description, control, this.descriptionTooltips));
        return control;
    }

    /** Adds a row whose control spans the whole row, and returns the control. */
    public <T extends AbstractWidget> T wide(T control) {
        this.addEntry(new Row(null, null, control, false));
        return control;
    }

    /** Adds a header that starts a group of rows. */
    public void section(Component title) {
        this.addEntry(new Section(Objects.requireNonNull(title, "title")));
    }

    /** The description of the row under the pointer, else of the focused row, or null. */
    public @Nullable Component description() {
        if (this.getHovered() instanceof Row row && row.description != null) return row.description;

        return this.getFocused() instanceof Row row ? row.description : null;
    }

    /** One line of the list: a row or a section header. */
    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {

        private Entry() {
        }
    }

    private static final class Row extends Entry {

        private final @Nullable Component label;
        private final @Nullable Component description;
        private final AbstractWidget control;

        private Row(@Nullable Component label, @Nullable Component description, AbstractWidget control,
                boolean descriptionTooltip) {
            this.label = label;
            this.description = description;
            this.control = Objects.requireNonNull(control, "control");
            if (descriptionTooltip && description != null) control.setTooltip(Tooltip.create(description));
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (this.label == null) {
                this.control.setRectangle(width, height, left, top);
                this.control.render(graphics, mouseX, mouseY, partialTick);
                return;
            }

            UiStyle style = UiStyle.DEFAULT;
            if (hovered || this.isFocused()) {
                graphics.fill(left, top, left + width, top + height, ARGB.color(0x40, style.hover()));
            }
            int controlWidth = Math.min(style.controlWidth(), width / 2);
            int labelWidth = width - controlWidth - 2 * style.padding();
            Font font = Minecraft.getInstance().font;
            graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(this.label, labelWidth)),
                    left + style.padding(), top + (height - 8) / 2, style.text());
            this.control.setRectangle(controlWidth, height, left + width - controlWidth, top);
            this.control.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.control);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.control);
        }
    }

    private static final class Section extends Entry {

        private final Component title;

        private Section(Component title) {
            this.title = title;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int y = top + height - font.lineHeight - 1;
            graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(this.title, width)),
                    left, y, UiStyle.DEFAULT.muted());
            graphics.fill(left, top + height - 1, left + width, top + height, UiStyle.DEFAULT.control());
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }
}

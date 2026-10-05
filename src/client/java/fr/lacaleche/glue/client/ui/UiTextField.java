package fr.lacaleche.glue.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Edits a {@code String}. Each edit is reported; while the field is not focused it shows its
 * supplier's value. It is a vanilla unbordered {@link EditBox} inset in a frame drawn in the kit's
 * style, so its bounds are the frame and its text sits four pixels inside.
 */
public final class UiTextField extends EditBox {

    private static final int INSET = 4;

    private final Supplier<String> value;
    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;

    public UiTextField(Component name, Supplier<String> value, Consumer<String> onChange) {
        super(Minecraft.getInstance().font, UiStyle.DEFAULT.controlWidth(), UiStyle.DEFAULT.controlHeight(), name);
        this.value = Objects.requireNonNull(value, "value");
        this.setBordered(false);
        this.setTextColor(UiStyle.DEFAULT.text());
        this.setTextColorUneditable(UiStyle.DEFAULT.disabled());
        this.setValue(value.get());
        this.setResponder(Objects.requireNonNull(onChange, "onChange"));
        this.setRectangle(UiStyle.DEFAULT.controlWidth(), UiStyle.DEFAULT.controlHeight(), 0, 0);
    }

    @Override
    public void setRectangle(int width, int height, int x, int y) {
        this.frameX = x;
        this.frameY = y;
        this.frameWidth = width;
        this.frameHeight = height;
        super.setRectangle(Math.max(0, width - 2 * INSET), 9, x + INSET, y + (height - 8) / 2);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.active && this.visible
                && mouseX >= this.frameX && mouseX < this.frameX + this.frameWidth
                && mouseY >= this.frameY && mouseY < this.frameY + this.frameHeight;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String current = this.value.get();
        if (!this.isFocused() && !current.equals(this.getValue())) this.setValue(current);

        UiStyle style = UiStyle.DEFAULT;
        boolean lit = this.active && (this.isFocused() || this.isMouseOver(mouseX, mouseY));
        graphics.fill(this.frameX, this.frameY, this.frameX + this.frameWidth, this.frameY + this.frameHeight,
                lit ? style.hover() : style.control());
        if (this.isFocused()) {
            graphics.renderOutline(this.frameX, this.frameY, this.frameWidth, this.frameHeight, style.focus());
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}

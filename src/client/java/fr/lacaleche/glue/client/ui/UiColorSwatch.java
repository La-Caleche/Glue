package fr.lacaleche.glue.client.ui;

import fr.lacaleche.glue.math.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** Shows a colour read from its supplier each frame, as a swatch and its hex code; it takes no input. */
public final class UiColorSwatch extends AbstractWidget {

    private final Supplier<Color> value;

    public UiColorSwatch(Component name, Supplier<Color> value) {
        super(0, 0, UiStyle.DEFAULT.controlWidth(), UiStyle.DEFAULT.controlHeight(), name);
        this.value = Objects.requireNonNull(value, "value");
        this.active = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiStyle style = UiStyle.DEFAULT;
        Color color = this.value.get();
        int swatch = this.getHeight();
        graphics.fill(this.getX(), this.getY(), this.getX() + swatch, this.getBottom(), style.control());
        graphics.fill(this.getX() + 2, this.getY() + 2, this.getX() + swatch - 2, this.getBottom() - 2, color.getColor());
        Font font = Minecraft.getInstance().font;
        String hex = String.format(Locale.ROOT, "#%08X", color.getColor());
        graphics.drawString(font, hex, this.getX() + swatch + 4, this.getY() + (this.getHeight() - 8) / 2, style.text());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

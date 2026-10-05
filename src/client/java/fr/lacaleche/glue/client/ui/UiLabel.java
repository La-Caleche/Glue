package fr.lacaleche.glue.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Supplier;

/** Shows a value read from its supplier each frame; it takes no input. */
public final class UiLabel extends AbstractWidget {

    private final Supplier<Component> value;

    public UiLabel(Component name, Supplier<Component> value) {
        super(0, 0, UiStyle.DEFAULT.controlWidth(), UiStyle.DEFAULT.controlHeight(), name);
        this.value = Objects.requireNonNull(value, "value");
        this.active = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderScrollingString(graphics, Minecraft.getInstance().font, this.value.get(),
                this.getX() + 2, this.getY(), this.getRight() - 2, this.getBottom(), UiStyle.DEFAULT.text());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

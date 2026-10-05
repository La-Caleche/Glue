package fr.lacaleche.glue.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * A button drawn in the kit's style. Its message is its name; a button that holds a value shows the
 * value and narrates both.
 */
abstract class FlatButton extends AbstractButton {

    FlatButton(Component name) {
        super(0, 0, UiStyle.DEFAULT.controlWidth(), UiStyle.DEFAULT.controlHeight(), name);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiStyle style = UiStyle.DEFAULT;
        style.drawControl(graphics, this);
        Component value = this.value();
        Component shown = value != null ? value : this.getMessage();
        int color = this.active ? this.valueColor(style) : style.disabled();
        renderScrollingString(graphics, Minecraft.getInstance().font, shown,
                this.getX() + TEXT_MARGIN, this.getY(), this.getRight() - TEXT_MARGIN, this.getBottom(), color);
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        Component value = this.value();
        if (value == null) return super.createNarrationMessage();

        return wrapDefaultNarrationMessage(CommonComponents.optionNameValue(this.getMessage(), value));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    /** The value shown in place of the name, or null for a plain button. */
    abstract @Nullable Component value();

    int valueColor(UiStyle style) {
        return style.text();
    }
}

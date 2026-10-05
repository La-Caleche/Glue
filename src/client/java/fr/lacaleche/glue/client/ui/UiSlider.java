package fr.lacaleche.glue.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * Picks a number within a range, on a fixed step. The value is read from its supplier each frame, and
 * every change, by dragging or with the left and right arrow keys, is reported snapped to the step.
 * A whole-number step reports whole numbers.
 */
public final class UiSlider extends AbstractWidget {

    private static final int HANDLE_WIDTH = 4;

    private final double min;
    private final double max;
    private final double step;
    private final DoubleSupplier value;
    private final DoubleConsumer onChange;
    private DoubleFunction<Component> format;

    public UiSlider(Component name, double min, double max, double step, DoubleSupplier value,
            DoubleConsumer onChange) {
        super(0, 0, UiStyle.DEFAULT.controlWidth(), UiStyle.DEFAULT.controlHeight(), name);
        if (!(max > min)) throw new IllegalArgumentException("A slider's max must exceed its min");
        if (!(step > 0)) throw new IllegalArgumentException("A slider's step must be positive");

        this.min = min;
        this.max = max;
        this.step = step;
        this.value = Objects.requireNonNull(value, "value");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        this.format = number -> Component.literal(format(number, step));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiStyle style = UiStyle.DEFAULT;
        style.drawControl(graphics, this);
        double fraction = (Mth.clamp(this.value.getAsDouble(), this.min, this.max) - this.min) / (this.max - this.min);
        int track = this.getWidth() - HANDLE_WIDTH;
        int handleX = this.getX() + (int) Math.round(fraction * track);
        graphics.fill(this.getX() + 1, this.getY() + 1, handleX, this.getBottom() - 1, ARGB.color(0x60, style.accent()));
        graphics.fill(handleX, this.getY(), handleX + HANDLE_WIDTH, this.getBottom(),
                this.active ? style.accent() : style.disabled());
        renderScrollingString(graphics, Minecraft.getInstance().font, this.valueText(),
                this.getX() + 2, this.getY(), this.getRight() - 2, this.getBottom(),
                this.active ? style.text() : style.disabled());
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.setFromMouse(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        this.setFromMouse(mouseX);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!this.active) return false;

        int direction = key == GLFW.GLFW_KEY_RIGHT ? 1 : key == GLFW.GLFW_KEY_LEFT ? -1 : 0;
        if (direction == 0) return false;

        this.report(this.value.getAsDouble() + direction * this.step);
        return true;
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return Component.translatable("gui.narrate.slider",
                CommonComponents.optionNameValue(this.getMessage(), this.valueText()));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.createNarrationMessage());
        if (this.active && this.isFocused()) {
            output.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.focused"));
        }
    }

    /** Replaces how the value is shown; by default it is printed with the step's decimals. */
    public void setFormat(DoubleFunction<Component> format) {
        this.format = Objects.requireNonNull(format, "format");
    }

    private Component valueText() {
        return this.format.apply(this.value.getAsDouble());
    }

    private void setFromMouse(double mouseX) {
        double fraction = (mouseX - (this.getX() + HANDLE_WIDTH / 2.0)) / (this.getWidth() - HANDLE_WIDTH);
        this.report(this.min + Mth.clamp(fraction, 0.0, 1.0) * (this.max - this.min));
    }

    private void report(double raw) {
        double snapped = snap(raw, this.min, this.max, this.step);
        if (snapped != this.value.getAsDouble()) this.onChange.accept(snapped);
    }

    /**
     * Clamps a value into the range and rounds it to the nearest step counted from {@code min}, or to
     * {@code max} when that is nearer, so the end of a range that is not a whole number of steps stays
     * reachable.
     */
    static double snap(double value, double min, double max, double step) {
        double clamped = Mth.clamp(value, min, max);
        double stepped = Math.min(max, min + Math.round((clamped - min) / step) * step);
        double snapped = max - clamped < Math.abs(clamped - stepped) ? max : stepped;
        // Rounds away the binary error of min + steps * step, so 0.1 steps report 0.3, not 0.30000000000000004.
        return BigDecimal.valueOf(snapped).setScale(Math.max(decimals(step), decimals(min)), RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Prints a value with as many decimals as the step has. */
    static String format(double value, double step) {
        return BigDecimal.valueOf(value).setScale(decimals(step), RoundingMode.HALF_UP).toPlainString();
    }

    static int decimals(double number) {
        return Math.max(0, BigDecimal.valueOf(number).stripTrailingZeros().scale());
    }
}

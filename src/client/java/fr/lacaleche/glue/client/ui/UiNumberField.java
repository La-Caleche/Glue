package fr.lacaleche.glue.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * Edits a number, bounded or not, on a step. Dragging sideways moves it a step every two pixels and
 * hides the pointer, so a drag never runs out of screen; the pointer comes back where it was pressed.
 * The arrow keys, and the mouse wheel while the field is focused, move it a step. Shift moves a tenth
 * of a step and Control ten steps. A click without a drag, or Enter, types a value; Enter or leaving
 * the field sets it. The value is read from its supplier each frame, and every change is reported.
 */
public final class UiNumberField extends AbstractWidget {

    private static final double DRAG_THRESHOLD = 3.0;
    private static final double PIXELS_PER_STEP = 2.0;
    private static final int BLINK_MILLIS = 300;

    private final double min;
    private final double max;
    private final double step;
    private final DoubleSupplier value;
    private final DoubleConsumer onChange;
    private DoubleFunction<Component> format;
    private boolean pressed;
    private boolean dragging;
    private double travel;
    private double pressX;
    private double pressY;
    private @Nullable String text;
    private boolean replaceText;

    /**
     * @param min the lowest value, or {@link Double#NEGATIVE_INFINITY} for none
     * @param max the highest value, or {@link Double#POSITIVE_INFINITY} for none
     */
    public UiNumberField(Component name, double min, double max, double step, DoubleSupplier value,
            DoubleConsumer onChange) {
        super(0, 0, UiStyle.DEFAULT.controlWidth(), UiStyle.DEFAULT.controlHeight(), name);
        if (!(max > min)) throw new IllegalArgumentException("A number field's max must exceed its min");
        if (!(step > 0) || Double.isInfinite(step)) {
            throw new IllegalArgumentException("A number field's step must be positive and finite");
        }

        this.min = min;
        this.max = max;
        this.step = step;
        this.value = Objects.requireNonNull(value, "value");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        this.format = number -> Component.literal(format(number, step));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // A release the field never saw, such as one outside the window, still ends the drag.
        long handle = window().getWindow();
        if (this.dragging && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            this.endDrag();
        }

        UiStyle style = UiStyle.DEFAULT;
        style.drawControl(graphics, this);
        Font font = Minecraft.getInstance().font;
        int textY = this.getY() + (this.getHeight() - 8) / 2;
        if (this.text != null) {
            int textWidth = font.width(this.text);
            int x = Math.max(this.getX() + 4, this.getRight() - 4 - textWidth);
            if (this.replaceText) {
                graphics.fill(x - 1, textY - 1, x + textWidth + 1, textY + 9, ARGB.color(0x80, style.accent()));
            }
            graphics.enableScissor(this.getX() + 2, this.getY(), this.getRight() - 2, this.getBottom());
            graphics.drawString(font, this.text, x, textY, style.text());
            if (!this.replaceText && Util.getMillis() / BLINK_MILLIS % 2 == 0) {
                graphics.fill(x + textWidth, textY - 1, x + textWidth + 1, textY + 9, style.text());
            }
            graphics.disableScissor();
            return;
        }

        int color = this.active ? style.text() : style.disabled();
        boolean lit = this.active && (this.isHoveredOrFocused() || this.dragging);
        if (lit) {
            graphics.drawString(font, "<", this.getX() + 3, textY, style.muted());
            graphics.drawString(font, ">", this.getRight() - 3 - font.width(">"), textY, style.muted());
        }
        renderScrollingString(graphics, font, this.valueText(), this.getX() + 10, this.getY(), this.getRight() - 10,
                this.getBottom(), color);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.text != null) return;

        this.pressed = true;
        this.travel = 0.0;
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (!this.pressed) return;

        this.travel += dragX;
        if (!this.dragging) {
            if (Math.abs(this.travel) < DRAG_THRESHOLD) return;

            this.startDrag();
        }
        int steps = (int) (this.travel / PIXELS_PER_STEP);
        if (steps == 0) return;

        this.travel -= steps * PIXELS_PER_STEP;
        this.nudge(steps);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (!this.pressed) return;

        this.pressed = false;
        if (this.dragging) {
            this.endDrag();
        } else {
            this.startTyping();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.active || !this.isFocused() || scrollY == 0) return false;

        this.commitTyping();
        this.nudge(scrollY > 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!this.active) return false;

        if (this.text != null) return this.keyTyping(key);
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            this.startTyping();
            return true;
        }
        int direction = key == GLFW.GLFW_KEY_RIGHT ? 1 : key == GLFW.GLFW_KEY_LEFT ? -1 : 0;
        if (direction == 0) return false;

        this.nudge(direction);
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (this.text == null || "0123456789.,-+eE".indexOf(character) < 0) return false;

        String typed = character == ',' ? "." : String.valueOf(character);
        this.text = this.replaceText ? typed : this.text + typed;
        this.replaceText = false;
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        // A row refocuses its control on each click, unfocusing it first, so a press survives this.
        if (!focused) {
            this.commitTyping();
            if (this.dragging) this.endDrag();
        }
        super.setFocused(focused);
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return Component.translatable("gui.narrate.editBox", this.getMessage(), this.valueText());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, CommonComponents.optionNameValue(this.getMessage(), this.valueText()));
    }

    /** Replaces how the value is shown; by default it is printed with the step's decimals, or a few more. */
    public void setFormat(DoubleFunction<Component> format) {
        this.format = Objects.requireNonNull(format, "format");
    }

    /** Whether a value is being typed, so keys go to the field rather than its screen. */
    public boolean isEditing() {
        return this.text != null;
    }

    /**
     * Whether the field is being dragged, with the pointer hidden. A screen keeps the field while it is,
     * since a field removed mid-drag cannot show the pointer again.
     */
    public boolean isDragging() {
        return this.dragging;
    }

    private Component valueText() {
        return this.format.apply(this.value.getAsDouble());
    }

    private boolean keyTyping(int key) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            this.commitTyping();
            return true;
        }
        if (key != GLFW.GLFW_KEY_BACKSPACE) return false;

        String typed = Objects.requireNonNull(this.text);
        this.text = this.replaceText || typed.isEmpty() ? "" : typed.substring(0, typed.length() - 1);
        this.replaceText = false;
        return true;
    }

    private void startTyping() {
        this.text = BigDecimal.valueOf(this.value.getAsDouble()).stripTrailingZeros().toPlainString();
        this.replaceText = true;
    }

    private void commitTyping() {
        if (this.text == null) return;

        double typed = parse(this.text.trim());
        this.text = null;
        this.report(Mth.clamp(typed, this.min, this.max));
    }

    /** Moves the value by whole steps, a tenth of a step with Shift or ten with Control. */
    private void nudge(int steps) {
        double size = Screen.hasShiftDown() ? this.step / 10 : Screen.hasControlDown() ? this.step * 10 : this.step;
        this.report(snap(this.value.getAsDouble() + steps * size, this.min, this.max, size));
    }

    /** Reports a changed number; NaN, from a typed value that did not parse, leaves it as it was. */
    private void report(double number) {
        if (Double.isFinite(number) && number != this.value.getAsDouble()) this.onChange.accept(number);
    }

    private void startDrag() {
        Minecraft client = Minecraft.getInstance();
        this.dragging = true;
        this.pressX = client.mouseHandler.xpos();
        this.pressY = client.mouseHandler.ypos();
        InputConstants.grabOrReleaseMouse(window().getWindow(), GLFW.GLFW_CURSOR_DISABLED, this.pressX, this.pressY);
    }

    private void endDrag() {
        this.dragging = false;
        this.pressed = false;
        InputConstants.grabOrReleaseMouse(window().getWindow(), GLFW.GLFW_CURSOR_NORMAL, this.pressX, this.pressY);
    }

    private static double parse(String typed) {
        try {
            return Double.parseDouble(typed);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static Window window() {
        return Minecraft.getInstance().getWindow();
    }

    /** Rounds a value to the nearest multiple of the step, then clamps it into the range. */
    static double snap(double value, double min, double max, double step) {
        double stepped = Math.round(value / step) * step;
        // Rounds away the binary error of steps * step, so 0.1 steps reach 0.3, not 0.30000000000000004.
        int scale = UiSlider.decimals(step);
        return Mth.clamp(BigDecimal.valueOf(stepped).setScale(scale, RoundingMode.HALF_UP).doubleValue(), min, max);
    }

    /** Prints a value with the step's decimals, or up to two more when the value has them. */
    static String format(double value, double step) {
        int decimals = UiSlider.decimals(step);
        int scale = Mth.clamp(UiSlider.decimals(value), decimals, decimals + 2);
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }
}

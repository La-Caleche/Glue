package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.UiSounds;
import fr.lacaleche.glue.mcsx.client.internal.text.MinecraftText;
import fr.lacaleche.glue.mcsx.client.theme.Token;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Typeface;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

import java.util.Objects;

@Environment(EnvType.CLIENT)
public final class Button extends icyllis.modernui.widget.Button {

    private final PropertyScope<Button> properties = new PropertyScope<>(this);
    private final RaisedBackground themedBackground = new RaisedBackground();
    private final StyleMetadata style = new StyleMetadata(this);
    private boolean latched;

    public Button(Context context, CharSequence text, Runnable action) {
        this(context, action);
        this.text(text);
    }

    public Button(Context context, Component text, Runnable action) {
        this(context, action);
        this.text(text);
    }

    private Button(Context context, Runnable action) {
        super(context);
        Objects.requireNonNull(action, "action");
        int nativeTextColor = Color.toArgb(this.getCurrentTextColor());
        int nativeControlHeight = this.getMinimumHeight();
        int nativeTextStyle = this.getTextStyle();
        this.properties.setComponent(ButtonProperties.TEXT_SIZE, Math.round(this.getTextSize()));
        this.setBackground(this.themedBackground);
        this.properties.setComponent(ButtonProperties.TEXT_COLOR, nativeTextColor);
        this.properties.setComponent(ButtonProperties.BACKGROUND_COLOR, 0);
        this.properties.setComponent(ButtonProperties.CORNER_RADIUS, 0);
        this.properties.setComponent(ButtonProperties.CONTROL_HEIGHT, nativeControlHeight);
        this.properties.setComponent(ButtonProperties.FONT_WEIGHT, nativeTextStyle);
        this.properties.setComponent(ButtonProperties.ELEVATION, 0);
        this.properties.setComponent(ButtonProperties.TOP_HIGHLIGHT, 0);
        this.properties.setComponent(ButtonProperties.TOP_HIGHLIGHT_HEIGHT, 0);
        this.setOnClickListener(view -> action.run());
    }

    public Button text(CharSequence text) {
        this.properties.set(ButtonProperties.TEXT, text);
        return this;
    }

    public Button text(Component text) {
        this.properties.bind(ButtonProperties.TEXT, MinecraftText.value(text));
        return this;
    }

    public Button text(Value<? extends CharSequence> text) {
        this.properties.bind(ButtonProperties.TEXT, text);
        return this;
    }

    public Button visible(boolean visible) {
        this.properties.set(ButtonProperties.VISIBLE, visible);
        return this;
    }

    public Button visible(Value<Boolean> visible) {
        this.properties.bind(ButtonProperties.VISIBLE, visible);
        return this;
    }

    public Button enabled(boolean enabled) {
        this.properties.set(ButtonProperties.ENABLED, enabled);
        return this;
    }

    public Button enabled(Value<Boolean> enabled) {
        this.properties.bind(ButtonProperties.ENABLED, enabled);
        return this;
    }

    public Button pressed(boolean pressed) {
        this.properties.set(ButtonProperties.PRESSED, pressed);
        return this;
    }

    public Button pressed(Value<Boolean> pressed) {
        this.properties.bind(ButtonProperties.PRESSED, pressed);
        return this;
    }

    public Button background(Token<Integer> token) {
        this.properties.bind(ButtonProperties.BACKGROUND_COLOR, new ThemeValue<>(this, token));
        return this;
    }

    public Button tag(Object tag) {
        this.setTag(tag);
        return this;
    }

    public Button classes(String... names) {
        this.style.classes(names);
        return this;
    }

    public Button part(String name) {
        this.style.part(name);
        return this;
    }

    public Button state(String name, Value<Boolean> value) {
        this.style.state(name, value);
        return this;
    }

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        if (handled) UiSounds.playClick();
        return handled;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.style.mount();
        this.properties.mount();
    }

    @Override
    protected void onDetachedFromWindow() {
        RuntimeException failure = null;
        failure = LifecycleCleanup.attempt(failure, this.properties::unmount);
        failure = LifecycleCleanup.attempt(failure, this.style::unmount);
        failure = LifecycleCleanup.attempt(failure, super::onDetachedFromWindow);
        LifecycleCleanup.finish(failure);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (this.style != null) {
            this.style.interactionChanged();
        }
    }

    StyleMetadata styleMetadata() {
        return this.style;
    }

    void applyStylesheetProperty(String property, StyleValue value) {
        switch (property) {
            case "color" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    ButtonProperties.TEXT_COLOR,
                    value
            );
            case "background" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    ButtonProperties.BACKGROUND_COLOR,
                    value
            );
            case "corner-radius" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    ButtonProperties.CORNER_RADIUS,
                    value
            );
            case "control-height" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    ButtonProperties.CONTROL_HEIGHT,
                    value
            );
            case "text-size" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    ButtonProperties.TEXT_SIZE,
                    value
            );
            case "font-weight" -> this.applyFontWeight(value);
            case "elevation" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    ButtonProperties.ELEVATION,
                    value
            );
            case "top-highlight" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    ButtonProperties.TOP_HIGHLIGHT,
                    value
            );
            case "top-highlight-height" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    ButtonProperties.TOP_HIGHLIGHT_HEIGHT,
                    value
            );
            default -> {
            }
        }
    }

    void applyBackgroundColor(int color) {
        this.themedBackground.setColor(color);
    }

    void applyCornerRadius(int radius) {
        this.themedBackground.setCornerRadius(radius);
    }

    void applyElevation(int elevation) {
        this.setElevation(elevation);
    }

    void applyTopHighlight(int color) {
        this.themedBackground.setHighlightColor(color);
    }

    void applyTopHighlightHeight(int height) {
        this.themedBackground.setHighlightHeight(height);
    }

    void applyLatched(boolean latched) {
        if (this.latched == latched) return;

        this.latched = latched;
        this.style.interactionChanged();
    }

    boolean isVisuallyPressed() {
        return this.latched || this.isPressed();
    }

    private void applyFontWeight(StyleValue value) {
        if (value == null) {
            this.properties.clearStylesheet(ButtonProperties.FONT_WEIGHT);
        } else if (value instanceof StyleValue.Keyword keyword) {
            int textStyle = switch (keyword.value()) {
                case "normal" -> Typeface.NORMAL;
                case "bold" -> Typeface.BOLD;
                default -> throw new IllegalArgumentException(
                        "Unsupported font weight '" + keyword.value() + "'"
                );
            };
            this.properties.setStylesheet(ButtonProperties.FONT_WEIGHT, textStyle);
        }
    }

    private static final class RaisedBackground extends ShapeDrawable {

        private final Paint lighting = new Paint();
        private int highlightColor;
        private int highlightHeight;

        private void setHighlightColor(int color) {
            if (this.highlightColor == color) return;

            this.highlightColor = color;
            this.invalidateSelf();
        }

        private void setHighlightHeight(int height) {
            if (this.highlightHeight == height) return;

            this.highlightHeight = height;
            this.invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (this.highlightColor == 0 || this.highlightHeight == 0) return;

            Rect bounds = this.getBounds();
            float radius = Math.min(this.getCornerRadius(), Math.max(0, bounds.height() / 2.0f));
            float inset = Math.min(radius, Math.max(0, bounds.width() / 2.0f));
            this.lighting.setColor(this.highlightColor);
            canvas.drawRect(
                    bounds.left + inset,
                    bounds.top,
                    bounds.right - inset,
                    Math.min(bounds.bottom, bounds.top + this.highlightHeight),
                    this.lighting
            );
        }
    }
}

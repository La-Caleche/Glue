package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.internal.text.MinecraftText;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleValue;
import fr.lacaleche.glue.mcsx.client.theme.Token;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.widget.TextView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class Text extends TextView {

    private final PropertyScope<Text> properties = new PropertyScope<>(this);
    private final StyleMetadata style = new StyleMetadata(this);

    public Text(Context context, CharSequence text) {
        super(context);
        this.properties.setComponent(
                TextProperties.COLOR,
                Color.toArgb(this.getCurrentTextColor())
        );
        this.properties.setComponent(TextProperties.TEXT_SIZE, Math.round(this.getTextSize()));
        this.text(text);
    }

    public Text(Context context, Value<? extends CharSequence> text) {
        super(context);
        this.properties.setComponent(
                TextProperties.COLOR,
                Color.toArgb(this.getCurrentTextColor())
        );
        this.properties.setComponent(TextProperties.TEXT_SIZE, Math.round(this.getTextSize()));
        this.text(text);
    }

    public Text(Context context, Component text) {
        this(context, MinecraftText.value(text));
    }

    public Text text(CharSequence text) {
        this.properties.set(TextProperties.TEXT, text);
        return this;
    }

    public Text text(Value<? extends CharSequence> text) {
        this.properties.bind(TextProperties.TEXT, text);
        return this;
    }

    public Text text(Component text) {
        return this.text(MinecraftText.value(text));
    }

    public Text visible(boolean visible) {
        this.properties.set(TextProperties.VISIBLE, visible);
        return this;
    }

    public Text visible(Value<Boolean> visible) {
        this.properties.bind(TextProperties.VISIBLE, visible);
        return this;
    }

    public Text color(int color) {
        this.properties.set(TextProperties.COLOR, color);
        return this;
    }

    public Text color(Value<? extends Integer> color) {
        this.properties.bind(TextProperties.COLOR, color);
        return this;
    }

    public Text color(Token<Integer> token) {
        this.properties.bind(TextProperties.COLOR, new ThemeValue<>(this, token));
        return this;
    }

    public Text textSize(int size) {
        this.properties.set(TextProperties.TEXT_SIZE, size);
        return this;
    }

    public Text tag(Object tag) {
        this.setTag(tag);
        return this;
    }

    public Text classes(String... names) {
        this.style.classes(names);
        return this;
    }

    public Text part(String name) {
        this.style.part(name);
        return this;
    }

    public Text state(String name, Value<Boolean> value) {
        this.style.state(name, value);
        return this;
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
                    TextProperties.COLOR,
                    value
            );
            case "text-size" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    TextProperties.TEXT_SIZE,
                    value
            );
            default -> {
            }
        }
    }
}

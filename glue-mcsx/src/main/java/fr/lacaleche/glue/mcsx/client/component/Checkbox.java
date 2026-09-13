package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.internal.text.MinecraftText;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleValue;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.widget.CheckBox;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

import java.util.Objects;

@Environment(EnvType.CLIENT)
public final class Checkbox extends CheckBox {

    private final PropertyScope<Checkbox> properties = new PropertyScope<>(this);
    private final StyleMetadata style = new StyleMetadata(this);
    private Signal<Boolean> checkedSignal;
    private Subscription checkedSubscription;
    private ColorStateList nativeIndicatorTint;
    private boolean applyingBoundChecked;
    private boolean checkedBindingMounted;
    private boolean directCheckedSet;

    public Checkbox(Context context, CharSequence text) {
        super(context);
        this.initialize();
        this.text(text);
    }

    public Checkbox(Context context, Component text) {
        super(context);
        this.initialize();
        this.text(text);
    }

    public Checkbox text(CharSequence text) {
        this.properties.set(CheckboxProperties.TEXT, text);
        return this;
    }

    public Checkbox text(Component text) {
        this.properties.bind(CheckboxProperties.TEXT, MinecraftText.value(text));
        return this;
    }

    public Checkbox text(Value<? extends CharSequence> text) {
        this.properties.bind(CheckboxProperties.TEXT, text);
        return this;
    }

    public Checkbox checked(boolean checked) {
        if (this.checkedSignal != null) {
            throw new IllegalStateException("Checkbox already has a checked signal");
        }
        this.setChecked(checked);
        this.directCheckedSet = true;
        return this;
    }

    public Checkbox checked(Signal<Boolean> checked) {
        if (this.isAttachedToWindow()) {
            throw new IllegalStateException("Checked signal must be installed before attachment");
        }
        if (this.checkedSignal != null) {
            throw new IllegalStateException("Checkbox already has a checked signal");
        }
        if (this.directCheckedSet) {
            throw new IllegalStateException("Checkbox already has direct checked state");
        }
        this.checkedSignal = Objects.requireNonNull(checked, "checked");
        this.applyBoundChecked(checked.get());
        return this;
    }

    public Checkbox visible(boolean visible) {
        this.properties.set(CheckboxProperties.VISIBLE, visible);
        return this;
    }

    public Checkbox visible(Value<Boolean> visible) {
        this.properties.bind(CheckboxProperties.VISIBLE, visible);
        return this;
    }

    public Checkbox enabled(boolean enabled) {
        this.properties.set(CheckboxProperties.ENABLED, enabled);
        return this;
    }

    public Checkbox enabled(Value<Boolean> enabled) {
        this.properties.bind(CheckboxProperties.ENABLED, enabled);
        return this;
    }

    public Checkbox tag(Object tag) {
        this.setTag(tag);
        return this;
    }

    public Checkbox classes(String... names) {
        this.style.classes(names);
        return this;
    }

    public Checkbox part(String name) {
        this.style.part(name);
        return this;
    }

    public Checkbox state(String name, Value<Boolean> value) {
        this.style.state(name, value);
        return this;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.style.mount();
        this.properties.mount();
        this.mountCheckedBinding();
    }

    @Override
    protected void onDetachedFromWindow() {
        RuntimeException failure = null;
        failure = LifecycleCleanup.attempt(failure, this::unmountCheckedBinding);
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEY_SPACE && event.isCanceled()) return true;

        return super.onKeyDown(keyCode == KeyEvent.KEY_SPACE ? KeyEvent.KEY_ENTER : keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean space = keyCode == KeyEvent.KEY_SPACE;
        if (space && (event.isCanceled() || !this.isEnabled())) {
            this.setPressed(false);
            return true;
        }

        boolean handled = super.onKeyUp(space ? KeyEvent.KEY_ENTER : keyCode, event);
        return space || handled;
    }

    @Override
    public void setCheckedState(int checkedState) {
        super.setCheckedState(checkedState);
        if (!this.checkedBindingMounted || this.applyingBoundChecked) return;

        boolean checked = checkedState == STATE_CHECKED;
        this.checkedSignal.set(checked);
        if (checkedState == STATE_INDETERMINATE) {
            this.applyBoundChecked(false);
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
                    CheckboxProperties.TEXT_COLOR,
                    value
            );
            case "indicator-tint" -> this.applyIndicatorTint(value);
            case "control-height" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    CheckboxProperties.CONTROL_HEIGHT,
                    value
            );
            case "text-size" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    CheckboxProperties.TEXT_SIZE,
                    value
            );
            default -> {
            }
        }
    }

    private void initialize() {
        this.setFocusable(true);
        this.setFocusableInTouchMode(true);
        this.setClickable(true);
        this.nativeIndicatorTint = this.getButtonTintList();
        this.properties.setComponent(
                CheckboxProperties.TEXT_COLOR,
                Color.toArgb(this.getCurrentTextColor())
        );
        this.properties.setComponent(
                CheckboxProperties.INDICATOR_TINT,
                this.nativeIndicatorTint
        );
        this.properties.setComponent(
                CheckboxProperties.CONTROL_HEIGHT,
                this.getMinimumHeight()
        );
        this.properties.setComponent(
                CheckboxProperties.TEXT_SIZE,
                Math.round(this.getTextSize())
        );
    }

    private void applyIndicatorTint(StyleValue value) {
        if (value == null) {
            this.properties.clearStylesheet(CheckboxProperties.INDICATOR_TINT);
        } else if (value instanceof StyleValue.Keyword keyword) {
            if (keyword.value().equals("theme")) {
                this.properties.bindStylesheet(
                        CheckboxProperties.INDICATOR_TINT,
                        new ThemeValue<>(this, ThemeTokens.CHECKBOX_INDICATOR)
                );
            } else {
                this.properties.setStylesheet(
                        CheckboxProperties.INDICATOR_TINT,
                        this.nativeIndicatorTint
                );
            }
        }
    }

    private void mountCheckedBinding() {
        if (this.checkedSignal == null || this.checkedSubscription != null) return;

        this.applyBoundChecked(this.checkedSignal.get());
        this.checkedBindingMounted = true;
        try {
            this.checkedSubscription = Objects.requireNonNull(
                    this.checkedSignal.subscribe(this::applyBoundChecked),
                    "checked subscription"
            );
        } catch (RuntimeException exception) {
            this.checkedBindingMounted = false;
            throw exception;
        }
    }

    private void unmountCheckedBinding() {
        Subscription subscription = this.checkedSubscription;
        if (subscription == null) return;

        this.checkedSubscription = null;
        this.checkedBindingMounted = false;
        subscription.close();
    }

    private void applyBoundChecked(Boolean checked) {
        boolean required = Objects.requireNonNull(checked, "checked value");
        int requiredState = required ? STATE_CHECKED : STATE_UNCHECKED;
        if (this.getCheckedState() == requiredState) return;

        this.applyingBoundChecked = true;
        try {
            this.setChecked(required);
        } finally {
            this.applyingBoundChecked = false;
        }
    }
}

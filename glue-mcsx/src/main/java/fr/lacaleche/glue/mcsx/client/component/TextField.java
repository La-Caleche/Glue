package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.UiSounds;
import fr.lacaleche.glue.mcsx.client.internal.text.MinecraftText;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.widget.EditText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class TextField extends EditText {

    private final PropertyScope<TextField> properties = new PropertyScope<>(this);
    private final ShapeDrawable themedBackground = new ShapeDrawable();
    private final StyleMetadata style = new StyleMetadata(this);
    private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence text, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence text, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable text) {
            if (!TextField.this.applyingBoundText && TextField.this.textSignal != null) {
                TextField.this.textSignal.set(text.toString());
            }
        }
    };
    private Signal<String> textSignal;
    private Subscription textSubscription;
    private Runnable submitAction;
    private Consumer<Boolean> focusListener;
    private int horizontalPadding;
    private int verticalPadding;
    private boolean applyingBoundText;
    private boolean directTextSet;

    public TextField(Context context) {
        super(context);
        int nativeTextColor = Color.toArgb(this.getCurrentTextColor());
        int nativeHintColor = Color.toArgb(this.getCurrentHintTextColor());
        int nativeControlHeight = this.getMinimumHeight();
        this.horizontalPadding = this.getPaddingLeft();
        this.verticalPadding = this.getPaddingTop();
        this.properties.setComponent(
                TextFieldProperties.TEXT_SIZE,
                Math.round(this.getTextSize())
        );
        this.setBackground(this.themedBackground);
        this.properties.setComponent(TextFieldProperties.TEXT_COLOR, nativeTextColor);
        this.properties.setComponent(TextFieldProperties.HINT_TEXT_COLOR, nativeHintColor);
        this.properties.setComponent(TextFieldProperties.BACKGROUND_COLOR, 0);
        this.properties.setComponent(TextFieldProperties.CORNER_RADIUS, 0);
        this.properties.setComponent(TextFieldProperties.CONTROL_HEIGHT, nativeControlHeight);
        this.properties.setComponent(
                TextFieldProperties.HORIZONTAL_PADDING,
                this.horizontalPadding
        );
        this.properties.setComponent(TextFieldProperties.VERTICAL_PADDING, this.verticalPadding);
        this.setSingleLine(true);
    }

    public TextField text(CharSequence text) {
        if (this.textSignal != null) {
            throw new IllegalStateException("TextField already has a text signal");
        }
        this.setText(text);
        this.directTextSet = true;
        return this;
    }

    public TextField text(Signal<String> text) {
        if (this.isAttachedToWindow()) {
            throw new IllegalStateException("Text signal must be installed before attachment");
        }
        if (this.textSignal != null) {
            throw new IllegalStateException("TextField already has a text signal");
        }
        if (this.directTextSet) {
            throw new IllegalStateException("TextField already has direct text");
        }
        this.textSignal = Objects.requireNonNull(text, "text");
        this.applyBoundText(text.get());
        return this;
    }

    public TextField visible(boolean visible) {
        this.properties.set(TextFieldProperties.VISIBLE, visible);
        return this;
    }

    public TextField visible(Value<Boolean> visible) {
        this.properties.bind(TextFieldProperties.VISIBLE, visible);
        return this;
    }

    public TextField enabled(boolean enabled) {
        this.properties.set(TextFieldProperties.ENABLED, enabled);
        return this;
    }

    public TextField enabled(Value<Boolean> enabled) {
        this.properties.bind(TextFieldProperties.ENABLED, enabled);
        return this;
    }

    public TextField onSubmit(Runnable action) {
        this.submitAction = Objects.requireNonNull(action, "action");
        return this;
    }

    public TextField onFocusChanged(Consumer<Boolean> listener) {
        this.focusListener = Objects.requireNonNull(listener, "listener");
        return this;
    }

    public TextField hint(CharSequence hint) {
        this.properties.set(TextFieldProperties.HINT, hint);
        return this;
    }

    public TextField hint(Component hint) {
        this.properties.bind(TextFieldProperties.HINT, MinecraftText.value(hint));
        return this;
    }

    public TextField hint(Value<? extends CharSequence> hint) {
        this.properties.bind(TextFieldProperties.HINT, hint);
        return this;
    }

    public TextField tag(Object tag) {
        this.setTag(tag);
        return this;
    }

    public TextField classes(String... names) {
        this.style.classes(names);
        return this;
    }

    public TextField part(String name) {
        this.style.part(name);
        return this;
    }

    public TextField state(String name, Value<Boolean> value) {
        this.style.state(name, value);
        return this;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.style.mount();
        this.properties.mount();
        this.mountTextBinding();
    }

    @Override
    protected void onDetachedFromWindow() {
        RuntimeException failure = null;
        failure = LifecycleCleanup.attempt(failure, this::unmountTextBinding);
        failure = LifecycleCleanup.attempt(failure, this.properties::unmount);
        failure = LifecycleCleanup.attempt(failure, this.style::unmount);
        failure = LifecycleCleanup.attempt(failure, super::onDetachedFromWindow);
        LifecycleCleanup.finish(failure);
    }

    void mountTextBinding() {
        if (this.textSignal == null || this.textSubscription != null) return;

        this.applyBoundText(this.textSignal.get());
        this.addTextChangedListener(this.textWatcher);
        try {
            this.textSubscription = Objects.requireNonNull(
                    this.textSignal.subscribe(this::applyBoundText),
                    "text subscription"
            );
        } catch (RuntimeException exception) {
            this.removeTextChangedListener(this.textWatcher);
            throw exception;
        }
    }

    void unmountTextBinding() {
        Subscription subscription = this.textSubscription;
        if (subscription == null) return;

        this.textSubscription = null;
        RuntimeException failure = null;
        failure = LifecycleCleanup.attempt(
                failure,
                () -> this.removeTextChangedListener(this.textWatcher)
        );
        failure = LifecycleCleanup.attempt(failure, subscription::close);
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
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (this.submitAction != null
                && this.isEnabled()
                && event.hasNoModifiers()
                && !event.isCanceled()
                && (keyCode == KeyEvent.KEY_ENTER || keyCode == KeyEvent.KEY_KP_ENTER)) {
            UiSounds.playClick();
            this.submitAction.run();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (this.focusListener != null) {
            this.focusListener.accept(gainFocus);
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
                    TextFieldProperties.TEXT_COLOR,
                    value
            );
            case "background" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    TextFieldProperties.BACKGROUND_COLOR,
                    value
            );
            case "corner-radius" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    TextFieldProperties.CORNER_RADIUS,
                    value
            );
            case "control-height" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    TextFieldProperties.CONTROL_HEIGHT,
                    value
            );
            case "text-size" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    TextFieldProperties.TEXT_SIZE,
                    value
            );
            case "hint-color" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    TextFieldProperties.HINT_TEXT_COLOR,
                    value
            );
            case "padding-horizontal" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    TextFieldProperties.HORIZONTAL_PADDING,
                    value
            );
            case "padding-vertical" -> StylesheetProperty.apply(
                    this,
                    this.properties,
                    TextFieldProperties.VERTICAL_PADDING,
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

    void applyHorizontalPadding(int padding) {
        this.horizontalPadding = padding;
        this.setPadding(padding, this.verticalPadding, padding, this.verticalPadding);
    }

    void applyVerticalPadding(int padding) {
        this.verticalPadding = padding;
        this.setPadding(this.horizontalPadding, padding, this.horizontalPadding, padding);
    }

    private void applyBoundText(String text) {
        String value = Objects.requireNonNull(text, "Bound text");
        if (value.contentEquals(this.getText())) return;

        int selectionStart = this.getSelectionStart();
        int selectionEnd = this.getSelectionEnd();
        this.applyingBoundText = true;
        try {
            this.setText(value);
            if (selectionStart >= 0 && selectionEnd >= 0) {
                int length = this.getText().length();
                this.setSelection(
                        Math.min(selectionStart, length),
                        Math.min(selectionEnd, length)
                );
            }
        } finally {
            this.applyingBoundText = false;
        }
    }
}

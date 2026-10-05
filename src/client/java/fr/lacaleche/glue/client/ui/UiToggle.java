package fr.lacaleche.glue.client.ui;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Switches a {@code boolean}, read from its supplier each frame and reported on each press. */
public final class UiToggle extends FlatButton {

    private final BooleanSupplier value;
    private final BooleanConsumer onChange;

    public UiToggle(Component name, BooleanSupplier value, BooleanConsumer onChange) {
        super(name);
        this.value = Objects.requireNonNull(value, "value");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    @Override
    public void onPress() {
        this.onChange.accept(!this.value.getAsBoolean());
    }

    @Override
    Component value() {
        return this.value.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    @Override
    int valueColor(UiStyle style) {
        return this.value.getAsBoolean() ? style.accent() : style.muted();
    }
}

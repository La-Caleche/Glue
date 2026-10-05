package fr.lacaleche.glue.client.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Steps through a fixed list of values, such as an enum's constants: a press reports the next one,
 * with Shift the previous one.
 *
 * @param <E> the value type
 */
public final class UiCycle<E> extends FlatButton {

    private final List<E> values;
    private final Function<E, Component> names;
    private final Supplier<E> value;
    private final Consumer<E> onChange;

    public UiCycle(Component name, List<E> values, Function<E, Component> names, Supplier<E> value,
            Consumer<E> onChange) {
        super(name);
        if (values.isEmpty()) throw new IllegalArgumentException("A cycle needs at least one value");

        this.values = List.copyOf(values);
        this.names = Objects.requireNonNull(names, "names");
        this.value = Objects.requireNonNull(value, "value");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    @Override
    public void onPress() {
        int step = Screen.hasShiftDown() ? -1 : 1;
        int index = Math.max(0, this.values.indexOf(this.value.get()));
        this.onChange.accept(this.values.get(Math.floorMod(index + step, this.values.size())));
    }

    @Override
    Component value() {
        return this.names.apply(this.value.get());
    }
}

package fr.lacaleche.glue.client.ui;

import fr.lacaleche.glue.math.Color;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What a {@link UiPage} builds with. A page adds rows, which fill the content area with one
 * {@link UiRowList}, or lays out its own widgets in {@link #area()} and adds them with {@link #add}, or
 * both, keeping its rows to a part of the area with {@link #rows(ScreenRectangle)}. Every row method
 * returns its control, so the page can keep it.
 */
public final class UiPageBuilder {

    private final ScreenRectangle area;
    private final boolean descriptionTooltips;
    private final Consumer<AbstractWidget> addWidget;
    private @Nullable UiRowList rows;

    UiPageBuilder(ScreenRectangle area, boolean descriptionTooltips, Consumer<AbstractWidget> addWidget) {
        this.area = area;
        this.descriptionTooltips = descriptionTooltips;
        this.addWidget = addWidget;
    }

    /** The content area, in GUI pixels. */
    public ScreenRectangle area() {
        return this.area;
    }

    /** Adds a widget the page has placed itself, and returns it. */
    public <T extends AbstractWidget> T add(T widget) {
        this.addWidget.accept(widget);
        return widget;
    }

    /** The page's row list, created to fill the content area on first use. */
    public UiRowList rows() {
        if (this.rows == null) this.rows = this.add(new UiRowList(this.area, this.descriptionTooltips));

        return this.rows;
    }

    /**
     * The page's row list over part of the content area, for a page that places other widgets beside
     * it. Call it before any row method; the screen's description panel still follows its rows.
     */
    public UiRowList rows(ScreenRectangle part) {
        if (this.rows != null) throw new IllegalStateException("The page's rows already exist");

        this.rows = this.add(new UiRowList(part, this.descriptionTooltips));
        return this.rows;
    }

    public void section(Component title) {
        this.rows().section(title);
    }

    public UiButton button(Component label, @Nullable Component description, Component text, Runnable action) {
        return this.rows().row(label, description, new UiButton(text, action));
    }

    public UiToggle toggle(Component label, @Nullable Component description, BooleanSupplier value,
            BooleanConsumer onChange) {
        return this.rows().row(label, description, new UiToggle(label, value, onChange));
    }

    public <E> UiCycle<E> cycle(Component label, @Nullable Component description, List<E> values,
            Function<E, Component> names, Supplier<E> value, Consumer<E> onChange) {
        return this.rows().row(label, description, new UiCycle<>(label, values, names, value, onChange));
    }

    public UiSlider slider(Component label, @Nullable Component description, double min, double max, double step,
            DoubleSupplier value, DoubleConsumer onChange) {
        return this.rows().row(label, description, new UiSlider(label, min, max, step, value, onChange));
    }

    /** A number field; {@code min} and {@code max} may be infinite for an unbounded value. */
    public UiNumberField number(Component label, @Nullable Component description, double min, double max, double step,
            DoubleSupplier value, DoubleConsumer onChange) {
        return this.rows().row(label, description, new UiNumberField(label, min, max, step, value, onChange));
    }

    public UiTextField textField(Component label, @Nullable Component description, Supplier<String> value,
            Consumer<String> onChange) {
        return this.rows().row(label, description, new UiTextField(label, value, onChange));
    }

    public UiLabel label(Component label, @Nullable Component description, Supplier<Component> value) {
        return this.rows().row(label, description, new UiLabel(label, value));
    }

    public UiColorSwatch color(Component label, @Nullable Component description, Supplier<Color> value) {
        return this.rows().row(label, description, new UiColorSwatch(label, value));
    }

    @Nullable UiRowList builtRows() {
        return this.rows;
    }
}

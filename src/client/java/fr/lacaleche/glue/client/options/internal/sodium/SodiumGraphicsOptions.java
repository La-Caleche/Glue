package fr.lacaleche.glue.client.options.internal.sodium;

import fr.lacaleche.glue.client.options.GraphicsOptionsPage;
import fr.lacaleche.glue.client.options.OptionRow;
import net.caffeinemc.mods.sodium.client.gui.options.Option;
import net.caffeinemc.mods.sodium.client.gui.options.OptionFlag;
import net.caffeinemc.mods.sodium.client.gui.options.OptionGroup;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpact;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpl;
import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.caffeinemc.mods.sodium.client.gui.options.control.CyclingControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl;
import net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A graphics options page as a page of Sodium's settings screen, which stands in for the video settings while
 * Sodium is installed. Loaded only from the Sodium mixin, so only with Sodium present.
 */
@Environment(EnvType.CLIENT)
public final class SodiumGraphicsOptions {

    private SodiumGraphicsOptions() {
    }

    /** {@code page}'s rows, sharing one storage so a single Apply puts them all in force. */
    public static <S> SodiumGraphicsPage page(GraphicsOptionsPage<S> page) {
        Form<S> form = new Form<>(page);
        List<SodiumGraphicsPage.Section> sections = new ArrayList<>();
        for (GraphicsOptionsPage.Section<S> section : page.sections()) {
            OptionGroup.Builder group = OptionGroup.createBuilder();
            for (OptionRow<S> row : section.rows()) group.add(form.option(row));
            sections.add(new SodiumGraphicsPage.Section(section.title(), group.build()));
        }
        form.page = new SodiumGraphicsPage(Component.translatable(page.key("page")), sections);
        return form.page;
    }

    private static OptionImpact impact(fr.lacaleche.glue.client.options.OptionImpact impact) {
        return OptionImpact.valueOf(impact.name());
    }

    /**
     * The page's value rows over the stored value, which together read and set as one {@code S} the values the
     * controls show, before Apply puts them in force.
     */
    private static final class Form<S> {

        private final Pending<S> pending;
        private final List<Bound<S, ?>> values = new ArrayList<>();
        /** The page these options are laid out on, set once it is built. */
        private SodiumGraphicsPage page;

        Form(GraphicsOptionsPage<S> page) {
            this.pending = new Pending<>(page, this);
        }

        Option<?> option(OptionRow<S> row) {
            return switch (row) {
                case OptionRow.Slider<S> slider -> bind(slider.get(), slider.with(), OptionImpl
                        .createBuilder(int.class, pending)
                        .setControl(option -> new SliderControl(option, slider.min(), slider.max(), slider.step(),
                                value -> slider.label().apply(value))), slider);
                case OptionRow.Toggle<S> toggle -> bind(toggle.get(), toggle.with(), OptionImpl
                        .createBuilder(boolean.class, pending)
                        .setControl(TickBoxControl::new), toggle);
                case OptionRow.Choice<S, ?> choice -> choice(choice);
                case OptionRow.Level<S> level -> new LevelOption<>(level, this);
            };
        }

        private <E extends Enum<E>> Option<E> choice(OptionRow.Choice<S, E> choice) {
            Component[] labels = Arrays.stream(choice.type().getEnumConstants()).map(choice.label())
                    .toArray(Component[]::new);
            return bind(choice.get(), choice.with(), OptionImpl.createBuilder(choice.type(), pending)
                    .setControl(option -> new CyclingControl<>(option, choice.type(), labels)), choice);
        }

        private <T> OptionImpl<Pending<S>, T> bind(Function<S, T> get, BiFunction<S, T, S> with,
                                                    OptionImpl.Builder<Pending<S>, T> builder, OptionRow<S> row) {
            OptionImpl<Pending<S>, T> option = builder
                    .setName(Component.translatable(row.key()))
                    .setTooltip(Component.translatable(row.key() + ".tooltip"))
                    // Read when the option is built and on Undo, which both want what is in force, whatever
                    // level is pending and whichever row Undo resets first.
                    .setBinding((stored, value) -> stored.value = with.apply(stored.value, value),
                            stored -> get.apply(stored.applied))
                    .setImpact(impact(row.impact()))
                    .build();
            values.add(new Bound<>(option, get, with));
            return option;
        }

        /** The settings the controls show, Applied or not. */
        S current() {
            S settings = pending.value;
            for (Bound<S, ?> bound : values) settings = bound.into(settings);
            return settings;
        }

        /**
         * Shows {@code settings} on the controls, pending Apply like a change made on them, and lays the page out
         * again so each control draws its new value.
         */
        void show(S settings) {
            // Values no row shows ride in the stored value; Apply saves it with every row's value folded in.
            pending.value = settings;
            for (Bound<S, ?> bound : values) bound.show(settings);
            page.relayout();
        }
    }

    /** A value row's option, with the row's reading and writing of {@code S}. */
    private record Bound<S, T>(OptionImpl<Pending<S>, T> option, Function<S, T> get, BiFunction<S, T, S> with) {

        S into(S settings) {
            return with.apply(settings, option.getValue());
        }

        void show(S settings) {
            option.setValue(get.apply(settings));
        }
    }

    /**
     * A level row's option, showing the level the controls are at and setting theirs. It holds no value of its
     * own, so Apply has nothing of it to save.
     */
    private static final class LevelOption<S> implements Option<S> {

        private final OptionRow.Level<S> level;
        private final Form<S> form;
        private final Control<S> control = new Control<>() {
            @Override
            public Option<S> getOption() {
                return LevelOption.this;
            }

            @Override
            public ControlElement<S> createElement(Dim2i dim) {
                return new LevelElement<>(LevelOption.this, dim);
            }

            @Override
            public int getMaxWidth() {
                return 70;
            }
        };

        LevelOption(OptionRow.Level<S> level, Form<S> form) {
            this.level = level;
            this.form = form;
        }

        @Override
        public Component getName() {
            return Component.translatable(level.key());
        }

        @Override
        public Component getTooltip() {
            return Component.translatable(level.key() + ".tooltip");
        }

        @Override
        public OptionImpact getImpact() {
            return OptionImpact.VARIES;
        }

        @Override
        public Control<S> getControl() {
            return control;
        }

        @Override
        public S getValue() {
            return form.current();
        }

        @Override
        public void setValue(S settings) {
            form.show(settings);
        }

        /** Undo: the values no row shows go back to those last put in force; the rows reset themselves. */
        @Override
        public void reset() {
            form.pending.value = form.pending.applied;
        }

        @Override
        public OptionStorage<?> getStorage() {
            return form.pending;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        /** True once a level was picked and not yet Applied, so Apply saves even values no row shows. */
        @Override
        public boolean hasChanged() {
            return !form.pending.value.equals(form.pending.applied);
        }

        @Override
        public void applyChanges() {
            // The controls it sets apply their own changes.
        }

        @Override
        public Collection<OptionFlag> getFlags() {
            return List.of();
        }
    }

    /** A level row: its label at the right, cycled forward by a click or Enter, backward with Shift. */
    private static final class LevelElement<S> extends ControlElement<S> {

        private final OptionRow.Level<S> level;

        LevelElement(LevelOption<S> option, Dim2i dim) {
            super(option, dim);
            this.level = option.level;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            Component label = level.label().apply(option.getValue());
            drawString(graphics, label, dim.getLimitX() - getStringWidth(label) - 6, dim.getCenterY() - 4, -1);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !dim.containsCursor(mouseX, mouseY)) return false;
            cycle();
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!isFocused() || !CommonInputs.selected(keyCode)) return false;
            cycle();
            return true;
        }

        private void cycle() {
            playClickSound();
            option.setValue(level.cycle().apply(option.getValue(), Screen.hasShiftDown()));
        }
    }

    /**
     * The value under Sodium's controls until its Apply button: the values no row shows, the saved ones when the
     * screen opens, then those of the last level picked. Sodium binds only the options that changed into it before
     * saving, which would lose a row moved back to where it opened; so saving puts in force what every control
     * shows, folded into this value.
     */
    private static final class Pending<S> implements OptionStorage<Pending<S>> {

        private final GraphicsOptionsPage<S> page;
        private final Form<S> form;
        /** The settings last put in force from this screen, the saved ones until its first Apply. */
        private S applied;
        private S value;

        Pending(GraphicsOptionsPage<S> page, Form<S> form) {
            this.page = page;
            this.form = form;
            this.applied = page.saved();
            this.value = applied;
        }

        @Override
        public Pending<S> getData() {
            return this;
        }

        @Override
        public void save() {
            S settings = form.current();
            page.apply(settings);
            applied = settings;
            value = settings;
        }
    }
}

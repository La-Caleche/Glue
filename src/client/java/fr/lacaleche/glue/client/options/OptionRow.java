package fr.lacaleche.glue.client.options;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * One row of a {@link GraphicsOptionsPage}, over the page's settings value {@code S}. A value row reads its value
 * from {@code S} and writes it into a copy of {@code S}; a {@link Level} row has no value of its own and sets
 * several at once. A row's name is the translation of its {@link #key()}, its tooltip that of the key's
 * {@code .tooltip}.
 *
 * @param <S> the settings value of the page, immutable
 */
public sealed interface OptionRow<S> permits OptionRow.Slider, OptionRow.Toggle, OptionRow.Choice, OptionRow.Level {

    /** The translation key of the row's name. */
    String key();

    OptionImpact impact();

    /**
     * Whether the vanilla screen may set this row beside the next half row, two to a line. Sodium's page shows
     * one row per line whatever this says.
     */
    boolean half();

    /** This row, set beside the next half row on the vanilla screen. */
    OptionRow<S> asHalf();

    /**
     * Whole numbers from {@code min} to {@code max} by {@code step}, each shown as {@code label} gives it.
     * {@code max - min} is a multiple of {@code step}, as Sodium's slider requires.
     */
    record Slider<S>(String key, int min, int max, int step, IntFunction<Component> label,
                     Function<S, Integer> get, BiFunction<S, Integer, S> with, OptionImpact impact, boolean half)
            implements OptionRow<S> {

        public Slider {
            Objects.requireNonNull(key);
            if (step <= 0 || max <= min || (max - min) % step != 0) {
                throw new IllegalArgumentException(key + ": a slider runs from min to a greater max by a step that"
                        + " divides the range, not " + min + ".." + max + " by " + step);
            }
        }

        /** The last position, counting steps from 0 at {@code min}. */
        public int positions() {
            return (max - min) / step;
        }

        /** The value at {@code position} steps from {@code min}. */
        public int valueAt(int position) {
            return min + position * step;
        }

        /** The position nearest {@code value}, held within the slider's range. */
        public int positionOf(int value) {
            return Math.round((Math.clamp(value, min, max) - min) / (float) step);
        }

        @Override
        public Slider<S> asHalf() {
            return new Slider<>(key, min, max, step, label, get, with, impact, true);
        }
    }

    /** On or off. */
    record Toggle<S>(String key, Function<S, Boolean> get, BiFunction<S, Boolean, S> with, OptionImpact impact,
                     boolean half) implements OptionRow<S> {

        @Override
        public Toggle<S> asHalf() {
            return new Toggle<>(key, get, with, impact, true);
        }
    }

    /**
     * One constant of {@code type}, cycled through in declaration order. {@code label} gives the whole text the
     * row shows for a constant, on both screens.
     */
    record Choice<S, E extends Enum<E>>(String key, Class<E> type, Function<E, Component> label,
                                        Function<S, E> get, BiFunction<S, E, S> with, OptionImpact impact,
                                        boolean half) implements OptionRow<S> {

        @Override
        public Choice<S, E> asHalf() {
            return new Choice<>(key, type, label, get, with, impact, true);
        }
    }

    /**
     * A row over several values, a preset's: it shows {@code label} of the values the other rows are at and sets
     * them to {@code cycle} of those values, forward, or backward when the boolean is true (Shift held). It holds
     * no value of its own and always takes a whole line.
     */
    record Level<S>(String key, Function<S, Component> label, BiFunction<S, Boolean, S> cycle) implements OptionRow<S> {

        @Override
        public OptionImpact impact() {
            return OptionImpact.VARIES;
        }

        @Override
        public boolean half() {
            return false;
        }

        @Override
        public Level<S> asHalf() {
            throw new UnsupportedOperationException(key + ": a level row takes a whole line");
        }
    }
}

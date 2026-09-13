package fr.lacaleche.glue.mcsx.client.reactive;

import java.util.Objects;
import java.util.function.BinaryOperator;

/**
 * Values that {@link Value#map} and {@link Value#combine} cannot express on their own: a value that
 * never changes, and boolean folds over more than two sources.
 */
public final class Values {

    private Values() {
    }

    /**
     * A value that never changes. Binding one costs nothing at runtime, and it says in the type what
     * a {@link Signal} holding a fixed value only says by convention.
     */
    public static <T> Value<T> constant(T value) {
        return new ConstantValue<>(value);
    }

    /** The logical negation of a boolean value. */
    public static Value<Boolean> not(Value<Boolean> value) {
        Objects.requireNonNull(value, "value");
        return value.map(current -> !current);
    }

    /** True while every source is true, and constantly true when no source is given. */
    @SafeVarargs
    public static Value<Boolean> all(Value<Boolean>... values) {
        return fold(values, Boolean::logicalAnd, true);
    }

    /** True while any source is true, and constantly false when no source is given. */
    @SafeVarargs
    public static Value<Boolean> any(Value<Boolean>... values) {
        return fold(values, Boolean::logicalOr, false);
    }

    private static Value<Boolean> fold(Value<Boolean>[] values, BinaryOperator<Boolean> operator,
                                       boolean identity) {
        Objects.requireNonNull(values, "values");
        Value<Boolean> folded = null;
        for (int index = 0; index < values.length; index++) {
            Value<Boolean> value = Objects.requireNonNull(values[index], "values[" + index + "]");
            folded = folded == null ? value : Value.combine(folded, value, operator);
        }
        return folded != null ? folded : constant(identity);
    }
}

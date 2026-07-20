package fr.lacaleche.glue.mcsx.core.style;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec.ColorValue;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Length;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Reflection sweep over every {@link StyleSpec.Builder} property: each must land on its
 * same-named accessor, win a merge when the overlay sets it, and survive a merge that doesn't.
 * This is the drift net for the 40-property plumbing — a property added to the Builder but
 * forgotten in a group record, {@code build()} or a group merge fails here, not in-game.
 */
class StyleSpecMergeTest {

    /** Every Builder method that sets one property (returns Builder, takes one argument). */
    private static List<Method> setters() {
        List<Method> setters = new ArrayList<>();
        for (Method method : StyleSpec.Builder.class.getDeclaredMethods()) {
            if (method.getReturnType() == StyleSpec.Builder.class && method.getParameterCount() == 1) {
                setters.add(method);
            }
        }
        assertFalse(setters.isEmpty());
        return setters;
    }

    /** A per-property distinct value, so a transposition of same-typed properties cannot hide. */
    private static Object valueFor(Class<?> type, int index, boolean overlay) {
        int salt = overlay ? 1000 : 100;
        if (type == int.class) {
            return salt + index;
        }
        if (type == float.class) {
            return salt + index + 0.5f;
        }
        if (type == boolean.class) {
            return overlay;
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants[(index + (overlay ? 1 : 0)) % constants.length];
        }
        if (type == ColorValue.class) {
            return new ColorValue.Literal(salt + index);
        }
        if (type == Length.class) {
            return Length.pixels(salt + index);
        }
        throw new AssertionError("no test value for Builder property type " + type);
    }

    private static Object get(StyleSpec spec, String property) throws ReflectiveOperationException {
        return StyleSpec.class.getMethod(property).invoke(spec);
    }

    @Test
    void everySetterLandsOnItsOwnAccessor() throws ReflectiveOperationException {
        List<Method> setters = setters();
        for (int i = 0; i < setters.size(); i++) {
            Method setter = setters.get(i);
            Object value = valueFor(setter.getParameterTypes()[0], i, true);
            StyleSpec.Builder builder = StyleSpec.builder();
            setter.invoke(builder, value);
            assertEquals(value, get(builder.build(), setter.getName()),
                    setter.getName() + " must land on its same-named accessor");
        }
    }

    @Test
    void everyPropertySetOnTheOverlayWinsAMerge() throws ReflectiveOperationException {
        List<Method> setters = setters();
        StyleSpec base = populated(setters, false);
        for (int i = 0; i < setters.size(); i++) {
            Method setter = setters.get(i);
            Object value = valueFor(setter.getParameterTypes()[0], i, true);
            StyleSpec.Builder overlay = StyleSpec.builder();
            setter.invoke(overlay, value);
            StyleSpec merged = base.merged(overlay.build());
            assertEquals(value, get(merged, setter.getName()),
                    setter.getName() + " set on the overlay must win the merge");
        }
    }

    /**
     * A flag the overlay explicitly turns OFF must beat a base that turned it on — the
     * {@code grow-0} case. A primitive flag cannot express this: its "off" is indistinguishable
     * from "unset", so no merge rule can honour both, and the flag silently keeps the base value.
     */
    @Test
    void everyBooleanSetFalseOnTheOverlayWinsAMerge() throws ReflectiveOperationException {
        List<Method> setters = setters();
        StyleSpec base = populated(setters, true);
        for (Method setter : setters) {
            if (setter.getParameterTypes()[0] != boolean.class) {
                continue;
            }
            StyleSpec.Builder overlay = StyleSpec.builder();
            setter.invoke(overlay, false);
            assertEquals(Boolean.FALSE, get(base.merged(overlay.build()), setter.getName()),
                    setter.getName() + " turned off by the overlay must win the merge");
        }
    }

    /** The tri-state itself: a flag reverted to a primitive would fail here, not only in-game. */
    @Test
    void anUnsetFlagIsDistinctFromAnExplicitlyOffOne() {
        assertNull(StyleSpec.builder().build().flex().grow());
        assertEquals(Boolean.FALSE, StyleSpec.builder().grow(false).build().flex().grow());
        assertEquals(Boolean.TRUE, StyleSpec.builder().grow(true).build().flex().grow());
    }

    /**
     * Both boolean polarities: an unset overlay property must keep the base value whether the
     * base flag was false or true (a flag whose merge reads the overlay unconditionally would
     * clobber the base here — the failure mode opposite to the one above).
     */
    @Test
    void everyPropertyUnsetOnTheOverlaySurvivesAMerge() throws ReflectiveOperationException {
        for (boolean baseBooleans : new boolean[]{false, true}) {
            List<Method> setters = setters();
            StyleSpec base = populated(setters, baseBooleans);
            StyleSpec merged = base.merged(StyleSpec.builder().build());
            for (Method setter : setters) {
                assertEquals(get(base, setter.getName()), get(merged, setter.getName()),
                        setter.getName() + " unset on the overlay must keep the base value");
            }
        }
    }

    private static StyleSpec populated(List<Method> setters, boolean booleans)
            throws ReflectiveOperationException {
        StyleSpec.Builder builder = StyleSpec.builder();
        for (int i = 0; i < setters.size(); i++) {
            Method setter = setters.get(i);
            Class<?> type = setter.getParameterTypes()[0];
            Object value = type == boolean.class ? booleans : valueFor(type, i, false);
            setter.invoke(builder, value);
        }
        return builder.build();
    }
}

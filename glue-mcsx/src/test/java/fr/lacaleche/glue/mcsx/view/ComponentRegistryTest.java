package fr.lacaleche.glue.mcsx.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ComponentRegistryTest {

    private static final NativeComponent COMPONENT = (context, element, binder) -> null;

    @Test
    void rejectsInvalidAndAmbiguousRegistrations() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.register("custom", COMPONENT);

        assertThrows(IllegalArgumentException.class, () -> registry.register("", COMPONENT));
        assertThrows(IllegalArgumentException.class, () -> registry.register("div", COMPONENT));
        assertThrows(IllegalArgumentException.class, () -> registry.register("custom", COMPONENT));
        assertThrows(NullPointerException.class, () -> registry.register("other", null));
    }
}

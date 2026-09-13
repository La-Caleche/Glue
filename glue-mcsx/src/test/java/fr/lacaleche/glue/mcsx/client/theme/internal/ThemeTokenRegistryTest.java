package fr.lacaleche.glue.mcsx.client.theme.internal;

import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import fr.lacaleche.glue.mcsx.client.theme.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ThemeTokenRegistryTest {

    @Test
    void exposesEveryWritableTokenWithItsKind() {
        assertSame(ThemeTokens.ACCENT, ThemeTokenRegistry.find("accent").token());
        assertSame(ThemeTokens.CONTROL_ON, ThemeTokenRegistry.find("control-on").token());
        assertSame(ThemeTokens.SURFACE_QUIET_HOVER, ThemeTokenRegistry.find("surface-quiet-hover").token());
        assertEquals(ThemeTokenRegistry.Kind.COLOR, ThemeTokenRegistry.find("dock-border").kind());
        assertEquals(ThemeTokenRegistry.Kind.DOCK_METRICS, ThemeTokenRegistry.find("dock-metrics").kind());
        assertEquals(null, ThemeTokenRegistry.find("checkbox-indicator"));
    }

    @Test
    void rejectsDuplicateNamesAndInstances() {
        Token<Integer> first = Token.of("first", 1);
        Token<Integer> second = Token.of("second", 2);

        assertThrows(IllegalStateException.class, () -> ThemeTokenRegistry.validate(List.of(
                new ThemeTokenRegistry.Entry("same", first, ThemeTokenRegistry.Kind.DIMENSION, first),
                new ThemeTokenRegistry.Entry("same", second, ThemeTokenRegistry.Kind.DIMENSION, second)
        )));
        assertThrows(IllegalStateException.class, () -> ThemeTokenRegistry.validate(List.of(
                new ThemeTokenRegistry.Entry("first", first, ThemeTokenRegistry.Kind.DIMENSION, first),
                new ThemeTokenRegistry.Entry("other", first, ThemeTokenRegistry.Kind.DIMENSION, first)
        )));
    }
}

package fr.lacaleche.glue.gametest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrisShadersToolTest {

    private final IrisShadersTool tool = new IrisShadersTool();

    @Test
    void rejectsAMissingArgument() {
        assertThrows(IllegalArgumentException.class, () -> tool.start(null, List.of()));
    }

    @Test
    void rejectsANonBooleanArgument() {
        assertThrows(IllegalArgumentException.class, () -> tool.start(null, List.of("maybe")));
    }

    @Test
    void rejectsExtraArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.start(null, List.of("true", "10")));
    }

    @Test
    void acceptsExactlyOneBooleanArgument() throws Exception {
        assertNotNull(tool.start(null, List.of("true")));
        assertNotNull(tool.start(null, List.of("false")));
    }
}

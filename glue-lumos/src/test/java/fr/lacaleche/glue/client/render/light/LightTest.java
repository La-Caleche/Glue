package fr.lacaleche.glue.client.render.light;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LightTest {

    @Test
    void spotNormalizesDirectionWithoutExposingMutableState() {
        Light light = Light.spot(1, 2, 3, 0, -4, 0,
                1, 0.5f, 0.25f, 2, 10, 15, 30);

        assertEquals(0f, light.directionX);
        assertEquals(-1f, light.directionY);
        assertEquals(0f, light.directionZ);
    }

    @Test
    void withShadowReturnsSameInstanceWhenUnchanged() {
        Light light = Light.point(0, 0, 0, 1, 1, 1, 1, 4);

        assertSame(light, light.withShadow(true));
        assertNotSame(light, light.withShadow(false));
    }

    @Test
    void rangeHasNoArtificialUpperLimit() {
        Light light = Light.point(0, 0, 0, 1, 1, 1, 1, 10_000);

        assertEquals(10_000f, light.range);
    }

    @Test
    void rejectsInvalidRangeColorDirectionAndCone() {
        assertThrows(IllegalArgumentException.class,
                () -> Light.point(0, 0, 0, 1, 1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Light.point(0, 0, 0, 1.1f, 1, 1, 1, 4));
        assertThrows(IllegalArgumentException.class,
                () -> Light.spot(0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 4, 10, 20));
        assertThrows(IllegalArgumentException.class,
                () -> Light.spot(0, 0, 0, 0, -1, 0, 1, 1, 1, 1, 4, 20, 10));
    }
}

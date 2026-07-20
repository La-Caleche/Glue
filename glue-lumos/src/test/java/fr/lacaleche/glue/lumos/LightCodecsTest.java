package fr.lacaleche.glue.lumos;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightCodecsTest {

    @Test
    void pointLightSurvivesNbtRoundTrip() {
        assertRoundTrips(Light.point(12.5, 64.0, -8.25, 1.0f, 0.85f, 0.6f, 2.5f, 12.0f));
    }

    @Test
    void spotLightSurvivesNbtRoundTrip() {
        assertRoundTrips(Light.spot(1.0, 2.0, 3.0, 0.3f, -0.9f, 0.1f,
                0.9f, 0.2f, 0.4f, 3.0f, 20.0f, 15.0f, 30.0f).withShadow(false));
    }

    private static void assertRoundTrips(Light original) {
        Tag encoded = LightCodecs.CODEC.encodeStart(NbtOps.INSTANCE, original)
                .getOrThrow(error -> new AssertionError("encode failed: " + error));
        DataResult<Light> decoded = LightCodecs.CODEC.parse(NbtOps.INSTANCE, encoded);
        Light result = decoded.getOrThrow(error -> new AssertionError("decode failed: " + error));

        assertEquals(original.type, result.type);
        assertEquals(original.x, result.x);
        assertEquals(original.y, result.y);
        assertEquals(original.z, result.z);
        assertEquals(original.directionX, result.directionX);
        assertEquals(original.directionY, result.directionY);
        assertEquals(original.directionZ, result.directionZ);
        assertEquals(original.r, result.r);
        assertEquals(original.g, result.g);
        assertEquals(original.b, result.b);
        assertEquals(original.intensity, result.intensity);
        assertEquals(original.range, result.range);
        assertEquals(original.cosInner, result.cosInner);
        assertEquals(original.cosOuter, result.cosOuter);
        assertEquals(original.castsShadow, result.castsShadow);
    }
}

package fr.lacaleche.glue.lumos;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deserialization rebuilds a light without the factories' argument checks, so a hostile or corrupt
 * payload can carry values the factories would have rejected. These cover what the server relies on
 * {@link Light#isWellFormed()} to catch before such a light is stored or broadcast.
 */
class LightValidationTest {

    @Test
    void factoryBuiltLightsAreWellFormed() {
        assertTrue(Light.point(0, 64, 0, 1f, 0.5f, 0.2f, 2f, 12f).isWellFormed());
        assertTrue(Light.spot(0, 64, 0, 0f, -1f, 0f,
                1f, 0.5f, 0.2f, 2f, 12f, 10f, 25f).isWellFormed());
    }

    @Test
    void decodedNonFinitePositionIsRejected() {
        assertFalse(decodedWith("x", Double.NaN).isWellFormed());
    }

    @Test
    void decodedOutOfBoundsRangeIsRejected() {
        assertFalse(decodedWith("range", 1.0e9).isWellFormed());
        assertFalse(decodedWith("range", 0.0).isWellFormed());
    }

    @Test
    void decodedOutOfBoundsColorAndIntensityAreRejected() {
        assertFalse(decodedWith("r", 40.0).isWellFormed());
        assertFalse(decodedWith("intensity", 1.0e6).isWellFormed());
    }

    @Test
    void decodedUnnormalizedSpotDirectionIsRejected() {
        assertFalse(reencode(spot(), "dirY", -7.0).isWellFormed());
    }

    @Test
    void decodedOverwideConeIsRejected() {
        // The factories cap the outer half-angle below 89 degrees; this is a 120-degree cone.
        assertFalse(reencode(spot(), "cosOuter", -0.5).isWellFormed());
    }

    @Test
    void decodedInvertedConeIsRejected() {
        // cosInner below cosOuter means inner angle wider than outer.
        assertFalse(reencode(spot(), "cosInner", 0.5).isWellFormed());
    }

    private static Light spot() {
        return Light.spot(0, 64, 0, 0f, -1f, 0f, 1f, 0.5f, 0.2f, 2f, 12f, 10f, 25f);
    }

    /** A point light re-encoded with one field overwritten, then decoded back through the codec. */
    private static Light decodedWith(String field, double value) {
        return reencode(Light.point(0, 64, 0, 1f, 0.5f, 0.2f, 2f, 12f), field, value);
    }

    private static Light reencode(Light light, String field, double value) {
        Tag encoded = LightCodecs.CODEC.encodeStart(NbtOps.INSTANCE, light)
                .getOrThrow(error -> new AssertionError("encode failed: " + error));
        CompoundTag tag = (CompoundTag) encoded;
        if (tag.get(field) instanceof net.minecraft.nbt.DoubleTag) {
            tag.putDouble(field, value);
        } else {
            tag.putFloat(field, (float) value);
        }
        DataResult<Light> decoded = LightCodecs.CODEC.parse(NbtOps.INSTANCE, tag);
        return decoded.getOrThrow(error -> new AssertionError("decode failed: " + error));
    }
}

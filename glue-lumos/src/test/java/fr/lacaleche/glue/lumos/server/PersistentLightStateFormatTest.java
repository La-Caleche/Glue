package fr.lacaleche.glue.lumos.server;

import com.mojang.serialization.Codec;
import fr.lacaleche.glue.lumos.Light;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The save format's forward-compatibility hinges on the version field: a parse failure silently
 * replaces the dimension's lights with an empty state, so the codec must treat an absent field as
 * version 1 (the pre-version format &mdash; and, since {@code optionalFieldOf} omits the default,
 * also the current one) and still accept an explicitly stamped version.
 */
class PersistentLightStateFormatTest {

    @Test
    void versionlessSaveDecodes() {
        CompoundTag tag = encode(stateWithOneLight());
        tag.remove("version");
        PersistentLightState decoded = decode(tag);
        assertEquals(1, decoded.entries().size());
        assertEquals(1L, decoded.entries().getFirst().id());
    }

    @Test
    void explicitVersionDecodes() {
        CompoundTag tag = encode(stateWithOneLight());
        tag.putInt("version", 1);
        assertEquals(1, decode(tag).entries().size());
    }

    @Test
    void lightsSurviveTheRoundTrip() {
        PersistentLightState state = stateWithOneLight();
        Light original = state.entries().getFirst().light();
        Light decoded = decode(encode(state)).entries().getFirst().light();
        assertEquals(original.x, decoded.x);
        assertEquals(original.range, decoded.range);
        assertEquals(original.castsShadow, decoded.castsShadow);
    }

    private static PersistentLightState stateWithOneLight() {
        PersistentLightState state = new PersistentLightState();
        state.add(Light.point(10, 64, -3, 1f, 0.5f, 0.2f, 2f, 12f));
        return state;
    }

    private static Codec<PersistentLightState> codec() {
        return PersistentLightState.TYPE.codec().apply(null);
    }

    private static CompoundTag encode(PersistentLightState state) {
        Tag tag = codec().encodeStart(NbtOps.INSTANCE, state)
                .getOrThrow(error -> new AssertionError("encode failed: " + error));
        return (CompoundTag) tag;
    }

    private static PersistentLightState decode(CompoundTag tag) {
        return codec().parse(NbtOps.INSTANCE, tag)
                .getOrThrow(error -> new AssertionError("decode failed: " + error));
    }
}

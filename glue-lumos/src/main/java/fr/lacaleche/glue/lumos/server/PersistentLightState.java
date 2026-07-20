package fr.lacaleche.glue.lumos.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.net.PlacedLight;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The persistent lights of one dimension, held in the world save so they travel with it, under
 * {@code <dimension>/data/glue_lumos_lights.dat}. Server-authoritative: the client only ever receives a
 * synced copy and never writes this. Ids are assigned by a monotonic counter that is itself persisted,
 * so a removed light's id is never reused within a save.
 */
public final class PersistentLightState extends SavedData {

    // DataFixTypes.LEVEL is an arbitrary but required label: vanilla has no fix type for mod data,
    // and the level.dat fixers find nothing to rewrite under these keys. Format migration is
    // handled by the codec's own version field, never by DFU.
    static final SavedDataType<PersistentLightState> TYPE = new SavedDataType<>(
            "glue_lumos_lights", PersistentLightState::new, codec(), DataFixTypes.LEVEL);

    /**
     * Bumped when the stored shape changes incompatibly. A codec parse failure silently wipes the
     * dimension's lights (SavedData replaces an unreadable file with a fresh empty state on the next
     * save), so a future format change must branch on this field rather than alter fields in place.
     * {@code optionalFieldOf} omits the field while it equals the default, so on disk version 1 is
     * represented by absence and the field only appears once this is bumped.
     */
    private static final int FORMAT_VERSION = 1;

    private final Map<Long, Light> lights = new LinkedHashMap<>();
    private long nextId = 1L;

    PersistentLightState() {
    }

    private PersistentLightState(int version, long nextId, List<PlacedLight> entries) {
        this.nextId = nextId;
        for (PlacedLight entry : entries) {
            lights.put(entry.id(), entry.light());
        }
    }

    long add(Light light) {
        long id = nextId++;
        lights.put(id, light);
        setDirty();
        return id;
    }

    @Nullable
    Light get(long id) {
        return lights.get(id);
    }

    int size() {
        return lights.size();
    }

    boolean update(long id, Light light) {
        if (!lights.containsKey(id)) return false;
        lights.put(id, light);
        setDirty();
        return true;
    }

    boolean remove(long id) {
        boolean removed = lights.remove(id) != null;
        if (removed) setDirty();
        return removed;
    }

    List<PlacedLight> entries() {
        List<PlacedLight> result = new ArrayList<>(lights.size());
        lights.forEach((id, light) -> result.add(new PlacedLight(id, light)));
        return result;
    }

    private static Codec<PersistentLightState> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("version", 1).forGetter(state -> FORMAT_VERSION),
                Codec.LONG.fieldOf("nextId").forGetter(state -> state.nextId),
                PlacedLight.CODEC.listOf().fieldOf("lights").forGetter(PersistentLightState::entries)
        ).apply(instance, PersistentLightState::new));
    }
}

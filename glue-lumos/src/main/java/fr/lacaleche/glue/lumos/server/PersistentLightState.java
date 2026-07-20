package fr.lacaleche.glue.lumos.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.net.IdLight;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

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

    static final SavedDataType<PersistentLightState> TYPE = new SavedDataType<>(
            "glue_lumos_lights", PersistentLightState::new, codec(), DataFixTypes.LEVEL);

    private final Map<Long, Light> lights = new LinkedHashMap<>();
    private long nextId = 1L;

    PersistentLightState() {
    }

    private PersistentLightState(long nextId, List<IdLight> entries) {
        this.nextId = nextId;
        for (IdLight entry : entries) {
            lights.put(entry.id(), entry.light());
        }
    }

    long add(Light light) {
        long id = nextId++;
        lights.put(id, light);
        setDirty();
        return id;
    }

    boolean remove(long id) {
        boolean removed = lights.remove(id) != null;
        if (removed) setDirty();
        return removed;
    }

    List<IdLight> entries() {
        List<IdLight> result = new ArrayList<>(lights.size());
        lights.forEach((id, light) -> result.add(new IdLight(id, light)));
        return result;
    }

    private static Codec<PersistentLightState> codec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("nextId").forGetter(state -> state.nextId),
                IdLight.CODEC.listOf().fieldOf("lights").forGetter(PersistentLightState::entries)
        ).apply(instance, PersistentLightState::new));
    }
}

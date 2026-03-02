package fr.lacaleche.glue.registries;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public abstract class GlueRegistry {

    private final String modId;
    private final Function<String, ResourceLocation> idFunction;

    public GlueRegistry(String modId) {
        this(modId, path -> ResourceLocation.tryBuild(modId, path));
    }

    public GlueRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        this.modId = modId;
        this.idFunction = idFunction;
    }

    public String getModId() {
        return modId;
    }

    public ResourceLocation id(String path) {
        return idFunction.apply(path);
    }

}

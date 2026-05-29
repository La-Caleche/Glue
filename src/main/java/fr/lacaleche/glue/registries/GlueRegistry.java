package fr.lacaleche.glue.registries;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public class GlueRegistry {

    private final String modId;
    private final Function<String, ResourceLocation> idFunction;

    public GlueRegistry(String modId) {
        // Use fromNamespaceAndPath (throws on invalid path) rather than tryBuild
        // (silently returns null) so that registration errors fail fast with
        // a useful message at the call site, not deep inside the registry code.
        this(modId, path -> ResourceLocation.fromNamespaceAndPath(modId, path));
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

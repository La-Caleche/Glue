package fr.lacaleche.glue.registries;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class PostShaderRegistry extends GlueRegistry {

    private final List<PostShaderHandle> handles = new ArrayList<>();

    public PostShaderRegistry(String modId) {
        super(modId);
    }

    public PostShaderRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public PostShaderHandle register(String name) {
        return register(name, LevelTargetBundle.MAIN_TARGETS);
    }

    public PostShaderHandle register(String name, Set<ResourceLocation> externalTargets) {
        ResourceLocation id = this.id(name);
        PostShaderHandle handle = new PostShaderHandle(id, externalTargets);
        GlueClientRegistries.POST_CHAINS.register(id, handle);
        this.handles.add(handle);
        return handle;
    }

    public List<PostShaderHandle> getHandles() {
        return List.copyOf(this.handles);
    }
}

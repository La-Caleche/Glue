package fr.lacaleche.glue.registries;

import fr.lacaleche.glue.client.shader.PostShaderHandle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A GlueRegistry for declaring post-processing shader handles.
 * <p>
 * Post-processing shaders in MC 1.21.8 are loaded from JSON resources at
 * {@code post_effect/<namespace>/<path>.json}. This registry creates
 * {@link PostShaderHandle} instances that lazily load post chains via the ShaderManager.
 *
 * <pre>{@code
 * public static final PostShaderRegistry POST = new PostShaderRegistry("mymod", MyMod::id);
 *
 * // Registers a post effect from post_effect/mymod/grayscale.json
 * public static final PostShaderHandle GRAYSCALE = POST.register("grayscale");
 * }</pre>
 */
@Environment(EnvType.CLIENT)
public class PostShaderRegistry extends GlueRegistry {

    private final List<PostShaderHandle> handles = new ArrayList<>();

    public PostShaderRegistry(String modId) {
        super(modId);
    }

    public PostShaderRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    /**
     * Registers a post-processing shader handle with the default external targets (MAIN only).
     *
     * @param name The name of the post effect (maps to {@code post_effect/<modid>/<name>.json})
     * @return A handle for applying the post effect
     */
    public PostShaderHandle register(String name) {
        return register(name, LevelTargetBundle.MAIN_TARGETS);
    }

    /**
     * Registers a post-processing shader handle with custom external targets.
     *
     * @param name            The name of the post effect
     * @param externalTargets The set of external render targets this chain expects
     * @return A handle for applying the post effect
     */
    public PostShaderHandle register(String name, Set<ResourceLocation> externalTargets) {
        ResourceLocation id = this.id(name);
        PostShaderHandle handle = new PostShaderHandle(id, externalTargets);
        this.handles.add(handle);
        return handle;
    }

    /**
     * @return All post shader handles registered through this registry
     */
    public List<PostShaderHandle> getHandles() {
        return List.copyOf(this.handles);
    }
}

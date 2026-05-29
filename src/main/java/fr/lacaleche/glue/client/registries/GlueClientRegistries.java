package fr.lacaleche.glue.client.registries;

import com.mojang.serialization.Lifecycle;
import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public class GlueClientRegistries {

    /**
     * Client-only {@link Registry} for {@link GlueOutlineRenderer}s — populated by
     * {@code OutlineRendererRegistry.register(...)} (Java path) and by the
     * {@code OutlineDefinitionLoader} resource reload listener (JSON path).
     *
     * <p>Same rationale as {@link #PIPELINES}: must be a free-standing
     * {@link MappedRegistry} to avoid Fabric's post-init registry freeze.</p>
     */
    public static final Registry<GlueOutlineRenderer> OUTLINE_RENDERERS = newClientRegistry("outline_renderer");

    /**
     * Client-only {@link Registry} for {@link GluePipeline}s — populated by the
     * {@code PipelineDefinitionLoader} resource reload listener (JSON path) and
     * by {@code CoreShaderRegistry.registerPipeline(...)} (Java path).
     *
     * <p>This is a free-standing {@link MappedRegistry} intentionally <em>not</em>
     * passed through {@code FabricRegistryBuilder.buildAndRegister()}. Doing so
     * would insert it into {@code BuiltInRegistries.REGISTRY}, which Fabric's
     * {@code MinecraftClientMixin.afterModInit} freezes after mod init —
     * blocking the resource reload listener from registering JSON pipelines
     * later. Vanilla itself follows this same pattern for client-side, non-tag,
     * non-synced registries (see {@code WorldOpenFlows#createLevelStems}).</p>
     */
    public static final Registry<GluePipeline> PIPELINES = newClientRegistry("pipeline");

    /**
     * Client-only {@link Registry} for {@link PostShaderHandle}s — populated by
     * {@code PostShaderRegistry.register(...)}. Same rationale as
     * {@link #PIPELINES}.
     */
    public static final Registry<PostShaderHandle> POST_CHAINS = newClientRegistry("post_chain");


    private static <T> Registry<T> newClientRegistry(String name) {
        return new MappedRegistry<>(ResourceKey.createRegistryKey(Glue.id(name)), Lifecycle.stable());
    }

    public static void bootstrap() {

    }
}

package fr.lacaleche.glue.testmod.registries;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.registries.CoreShaderRegistry;
import fr.lacaleche.glue.registries.PostShaderRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Registers all test shaders using the Glue shader registry system.
 * <p>
 * Demonstrates:
 * <ul>
 *   <li>A core shader pipeline for world-space gradient quad rendering</li>
 *   <li>A core shader pipeline for GUI/HUD overlay rendering</li>
 *   <li>Post-processing shaders (grayscale, blur, shattered screen)</li>
 * </ul>
 */
public class TestShaders {

    public static final CoreShaderRegistry CORE = new CoreShaderRegistry(TestmodClient.MOD_ID, TestmodClient::id);
    public static final PostShaderRegistry POST = new PostShaderRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    /**
     * World-space gradient quad pipeline.
     * Uses POSITION_COLOR format with translucent blend, no depth write, no cull.
     */
    public static final RenderPipeline GRADIENT_WORLD = CORE.register("gradient_world",
            RenderPipelines.MATRICES_PROJECTION_SNIPPET,
            builder -> builder
                    .withVertexShader(TestmodClient.id("core/gradient_world"))
                    .withFragmentShader(TestmodClient.id("core/gradient_world"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthWrite(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS),
            "BLOCK_TRANSLUCENT"
    );

    /**
     * GUI overlay shader pipeline.
     * Uses POSITION_COLOR format with translucent blend for HUD elements.
     * Compatible with {@code guiGraphics.fill()} which emits position + color.
     */
    public static final RenderPipeline GRADIENT_GUI = CORE.register("gradient_gui",
            RenderPipelines.MATRICES_PROJECTION_SNIPPET,
            builder -> builder
                    .withVertexShader(TestmodClient.id("core/gradient_gui"))
                    .withFragmentShader(TestmodClient.id("core/gradient_gui"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
    );

    /**
     * Post-processing: Grayscale effect.
     * Loaded from {@code post_effect/glue-test/grayscale.json}.
     */
    public static final PostShaderHandle GRAYSCALE = POST.register("grayscale");

    /**
     * Post-processing: Blur effect.
     * Loaded from {@code post_effect/glue-test/blur.json}.
     */
    public static final PostShaderHandle BLUR = POST.register("blur");

    /**
     * Post-processing: Shattered screen effect.
     * Voronoi-based crack pattern with chromatic aberration.
     * Loaded from {@code post_effect/glue-test/shattered_screen.json}.
     */
    public static final PostShaderHandle SHATTERED_SCREEN = POST.register("shattered_screen");

    public static void registerShaders() {
        TestmodClient.LOGGER.info("Registering test shaders ({} core, {} post)",
                CORE.getPipelines().size(), POST.getHandles().size());
    }
}

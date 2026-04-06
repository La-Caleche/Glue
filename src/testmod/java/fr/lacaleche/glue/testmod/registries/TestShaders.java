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

public class TestShaders {

    public static final CoreShaderRegistry CORE = new CoreShaderRegistry(TestmodClient.MOD_ID, TestmodClient::id);
    public static final PostShaderRegistry POST = new PostShaderRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static final RenderPipeline GRADIENT_GUI = CORE.register("gradient_gui",
            RenderPipelines.MATRICES_PROJECTION_SNIPPET,
            builder -> builder
                    .withVertexShader(TestmodClient.id("core/gradient_gui"))
                    .withFragmentShader(TestmodClient.id("core/gradient_gui"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
    );

    public static final PostShaderHandle GRAYSCALE = POST.register("grayscale");
    public static final PostShaderHandle BLUR = POST.register("blur");
    public static final PostShaderHandle SHATTERED_SCREEN = POST.register("shattered_screen");

    public static void registerShaders() {
        TestmodClient.LOGGER.info("Registering test shaders ({} core, {} post)",
                CORE.getPipelines().size(), POST.getHandles().size());
    }
}

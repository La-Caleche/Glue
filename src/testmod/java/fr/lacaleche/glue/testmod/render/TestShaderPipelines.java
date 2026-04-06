package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.testmod.TestmodClient;

public final class TestShaderPipelines {

    private static final String[] SHADER_NAMES = {
            "hologram", "enchanted_glow", "frozen", "xray", "inferno"
    };

    private static GluePipeline[] pipelines;

    private TestShaderPipelines() {}

    public static GluePipeline[] get() {
        if (pipelines == null) {
            pipelines = new GluePipeline[SHADER_NAMES.length];
            for (int i = 0; i < SHADER_NAMES.length; i++) {
                String name = SHADER_NAMES[i];
                pipelines[i] = GluePipeline.entity(
                        TestmodClient.id(name),
                        TestmodClient.id("core/entity"),
                        TestmodClient.id("core/" + name)
                );
            }
        }
        return pipelines;
    }

    public static int count() {
        return SHADER_NAMES.length;
    }

    public static String nameOf(int index) {
        return SHADER_NAMES[index % SHADER_NAMES.length];
    }
}

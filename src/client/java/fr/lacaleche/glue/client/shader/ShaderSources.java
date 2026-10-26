package fr.lacaleche.glue.client.shader;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Map;
import java.util.Objects;

/**
 * The GLSL text of a runtime pipeline: its two stages and the includes they may import that no resource
 * pack holds. An {@code #moj_import <ns:path>} or {@code "path"} names a key of {@code includes} first, then
 * a loaded {@code <ns>:shaders/include/<path>}.
 */
@Environment(EnvType.CLIENT)
public record ShaderSources(String vertex, String fragment, Map<String, String> includes) {

    public ShaderSources {
        Objects.requireNonNull(vertex, "vertex");
        Objects.requireNonNull(fragment, "fragment");
        includes = Map.copyOf(includes);
    }

    public ShaderSources(String vertex, String fragment) {
        this(vertex, fragment, Map.of());
    }
}

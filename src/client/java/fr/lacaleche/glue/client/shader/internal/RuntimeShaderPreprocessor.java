package fr.lacaleche.glue.client.shader.internal;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Expands the {@code #moj_import} lines of a runtime shader: a key of the in-memory include map first,
 * then an absolute {@code <ns:path>} read from {@code <ns>:shaders/include/<path>}. What cannot be
 * resolved becomes an {@code #error} line, so the driver's log names it.
 */
@Environment(EnvType.CLIENT)
public final class RuntimeShaderPreprocessor extends GlslPreprocessor {

    private final Map<String, String> includes;
    private final Function<ResourceLocation, String> resources;
    private final Set<String> imported = new HashSet<>();

    private RuntimeShaderPreprocessor(Map<String, String> includes, Function<ResourceLocation, String> resources) {
        this.includes = includes;
        this.resources = resources;
    }

    /**
     * @param resources reads a loaded resource's text, null when it does not exist
     */
    public static String expand(String source, Map<String, String> includes,
                                Function<ResourceLocation, String> resources) {
        return String.join("", new RuntimeShaderPreprocessor(includes, resources).process(source));
    }

    @Override
    public @Nullable String applyImport(boolean relative, String path) {
        String included = includes.get(path);
        if (included != null)
            return imported.add(path) ? included : null;
        if (relative)
            return "#error Unknown include " + path + "\n";

        ResourceLocation location;
        try {
            location = ResourceLocation.parse(path).withPrefix("shaders/include/");
        } catch (ResourceLocationException e) {
            return "#error Malformed include " + path + ": " + e.getMessage() + "\n";
        }
        if (!imported.add(location.toString()))
            return null;
        String text = resources.apply(location);
        return text != null ? text : "#error Unknown include " + path + "\n";
    }
}

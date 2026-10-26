package fr.lacaleche.glue.client.shader.internal;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeShaderPreprocessorTest {

    private static final Function<ResourceLocation, String> NO_RESOURCES = location -> null;

    @Test
    void anIncludeFromTheMapIsExpandedOnce() {
        String source = "#version 150\n#moj_import <common>\n#moj_import <common>\nvoid main() {}\n";

        String expanded = RuntimeShaderPreprocessor.expand(source, Map.of("common", "float glueCommon;\n"), NO_RESOURCES);

        assertEquals(1, occurrences(expanded, "float glueCommon;"));
        assertTrue(expanded.contains("void main() {}"));
        assertFalse(expanded.replace("/*#moj_import", "").contains("#moj_import"), expanded);
    }

    @Test
    void anAbsoluteIncludeMissingFromTheMapIsReadFromShaderIncludes() {
        List<ResourceLocation> read = new ArrayList<>();
        Function<ResourceLocation, String> resources = location -> {
            read.add(location);
            return "float fromResource;\n";
        };

        String expanded = RuntimeShaderPreprocessor.expand("#version 150\n#moj_import <ignis:noise.glsl>\n",
                Map.of(), resources);

        assertTrue(expanded.contains("float fromResource;"));
        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("ignis", "shaders/include/noise.glsl")), read);
    }

    @Test
    void anUnknownIncludeBecomesAnErrorTheDriverReports() {
        String relative = RuntimeShaderPreprocessor.expand("#version 150\n#moj_import \"missing.glsl\"\n", Map.of(), NO_RESOURCES);
        String absolute = RuntimeShaderPreprocessor.expand("#version 150\n#moj_import <glue:missing.glsl>\n", Map.of(), NO_RESOURCES);

        assertTrue(relative.contains("#error Unknown include missing.glsl"), relative);
        assertTrue(absolute.contains("#error Unknown include glue:missing.glsl"), absolute);
    }

    @Test
    void aMalformedIncludeBecomesAnError() {
        String expanded = RuntimeShaderPreprocessor.expand("#version 150\n#moj_import <Bad Path!>\n", Map.of(), NO_RESOURCES);

        assertTrue(expanded.contains("#error Malformed include Bad Path!"), expanded);
    }

    @Test
    void anIncludeMayImportAnotherFromTheMap() {
        Map<String, String> includes = Map.of(
                "outer", "#moj_import <inner>\nfloat outer;\n",
                "inner", "float inner;\n");

        String expanded = RuntimeShaderPreprocessor.expand("#version 150\n#moj_import <outer>\n#moj_import <inner>\n",
                includes, NO_RESOURCES);

        assertEquals(1, occurrences(expanded, "float inner;"));
        assertTrue(expanded.indexOf("float inner;") < expanded.indexOf("float outer;"));
    }

    private static int occurrences(String text, String part) {
        int count = 0;
        for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + part.length()))
            count++;
        return count;
    }
}

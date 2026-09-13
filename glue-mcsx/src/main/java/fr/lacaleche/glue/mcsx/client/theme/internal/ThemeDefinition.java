package fr.lacaleche.glue.mcsx.client.theme.internal;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public record ThemeDefinition(ResourceLocation parent, Map<String, Declaration> values) {

    public sealed interface Declaration permits Literal, Reference, Alpha {
    }

    public record Literal(Object value) implements Declaration {
    }

    public record Reference(String token) implements Declaration {
    }

    public record Alpha(Declaration color, int alpha) implements Declaration {
    }
}

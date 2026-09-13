package fr.lacaleche.glue.mcsx.client.style;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public final class StylesheetParseException extends IllegalArgumentException {

    private final ResourceLocation resource;
    private final int line;
    private final int column;

    StylesheetParseException(
            ResourceLocation resource,
            int line,
            int column,
            String message
    ) {
        super(resource + ":" + line + ":" + column + ": " + message);
        this.resource = resource;
        this.line = line;
        this.column = column;
    }

    public ResourceLocation resource() {
        return this.resource;
    }

    public int line() {
        return this.line;
    }

    public int column() {
        return this.column;
    }
}

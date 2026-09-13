package fr.lacaleche.glue.mcsx.client.theme;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record DockMetrics(
        int gutter,
        int splitterSize,
        int headerHeight,
        int cornerRadius,
        int borderWidth,
        int tabTextSize,
        int controlTextSize,
        int tabPadding,
        int iconGap,
        int tabCloseWidth,
        int controlWidth
) {

    public DockMetrics {
        requireNonNegative("gutter", gutter);
        requireNonNegative("splitterSize", splitterSize);
        requireNonNegative("headerHeight", headerHeight);
        requireNonNegative("cornerRadius", cornerRadius);
        requireNonNegative("borderWidth", borderWidth);
        requireNonNegative("tabTextSize", tabTextSize);
        requireNonNegative("controlTextSize", controlTextSize);
        requireNonNegative("tabPadding", tabPadding);
        requireNonNegative("iconGap", iconGap);
        requireNonNegative("tabCloseWidth", tabCloseWidth);
        requireNonNegative("controlWidth", controlWidth);
    }

    private static void requireNonNegative(String field, int value) {
        if (value < 0) throw new IllegalArgumentException(field + " cannot be negative: " + value);
    }
}

package fr.lacaleche.glue.web.host;

import net.minecraft.client.gui.navigation.ScreenRectangle;

/** The GUI rectangle of a HUD or overlay; a null anchor covers the whole GUI. */
record LayerPlacement(WebAnchor anchor, int width, int height, int offsetX, int offsetY) {

    static final LayerPlacement FULL = new LayerPlacement(null, 0, 0, 0, 0);

    LayerPlacement {
        if (anchor != null && (width < 1 || height < 1)) throw new IllegalArgumentException("Layer sizes must be positive");
    }

    LayerPlacement withOffset(int x, int y) {
        if (this.anchor == null) throw new IllegalStateException("Set an anchor before an offset");
        return new LayerPlacement(this.anchor, this.width, this.height, x, y);
    }

    ScreenRectangle bounds(int guiWidth, int guiHeight) {
        int availableWidth = Math.max(1, guiWidth);
        int availableHeight = Math.max(1, guiHeight);
        if (this.anchor == null) return new ScreenRectangle(0, 0, availableWidth, availableHeight);

        int layerWidth = Math.min(this.width, availableWidth);
        int layerHeight = Math.min(this.height, availableHeight);
        return new ScreenRectangle(this.anchor.x(availableWidth, layerWidth, this.offsetX),
                this.anchor.y(availableHeight, layerHeight, this.offsetY), layerWidth, layerHeight);
    }
}

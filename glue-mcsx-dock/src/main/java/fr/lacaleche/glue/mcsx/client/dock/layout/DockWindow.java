package fr.lacaleche.glue.mcsx.client.dock.layout;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Objects;

/** A detached dock subtree positioned over the stage. */
@Environment(EnvType.CLIENT)
public record DockWindow(DockNode node, int x, int y, int width, int height, int stackingOrder) {

    public DockWindow {
        Objects.requireNonNull(node, "node");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Floating window dimensions must be positive");
        }
    }

    public DockWindow withNode(DockNode replacement) {
        return new DockWindow(replacement, this.x, this.y, this.width, this.height, this.stackingOrder);
    }

    public DockWindow withFrame(int newX, int newY, int newWidth, int newHeight) {
        return new DockWindow(this.node, newX, newY, newWidth, newHeight, this.stackingOrder);
    }

    public DockWindow withStackingOrder(int order) {
        return new DockWindow(this.node, this.x, this.y, this.width, this.height, order);
    }
}

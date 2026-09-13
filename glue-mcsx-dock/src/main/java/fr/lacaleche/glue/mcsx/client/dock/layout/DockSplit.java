package fr.lacaleche.glue.mcsx.client.dock.layout;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;
import java.util.Objects;

/** A division of an area among two or more child nodes. */
@Environment(EnvType.CLIENT)
public record DockSplit(DockAxis axis, List<DockNode> children, List<Double> shares) implements DockNode {

    public DockSplit {
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(children, "children");
        Objects.requireNonNull(shares, "shares");
        children = List.copyOf(children);
        shares = List.copyOf(shares);
        if (children.size() < 2) throw new IllegalArgumentException("A dock split needs at least two children");
        if (children.size() != shares.size()) {
            throw new IllegalArgumentException(children.size() + " children but " + shares.size() + " shares");
        }

        double total = 0.0;
        for (Double share : shares) {
            if (!Double.isFinite(share) || share < 0.0) {
                throw new IllegalArgumentException("Split shares must be finite and non-negative");
            }
            total += share;
        }
        if (!Double.isFinite(total) || total <= 0.0) {
            throw new IllegalArgumentException("Split shares must have a positive finite sum");
        }
    }
}

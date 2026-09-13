package fr.lacaleche.glue.mcsx.client.dock.layout;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;
import java.util.Objects;

/** A docked tree and its detached windows. The tree is null when the docked stage is empty. */
@Environment(EnvType.CLIENT)
public record DockLayout(DockNode tree, List<DockWindow> windows) {

    public DockLayout {
        Objects.requireNonNull(windows, "windows");
        windows = List.copyOf(windows);
    }

    public DockLayout withTree(DockNode replacement) {
        return new DockLayout(replacement, this.windows);
    }

    public DockLayout withWindows(List<DockWindow> replacements) {
        return new DockLayout(this.tree, replacements);
    }
}

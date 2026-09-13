package fr.lacaleche.glue.mcsx.client.dock.layout;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A tab strip and the pane currently presented by it. */
@Environment(EnvType.CLIENT)
public record DockTabs(List<String> tabs, String active) implements DockNode {

    public DockTabs {
        Objects.requireNonNull(tabs, "tabs");
        Objects.requireNonNull(active, "active");
        tabs = List.copyOf(tabs);
        if (tabs.isEmpty()) throw new IllegalArgumentException("Dock tabs cannot be empty");

        Set<String> unique = new HashSet<>();
        for (String tab : tabs) {
            if (tab.isBlank()) throw new IllegalArgumentException("Pane id cannot be blank");
            if (!unique.add(tab)) throw new IllegalArgumentException("Duplicate pane id: " + tab);
        }
        if (!unique.contains(active)) {
            throw new IllegalArgumentException("Active pane is not present in the tab strip: " + active);
        }
    }
}

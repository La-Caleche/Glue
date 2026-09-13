package fr.lacaleche.glue.mcsx.client.dock.layout;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public sealed interface DockNode permits DockTabs, DockSplit {
}

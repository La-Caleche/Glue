package fr.lacaleche.glue.testmod.mcsx;

import fr.lacaleche.glue.mcsx.core.dock.DockIds;
import fr.lacaleche.glue.mcsx.core.dock.DockLayout;
import fr.lacaleche.glue.mcsx.core.dock.DockLayoutCodec;
import fr.lacaleche.glue.mcsx.core.dock.DockOps;
import fr.lacaleche.glue.mcsx.core.dock.DockSplit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Guards the demo's shipped default dock layout: it must parse and open every pane it names. */
class DockDefaultLayoutTest {

    @Test
    void shippedDefaultLayoutAssetParses() throws IOException {
        Path asset = Path.of("src/main/resources/assets/mcsx/dock/default.json");
        DockLayout layout = DockLayoutCodec.read(Files.readString(asset), new DockIds());
        assertEquals(Set.of("hierarchy", "viewport", "console", "profiler", "inspector"),
                DockOps.openSet(layout));
        assertInstanceOf(DockSplit.class, layout.tree());
    }

    @Test
    void debugDockLayoutAssetParses() throws IOException {
        Path asset = Path.of("src/main/resources/assets/mcsx/dock/glue_debug.json");
        DockLayout layout = DockLayoutCodec.read(Files.readString(asset), new DockIds());
        assertEquals(Set.of("lights", "properties", "viewport", "effects"),
                DockOps.openSet(layout));
        assertInstanceOf(DockSplit.class, layout.tree());
    }
}

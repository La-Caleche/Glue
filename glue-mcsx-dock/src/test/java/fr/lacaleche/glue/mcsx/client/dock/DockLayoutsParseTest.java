package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.dock.layout.DockAxis;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayoutException;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockLayoutsParseTest {

    @Test
    void parsesAResourceLayoutDocument() {
        DockLayout layout = DockLayouts.parse("""
                {
                  "version": 1,
                  "tree": {
                    "type": "split",
                    "axis": "horizontal",
                    "shares": [0.3, 0.7],
                    "children": [
                      {"type": "tabs", "tabs": ["explorer"], "active": "explorer"},
                      {"type": "tabs", "tabs": ["document", "console"], "active": "console"}
                    ]
                  },
                  "windows": [
                    {"node": {"type": "tabs", "tabs": ["profiler"], "active": "profiler"},
                     "x": 40, "y": 60, "width": 380, "height": 280, "stackingOrder": 1}
                  ]
                }
                """);

        DockSplit split = assertInstanceOf(DockSplit.class, layout.tree());
        assertEquals(DockAxis.HORIZONTAL, split.axis());
        assertEquals(0.3, split.shares().getFirst(), 1.0e-9);
        assertEquals("console", ((DockTabs) split.children().get(1)).active());
        assertEquals(List.of("profiler"), ((DockTabs) layout.windows().getFirst().node()).tabs());
        assertEquals(380, layout.windows().getFirst().width());
    }

    @Test
    void parseAppliesTheSameNormalizationAsLoading() {
        DockLayout layout = DockLayouts.parse("""
                {"tree":{"type":"tabs","tabs":["one","one"],"active":"missing"},"windows":[]}
                """);

        DockTabs tabs = assertInstanceOf(DockTabs.class, layout.tree());
        assertEquals(List.of("one"), tabs.tabs());
        assertEquals("one", tabs.active());
    }

    @Test
    void malformedDocumentsThrowDockLayoutExceptionWithTheReason() {
        assertThrows(DockLayoutException.class, () -> DockLayouts.parse(null));
        assertThrows(DockLayoutException.class, () -> DockLayouts.parse("not json"));

        DockLayoutException unknownType = assertThrows(DockLayoutException.class,
                () -> DockLayouts.parse("{\"tree\":{\"type\":\"mystery\"},\"windows\":[]}"));
        assertTrue(unknownType.getMessage().contains("mystery"), unknownType.getMessage());
    }
}

package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockLayoutCodec;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockOperations;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockAxis;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayoutException;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockLayoutCodecTest {

    @Test
    void versionOneRoundTripPreservesSemanticStructureAndWindows() {
        DockTabs viewport = DockLayouts.tabs("viewport");
        DockTabs console = new DockTabs(List.of("console", "assets"), "assets");
        DockSplit tree = new DockSplit(DockAxis.VERTICAL, List.of(viewport, console), List.of(0.72, 0.28));
        DockWindow window = new DockWindow(DockLayouts.tabs("profiler"), 40, 60, 380, 280, 3);
        DockLayout source = new DockLayout(tree, List.of(window));

        String json = DockLayoutCodec.write(source);
        DockLayout result = DockLayoutCodec.read(json);

        assertTrue(json.contains("\"version\":1"));
        DockSplit split = assertInstanceOf(DockSplit.class, result.tree());
        assertEquals(DockAxis.VERTICAL, split.axis());
        assertEquals(0.72, split.shares().getFirst(), 1.0e-9);
        assertEquals("assets", ((DockTabs) split.children().get(1)).active());
        assertEquals(40, result.windows().getFirst().x());
        assertEquals(280, result.windows().getFirst().height());
        assertEquals(1, result.windows().getFirst().stackingOrder());
    }

    @Test
    void runtimeIdentityIsNotSerialized() {
        String json = DockLayoutCodec.write(DockLayouts.layout(DockLayouts.tabs("pane")));
        assertFalse(json.contains("identity"));
        assertFalse(json.contains("\"id\""));
    }

    @Test
    void emptyLayoutAndEscapedPaneIdsRoundTrip() {
        DockLayout empty = DockLayoutCodec.read(DockLayoutCodec.write(DockLayouts.empty()));
        assertNull(empty.tree());
        assertTrue(empty.windows().isEmpty());

        String pane = "quoted \"pane\"\nline";
        DockLayout escaped = DockLayoutCodec.read(DockLayoutCodec.write(DockLayouts.layout(DockLayouts.tabs(pane))));
        assertEquals(pane, ((DockTabs) escaped.tree()).tabs().getFirst());
    }

    @Test
    void staleTabsChildrenAndSharesAreSanitizedDeterministically() {
        DockLayout layout = DockLayoutCodec.read("""
                {"version":1,"tree":{"type":"split","axis":"horizontal",
                  "shares":[0,0,1],"children":[
                    {"type":"tabs","tabs":["one","one"],"active":"missing"},
                    {"type":"tabs","tabs":["two"],"active":"two"},
                    {"type":"tabs","tabs":[],"active":null}]},
                 "windows":[{"node":{"type":"tabs","tabs":["one","three"],"active":"one"},
                   "x":0,"y":0,"width":100,"height":100,"stackingOrder":8}]}
                """);

        DockSplit split = assertInstanceOf(DockSplit.class, layout.tree());
        assertEquals(List.of(0.5, 0.5), split.shares());
        assertEquals("one", ((DockTabs) split.children().getFirst()).active());
        assertEquals(List.of("three"), ((DockTabs) layout.windows().getFirst().node()).tabs());
        assertEquals(Set.of("one", "two", "three"), DockOperations.openSet(layout));
        assertEquals(1, layout.windows().getFirst().stackingOrder());
    }

    @Test
    void malformedShareArraysFallBackToEvenAndDroppedSharesRenormalize() {
        DockSplit mismatched = (DockSplit) DockLayoutCodec.read("""
                {"tree":{"type":"split","axis":"horizontal","shares":[0.9],"children":[
                  {"type":"tabs","tabs":["one"],"active":"one"},
                  {"type":"tabs","tabs":["two"],"active":"two"}]},"windows":[]}
                """).tree();
        assertEquals(List.of(0.5, 0.5), mismatched.shares());

        DockSplit dropped = (DockSplit) DockLayoutCodec.read("""
                {"tree":{"type":"split","axis":"horizontal","shares":[0.7,0.2,0.1],"children":[
                  {"type":"tabs","tabs":["one"],"active":"one"},
                  {"type":"tabs","tabs":["two"],"active":"two"},null]},"windows":[]}
                """).tree();
        assertEquals(0.7 / 0.9, dropped.shares().get(0), 1.0e-9);
        assertEquals(0.2 / 0.9, dropped.shares().get(1), 1.0e-9);
    }

    @Test
    void invalidWindowDimensionsUseDefaultsButCoordinatesRemainExact() {
        DockLayout layout = DockLayoutCodec.read("""
                {"tree":null,"windows":[{"node":{"type":"tabs","tabs":["one"],"active":"one"},
                  "x":-2147483648,"y":2147483647,"width":0,"height":-30,"stackingOrder":-5}]}
                """);
        DockWindow window = layout.windows().getFirst();
        assertEquals(Integer.MIN_VALUE, window.x());
        assertEquals(Integer.MAX_VALUE, window.y());
        assertEquals(360, window.width());
        assertEquals(260, window.height());
        assertEquals(1, window.stackingOrder());
    }

    @Test
    void nonFiniteOverflowAndShareTotalOverflowAreDomainExceptions() {
        for (String number : List.of("1e309", "-1e309")) {
            DockLayoutException exception = assertThrows(DockLayoutException.class,
                    () -> DockLayoutCodec.read(windowWithX(number)));
            assertTrue(exception.getMessage().toLowerCase().contains("finite"));
        }
        assertThrows(DockLayoutException.class, () -> DockLayoutCodec.read(windowWithX("2147483648")));
        assertThrows(DockLayoutException.class, () -> DockLayoutCodec.read("""
                {"tree":{"type":"split","axis":"horizontal","shares":[1e308,1e308],"children":[
                  {"type":"tabs","tabs":["one"],"active":"one"},
                  {"type":"tabs","tabs":["two"],"active":"two"}]},"windows":[]}
                """));
        assertThrows(DockLayoutException.class, () -> DockLayoutCodec.read("""
                {"tree":{"type":"split","axis":"horizontal","shares":[-1e309,1],"children":[
                  {"type":"tabs","tabs":["one"],"active":"one"},
                  {"type":"tabs","tabs":["two"],"active":"two"}]},"windows":[]}
                """));
        assertThrows(DockLayoutException.class,
                () -> DockLayoutCodec.read("{\"tree\":null,\"windows\":[],\"future\":1e309}"));
    }

    @Test
    void malformedDocumentsAlwaysUseDockLayoutException() {
        for (String json : List.of(
                "not json",
                "[1,2,3]",
                "{\"tree\":{\"type\":\"mystery\"}}",
                "{\"tree\":null,\"windows\":[]} trailing",
                "{\"tree\":null,\"windows\":[1]}",
                "{\"tree\":null,\"windows\":[]} +1"
        )) {
            assertThrows(DockLayoutException.class, () -> DockLayoutCodec.read(json), json);
        }
    }

    @Test
    void unknownFieldsAndFutureVersionsRemainForwardCompatible() {
        DockLayout layout = DockLayoutCodec.read("""
                {"version":9,"future":true,
                 "tree":{"type":"tabs","tabs":["one"],"active":"one","badge":3},"windows":[]}
                """);
        assertEquals(List.of("one"), ((DockTabs) layout.tree()).tabs());
    }

    private static String windowWithX(String x) {
        return "{\"tree\":null,\"windows\":[{\"node\":{\"type\":\"tabs\",\"tabs\":[\"one\"],"
                + "\"active\":\"one\"},\"x\":" + x + ",\"y\":0,\"width\":100,\"height\":100}]}";
    }
}

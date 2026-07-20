package fr.lacaleche.glue.mcsx.core.mcsx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McsxParserTest {

    @Test
    void parsesSingleElementWithAttributes() {
        McsxElement root = McsxParser.parse("<div bg=\"#151a2e\" pad=\"16\"/>");
        assertEquals("div", root.tag());
        assertEquals(2, root.attributes().size());
        assertEquals("#151a2e", root.attribute("bg"));
        assertEquals("16", root.attribute("pad"));
        assertTrue(root.children().isEmpty());
        assertEquals(1, root.line());
        assertEquals(1, root.column());
    }

    @Test
    void distinguishesLiteralBooleanAndBindingAttributes() {
        McsxElement root = McsxParser.parse("<button onClick={ping} primary label=\"Go\"/>");
        McsxAttribute onClick = root.attributes().get(0);
        assertEquals("onClick", onClick.name());
        assertEquals("ping", onClick.value());
        assertTrue(onClick.binding());
        // attribute() is literal-only: a binding is reported as absent
        assertNull(root.attribute("onClick"));

        McsxAttribute primary = root.attributes().get(1);
        assertEquals("", primary.value());
        assertFalse(primary.binding());
        assertEquals("", root.attribute("primary"));

        assertEquals("Go", root.attribute("label"));
    }

    @Test
    void parsesNestedElements() {
        McsxElement root = McsxParser.parse("<div><text>Hi</text><button/></div>");
        assertEquals(2, root.children().size());
        McsxElement text = (McsxElement) root.children().get(0);
        assertEquals("text", text.tag());
        McsxText hi = (McsxText) text.children().get(0);
        assertEquals("Hi", hi.parts().get(0).value());
        assertEquals("button", ((McsxElement) root.children().get(1)).tag());
    }

    /**
     * The binder renders every bare text run as an implicit {@code <text>}, so indentation between
     * elements must not survive parsing — otherwise each pretty-printed {@code .mcsx} would grow an
     * empty child per line.
     */
    @Test
    void indentationBetweenElementsProducesNoTextNodes() {
        McsxElement root = McsxParser.parse("<div>\n    <text>Hi</text>\n    <button/>\n</div>");
        assertEquals(2, root.children().size());
        for (McsxContent child : root.children()) {
            assertInstanceOf(McsxElement.class, child, "indentation must not become a text node");
        }
    }

    @Test
    void bareTextIsKeptAsTheOnlyChild() {
        McsxElement root = McsxParser.parse("<button>\n    Save\n</button>");
        assertEquals(1, root.children().size());
        McsxText label = assertInstanceOf(McsxText.class, root.children().get(0));
        assertEquals("Save", label.parts().get(0).value());
    }

    @Test
    void normalizesTextWhitespace() {
        McsxElement root = McsxParser.parse("<t>  hello   world  </t>");
        McsxText text = (McsxText) root.children().get(0);
        assertEquals(1, text.parts().size());
        assertEquals("hello world", text.parts().get(0).value());
    }

    @Test
    void keepsInteriorSpacingAroundBindings() {
        McsxElement root = McsxParser.parse("<t>Count: {{n}}</t>");
        McsxText text = (McsxText) root.children().get(0);
        assertEquals(2, text.parts().size());
        assertEquals("Count: ", text.parts().get(0).value());
        assertFalse(text.parts().get(0).binding());
        assertEquals("n", text.parts().get(1).value());
        assertTrue(text.parts().get(1).binding());
    }

    @Test
    void dropsSurroundingWhitespaceButKeepsBindingOnlyRun() {
        McsxElement root = McsxParser.parse("<t>   {{value}}   </t>");
        McsxText text = (McsxText) root.children().get(0);
        assertEquals(1, text.parts().size());
        assertEquals("value", text.parts().get(0).value());
        assertTrue(text.parts().get(0).binding());
    }

    @Test
    void dropsWhitespaceOnlyTextBetweenElements() {
        McsxElement root = McsxParser.parse("<div>\n    <a/>\n    <b/>\n</div>");
        assertEquals(2, root.children().size());
        assertTrue(root.children().get(0) instanceof McsxElement);
        assertTrue(root.children().get(1) instanceof McsxElement);
    }

    @Test
    void parsesDottedInterpolationPath() {
        McsxElement root = McsxParser.parse("<t>{{task.display}}</t>");
        McsxText text = (McsxText) root.children().get(0);
        assertEquals("task.display", text.parts().get(0).value());
    }

    @Test
    void parsesImportPreludeThenRoot() {
        String source = """
                <import name="Card" from="mcsx:card"/>
                <div><Card/></div>
                """;
        McsxDocument document = McsxParser.parseDocument(source);
        assertEquals(1, document.imports().size());
        assertEquals("mcsx:card", document.imports().get("Card"));
        assertEquals("div", document.root().tag());
        assertEquals("Card", ((McsxElement) document.root().children().get(0)).tag());
    }

    @Test
    void rejectsImportInBareParse() {
        assertPositioned("not allowed", () -> McsxParser.parse("<import name=\"x\" from=\"y\"/>"));
    }

    @Test
    void rejectsMalformedDocuments() {
        assertPositioned("mismatched closing tag", () -> McsxParser.parse("<div></span>"));
        assertPositioned("unterminated element", () -> McsxParser.parse("<div><span></span>"));
        assertPositioned("unexpected content after root", () -> McsxParser.parse("<a/><b/>"));
        assertPositioned("self-closing", () -> McsxParser.parseDocument("<import name=\"x\" from=\"y\"></import>"));
        assertPositioned("duplicate <import>", () -> McsxParser.parseDocument(
                "<import name=\"x\" from=\"a\"/><import name=\"x\" from=\"b\"/><div/>"));
        assertPositioned("requires a non-empty from", () -> McsxParser.parseDocument("<import name=\"x\"/><div/>"));
        assertPositioned("only allowed in the document prelude",
                () -> McsxParser.parseDocument("<div><import name=\"x\" from=\"y\"/></div>"));
        assertPositioned("duplicate attribute", () -> McsxParser.parse("<div id=\"a\" id={value}/>"));
    }

    private static void assertPositioned(String fragment, Runnable action) {
        McsxParseException ex = assertThrows(McsxParseException.class, action::run);
        assertTrue(ex.getMessage().contains(fragment),
                () -> "expected message to contain '" + fragment + "' but was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("line"), "message should carry a position");
    }
}

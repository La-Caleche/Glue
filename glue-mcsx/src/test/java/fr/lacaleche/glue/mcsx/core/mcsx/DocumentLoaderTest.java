package fr.lacaleche.glue.mcsx.core.mcsx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentLoaderTest {

    private static ClassLoader cl() {
        return DocumentLoaderTest.class.getClassLoader();
    }

    @Test
    void mapsIdToResourcePath() {
        assertEquals("assets/mcsx/ui/demo.mcsx", DocumentLoader.resourcePath("mcsx:demo"));
        assertEquals("assets/mcsx/ui/card.mcsx", DocumentLoader.resourcePath("mcsx:card"));
        assertEquals("assets/mcsx/ui/widgets/button.mcsx", DocumentLoader.resourcePath("mcsx:widgets/button"));
    }

    @Test
    void rejectsMalformedIds() {
        assertThrows(IllegalArgumentException.class, () -> DocumentLoader.resourcePath("nocolon"));
        assertThrows(IllegalArgumentException.class, () -> DocumentLoader.resourcePath(":leading"));
        assertThrows(IllegalArgumentException.class, () -> DocumentLoader.resourcePath("trailing:"));
    }

    @Test
    void loadsAndParsesADocument() {
        McsxDocument document = DocumentLoader.load("mcsx:demo", cl());
        assertEquals("div", document.root().tag());
        assertTrue(document.imports().isEmpty());
    }

    @Test
    void loadsADocumentWithAnImportPrelude() {
        McsxDocument document = DocumentLoader.load("mcsx:withcard", cl());
        assertEquals("mcsx:card", document.imports().get("Card"));
        // and the imported component itself resolves + parses
        McsxDocument card = DocumentLoader.load(document.imports().get("Card"), cl());
        assertEquals("div", card.root().tag());
    }

    @Test
    void throwsWhenResourceMissing() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DocumentLoader.load("mcsx:does/not/exist", cl()));
        assertTrue(ex.getMessage().contains("assets/mcsx/ui/does/not/exist.mcsx"));
    }
}

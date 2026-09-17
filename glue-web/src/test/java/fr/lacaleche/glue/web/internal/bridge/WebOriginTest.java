package fr.lacaleche.glue.web.internal.bridge;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebOriginTest {

    @Test
    void normalizesOriginRatherThanComparingUrlPrefixes() {
        WebOrigin origin = WebOrigin.from(URI.create("HTTPS://Example.com/path?q=1"));
        assertEquals(WebOrigin.from(URI.create("https://example.com:443/other")), origin);
        assertTrue(origin.matches("https://example.com/any-path#fragment"));
        assertFalse(origin.matches("https://example.com.attacker.test/"));
        assertFalse(origin.matches("http://example.com/"));
        assertFalse(origin.matches("https://example.com:444/"));
        assertFalse(origin.matches("https://example.com@attacker.test/"));
    }

    @Test
    void loopbackOriginsRemainPortSpecific() {
        WebOrigin origin = WebOrigin.from(URI.create("http://127.0.0.1:8123"));
        assertTrue(origin.matches("http://127.0.0.1:8123/index.html"));
        assertFalse(origin.matches("http://127.0.0.1:8124/index.html"));
        assertFalse(origin.matches("http://localhost:8123/index.html"));
        assertFalse(origin.matches("about:blank"));
        assertFalse(origin.matches("not a URL"));
    }

    @Test
    void rejectsOpaqueOriginsCredentialsAndInvalidPorts() {
        for (String address : new String[] {"file:///tmp/index.html", "about:blank", "/relative",
                "https://user:password@example.com", "https://example.com:70000"}) {
            assertThrows(IllegalArgumentException.class, () -> WebOrigin.from(URI.create(address)), address);
        }
    }

    @Test
    void trustsOnlyGlueAppAddressesByDefault() {
        assertEquals(WebOrigin.from(URI.create("https://glue-showcase.glue/")),
                WebOrigin.defaultFor(URI.create("https://GLUE-SHOWCASE.glue/hud.html")));
        assertEquals(WebOrigin.from(URI.create("https://my--mod.glue/")),
                WebOrigin.defaultFor(URI.create("https://my--mod.glue/index.html")));
        assertNull(WebOrigin.defaultFor(URI.create("http://glue-showcase.glue/hud.html")));
        assertNull(WebOrigin.defaultFor(URI.create("https://example.org/")));
        assertNull(WebOrigin.defaultFor(URI.create("https://glue.example.org/")));
        assertNull(WebOrigin.defaultFor(URI.create("about:blank")));
    }
}

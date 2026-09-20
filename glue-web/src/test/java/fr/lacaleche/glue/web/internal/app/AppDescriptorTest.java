package fr.lacaleche.glue.web.internal.app;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppDescriptorTest {

    private static final String KEY = publicKey();

    @Test
    void readsWhatTheBuildWrote() {
        AppDescriptor descriptor = AppDescriptor.parse(document("ignis:editor", KEY), "ignis", "editor");

        assertEquals("ignis-editor.editor/0", descriptor.contract());
        assertEquals("1.4.0", descriptor.version());
        assertEquals("web", descriptor.resources());
        assertEquals("https://bundles.lacaleche.cc/ignis/editor/channel.json", descriptor.channel().toString());
        assertEquals(1, descriptor.keys().size());
        assertTrue(descriptor.keys().containsKey("release"));
    }

    @Test
    void landsWhereTheBuildPutsIt() {
        assertEquals("assets/ignis/glue-web/apps/editor.json", AppDescriptor.path("ignis", "editor"));
    }

    @Test
    void refusesADescriptorWrittenForAnotherApplication() {
        assertThrows(IllegalArgumentException.class,
                () -> AppDescriptor.parse(document("occamod:hud", KEY), "ignis", "editor"));
    }

    @Test
    void refusesAnUnsupportedFormatOrAKeyItCannotRead() {
        assertThrows(IllegalArgumentException.class,
                () -> AppDescriptor.parse(document("ignis:editor", KEY).replace("\"format\": 1", "\"format\": 2"),
                        "ignis", "editor"));
        assertThrows(IllegalArgumentException.class,
                () -> AppDescriptor.parse(document("ignis:editor", "not-a-key"), "ignis", "editor"));
    }

    @Test
    void refusesADescriptorWithoutTrustedKeys() {
        String document = """
                {"format": 1, "app": "ignis:editor", "contract": "ignis-editor.editor/0", "version": "1.4.0",
                 "resources": "web", "channel": "https://bundles.lacaleche.cc/ignis/editor/channel.json"}
                """;

        assertThrows(IllegalArgumentException.class, () -> AppDescriptor.parse(document, "ignis", "editor"));
    }

    private static String document(String app, String key) {
        return """
                {
                  "format": 1,
                  "app": "%s",
                  "contract": "ignis-editor.editor/0",
                  "version": "1.4.0",
                  "resources": "web",
                  "channel": "https://bundles.lacaleche.cc/ignis/editor/channel.json",
                  "keys": {"release": "%s"}
                }
                """.formatted(app, key);
    }

    private static String publicKey() {
        try {
            return Base64.getEncoder().encodeToString(
                    KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

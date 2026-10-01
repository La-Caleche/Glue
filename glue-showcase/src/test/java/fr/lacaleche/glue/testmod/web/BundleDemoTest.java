package fr.lacaleche.glue.testmod.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BundleDemoTest {

    @TempDir
    Path directory;

    @Test
    void missingConfigurationKeepsTheDemoOffline() {
        assertNull(BundleDemo.readUpdateChannel(this.directory.resolve("absent.properties")));
    }

    @Test
    void loadsAnHttpsChannelAndAnEd25519PublicKey() throws Exception {
        PublicKey key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
        Path file = this.directory.resolve("bundles.properties");
        Files.writeString(file, "channel=https://example.org/releases/channel.json\npublicKey="
                + Base64.getEncoder().encodeToString(key.getEncoded()) + "\n");

        BundleDemo.UpdateChannel channel = BundleDemo.readUpdateChannel(file);
        assertEquals(URI.create("https://example.org/releases/channel.json"), channel.url());
        assertEquals(key, channel.publicKey());
    }

    @Test
    void rejectsPartialConfigurationAndInvalidChannelsOrKeys() throws Exception {
        Path file = this.directory.resolve("bundles.properties");
        Files.writeString(file, "channel=https://example.org/channel.json\n");
        assertThrows(IllegalArgumentException.class, () -> BundleDemo.readUpdateChannel(file));
        Files.writeString(file, "channel=http://example.org/channel.json\npublicKey=AA==\n");
        assertThrows(IllegalArgumentException.class, () -> BundleDemo.readUpdateChannel(file));
        Files.writeString(file, "channel=https://example.org/channel.json\npublicKey=AA==\n");
        assertThrows(IllegalArgumentException.class, () -> BundleDemo.readUpdateChannel(file));
    }
}

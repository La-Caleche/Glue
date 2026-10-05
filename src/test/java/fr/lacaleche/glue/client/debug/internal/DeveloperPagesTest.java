package fr.lacaleche.glue.client.debug.internal;

import fr.lacaleche.glue.client.ui.UiPage;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeveloperPagesTest {

    private static final Supplier<UiPage> PAGE = () -> null;

    @Test
    void pagesGroupByNamespaceInRegistrationOrder() {
        DeveloperPages pages = new DeveloperPages();
        pages.register(id("lumos", "lights"), true, PAGE);
        pages.register(id("glue", "framebuffers"), true, PAGE);
        pages.register(id("lumos", "shadows"), false, PAGE);
        pages.register(id("glue", "raycast"), true, PAGE);

        Map<String, List<DeveloperPages.Entry>> groups = pages.groups();
        assertEquals(List.of("lumos", "glue"), List.copyOf(groups.keySet()));
        assertEquals(List.of(id("lumos", "lights"), id("lumos", "shadows")),
                groups.get("lumos").stream().map(DeveloperPages.Entry::id).toList());
        assertEquals(List.of(id("glue", "framebuffers"), id("glue", "raycast")),
                groups.get("glue").stream().map(DeveloperPages.Entry::id).toList());
    }

    @Test
    void aDuplicateIdIsRejected() {
        DeveloperPages pages = new DeveloperPages();
        pages.register(id("glue", "framebuffers"), true, PAGE);

        assertThrows(IllegalArgumentException.class, () -> pages.register(id("glue", "framebuffers"), false, PAGE));
    }

    @Test
    void registrationClosesOnceTheClientHasStarted() {
        DeveloperPages pages = new DeveloperPages();
        pages.register(id("glue", "framebuffers"), true, PAGE);
        pages.close();

        assertThrows(IllegalStateException.class, () -> pages.register(id("glue", "raycast"), true, PAGE));
        assertEquals(1, pages.groups().get("glue").size());
    }

    @Test
    void groupsAreACopy() {
        DeveloperPages pages = new DeveloperPages();
        pages.groups().put("glue", List.of());

        assertTrue(pages.groups().isEmpty());
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}

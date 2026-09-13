package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.dock.internal.DockContentCache;
import fr.lacaleche.glue.mcsx.client.dock.internal.view.DockHostView;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.View;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockContentCacheTest {

    private final Context context = new HeadlessContext();

    @Test
    void contentAndTrailingHeaderAreCreatedAndDisposedIndependentlyExactlyOnce() {
        AtomicInteger contentCreates = new AtomicInteger();
        AtomicInteger contentDisposes = new AtomicInteger();
        AtomicInteger headerCreates = new AtomicInteger();
        AtomicInteger headerDisposes = new AtomicInteger();
        DockPane pane = DockPane.builder("pane", Component.literal("Pane"),
                        countingContent(contentCreates, contentDisposes))
                .trailingHeader(countingContent(headerCreates, headerDisposes))
                .build();
        DockContentCache cache = new DockContentCache(this.context, Map.of(pane.id(), pane));

        View content = cache.getContent("pane");
        assertSame(content, cache.getContent("pane"));
        assertEquals(0, headerCreates.get());
        View header = cache.getTrailingHeader("pane");
        assertSame(header, cache.getTrailingHeader("pane"));
        cache.close();
        cache.close();

        assertEquals(1, contentCreates.get());
        assertEquals(1, contentDisposes.get());
        assertEquals(1, headerCreates.get());
        assertEquals(1, headerDisposes.get());
        assertThrows(IllegalStateException.class, () -> cache.getContent("pane"));
    }

    @Test
    void absentTrailingHeaderDoesNotCreateAnEntry() {
        DockPane pane = DockPane.builder("pane", Component.literal("Pane"), context -> new View(context)).build();
        DockContentCache cache = new DockContentCache(this.context, Map.of(pane.id(), pane));

        assertNull(cache.getTrailingHeader("pane"));
        cache.close();
    }

    @Test
    void disposalAggregatesFailuresAndStillAttemptsEveryEntry() {
        AtomicInteger attempts = new AtomicInteger();
        DockPane first = failingPane("first", attempts, "content-first", "header-first");
        DockPane second = failingPane("second", attempts, "content-second", "header-second");
        DockContentCache cache = new DockContentCache(
                this.context,
                Map.of(first.id(), first, second.id(), second)
        );
        cache.getContent("first");
        cache.getTrailingHeader("first");
        cache.getContent("second");
        cache.getTrailingHeader("second");

        RuntimeException failure = assertThrows(RuntimeException.class, cache::close);

        assertEquals(4, attempts.get());
        assertEquals(3, failure.getSuppressed().length);
        cache.close();
        assertEquals(4, attempts.get());
    }

    @Test
    void repeatedFailureInstanceDoesNotInterruptDisposal() {
        AtomicInteger attempts = new AtomicInteger();
        IllegalStateException shared = new IllegalStateException("shared");
        DockContent failing = new DockContent() {
            @Override
            public View create(Context context) {
                return new View(context);
            }

            @Override
            public void dispose(View view) {
                attempts.incrementAndGet();
                throw shared;
            }
        };
        DockPane pane = DockPane.builder("pane", Component.literal("Pane"), failing)
                .trailingHeader(failing)
                .build();
        DockContentCache cache = new DockContentCache(this.context, Map.of(pane.id(), pane));
        cache.getContent("pane");
        cache.getTrailingHeader("pane");

        assertSame(shared, assertThrows(IllegalStateException.class, cache::close));
        assertEquals(2, attempts.get());
    }

    @Test
    void rejectsUnknownPanesAndNullFactoryResultsAtTheBoundary() {
        DockPane pane = DockPane.builder("pane", Component.literal("Pane"), context -> null).build();
        DockContentCache cache = new DockContentCache(this.context, Map.of(pane.id(), pane));

        assertThrows(IllegalArgumentException.class, () -> cache.getContent("missing"));
        assertThrows(IllegalStateException.class, () -> cache.getContent("pane"));
    }

    @Test
    void failedHostConstructionDisposesContentCreatedByEarlierPanes() {
        AtomicInteger disposes = new AtomicInteger();
        DockPane first = DockPane.builder("first", Component.literal("First"), new DockContent() {
            @Override
            public View create(Context context) {
                return new View(context);
            }

            @Override
            public void dispose(View view) {
                disposes.incrementAndGet();
            }
        }).build();
        DockPane second = DockPane.builder("second", Component.literal("Second"), context -> {
            throw new IllegalStateException("factory failure");
        }).build();
        Map<String, DockPane> panes = new LinkedHashMap<>();
        panes.put(first.id(), first);
        panes.put(second.id(), second);

        assertThrows(IllegalStateException.class, () -> new DockHostView(
                this.context,
                panes,
                DockLayouts.layout(DockLayouts.row(
                        DockLayouts.tabs("first"),
                        DockLayouts.tabs("second")
                )),
                Signal.of(Themes.dark()),
                ignored -> {
                },
                ignored -> {
                }
        ));
        assertEquals(1, disposes.get());
    }

    private static DockContent countingContent(AtomicInteger creates, AtomicInteger disposes) {
        return new DockContent() {
            @Override
            public View create(Context context) {
                creates.incrementAndGet();
                return new View(context);
            }

            @Override
            public void dispose(View view) {
                disposes.incrementAndGet();
            }
        };
    }

    private static DockPane failingPane(String id, AtomicInteger attempts, String contentMessage,
                                        String headerMessage) {
        return DockPane.builder(id, Component.literal(id), failingContent(attempts, contentMessage))
                .trailingHeader(failingContent(attempts, headerMessage))
                .build();
    }

    private static DockContent failingContent(AtomicInteger attempts, String message) {
        return new DockContent() {
            @Override
            public View create(Context context) {
                return new View(context);
            }

            @Override
            public void dispose(View view) {
                attempts.incrementAndGet();
                throw new IllegalStateException(message);
            }
        };
    }

    private static final class HeadlessContext extends Context {

        private final Resources resources = Resources.getSystem();
        private final Resources.Theme theme = this.resources.newTheme();

        @Override
        public Resources getResources() {
            return this.resources;
        }

        @Override
        public void setTheme(ResourceId resourceId) {
        }

        @Override
        public Resources.Theme getTheme() {
            return this.theme;
        }

        @Override
        public Object getSystemService(String name) {
            return null;
        }
    }
}

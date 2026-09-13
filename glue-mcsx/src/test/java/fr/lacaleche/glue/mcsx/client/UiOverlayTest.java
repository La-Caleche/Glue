package fr.lacaleche.glue.mcsx.client;

import fr.lacaleche.mui.OverlayHandle;
import icyllis.modernui.fragment.Fragment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiOverlayTest {

    @Test
    void hudMountsOneFreshFragmentAndRetainsItThroughUnmount() {
        AtomicInteger built = new AtomicInteger();
        List<FakeHandle> handles = new ArrayList<>();
        UiOverlay.Hud<Fragment> hud = new UiOverlay.Hud<>(
                () -> {
                    built.incrementAndGet();
                    return new Fragment();
                },
                fragment -> {
                    FakeHandle handle = new FakeHandle();
                    handles.add(handle);
                    return handle;
                }
        );

        assertFalse(hud.isMounted());
        assertThrows(IllegalStateException.class, hud::fragment);

        hud.mount();
        Fragment first = hud.fragment();
        assertTrue(hud.isMounted());
        assertEquals(1, built.get());

        hud.mount();
        assertEquals(1, built.get());
        assertSame(first, hud.fragment());

        hud.unmount();
        assertFalse(hud.isMounted());
        assertTrue(handles.get(0).closed);
        assertSame(first, hud.fragment());

        hud.mount();
        assertEquals(2, built.get());
        assertNotSame(first, hud.fragment());
    }

    @Test
    void hudTreatsAnExternallyClosedHandleAsUnmounted() {
        List<FakeHandle> handles = new ArrayList<>();
        UiOverlay.Hud<Fragment> hud = new UiOverlay.Hud<>(Fragment::new, fragment -> {
            FakeHandle handle = new FakeHandle();
            handles.add(handle);
            return handle;
        });

        hud.unmount();
        assertEquals(0, handles.size());

        hud.mount();
        handles.get(0).closed = true;
        assertFalse(hud.isMounted());

        hud.mount();
        assertEquals(2, handles.size());
        assertTrue(hud.isMounted());
    }

    @Test
    void anUnusedOverlaySlotReportsItselfFree() {
        assertFalse(UiOverlay.isOccupied());
    }

    @Test
    void hudRejectsNullFactoryAndNullFactoryResult() {
        assertThrows(NullPointerException.class, () -> UiOverlay.hud(null));

        UiOverlay.Hud<Fragment> hud = new UiOverlay.Hud<>(() -> null, fragment -> new FakeHandle());
        assertThrows(NullPointerException.class, hud::mount);
        assertFalse(hud.isMounted());
    }

    private static final class FakeHandle implements OverlayHandle {

        private boolean closed;

        @Override
        public boolean isClosed() {
            return this.closed;
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }
}

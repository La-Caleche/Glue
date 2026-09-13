package fr.lacaleche.glue.mcsx.client;

import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.View;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiScreenTest {

    private final Context context = new HeadlessContext();

    @Test
    void createViewDelegatesToCreate() {
        View expected = new View(this.context);
        AtomicReference<Ui> received = new AtomicReference<>();
        UiScreen screen = new TestScreen((ui) -> {
            received.set(ui);
            return expected;
        });

        assertSame(expected, screen.createView(this.context));
        assertNotNull(received.get());
    }

    @Test
    void createViewRejectsNullResult() {
        UiScreen screen = new TestScreen(ui -> null);

        assertThrows(NullPointerException.class, () -> screen.createView(this.context));
    }

    @Test
    void screenPoliciesUseDefaultsAndProtectedOverrides() {
        UiScreen defaults = new TestScreen(ui -> new View(this.context));
        UiScreen customized = new TestScreen(ui -> new View(this.context)) {
            @Override
            protected boolean pausesGame() {
                return true;
            }

            @Override
            protected boolean drawsDefaultBackground() {
                return false;
            }

            @Override
            protected boolean canClose() {
                return false;
            }
        };

        assertFalse(defaults.isPauseScreen());
        assertTrue(defaults.hasDefaultBackground());
        assertTrue(defaults.shouldClose());
        assertTrue(customized.isPauseScreen());
        assertFalse(customized.hasDefaultBackground());
        assertFalse(customized.shouldClose());
    }

    @Test
    void screenInstanceCanOnlyBeOpenedOnce() {
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<Screen> previous = new AtomicReference<>();
        UiScreen screen = new TestScreen(ui -> new View(this.context), (opened, previousScreen) -> {
            opens.incrementAndGet();
            previous.set(previousScreen);
        });

        screen.open(null);

        assertNull(previous.get());
        assertThrows(IllegalStateException.class, screen::open);
        assertEquals(1, opens.get());
    }

    @Test
    void destroyedScreenCannotOpenAndLifecycleRemainsOverridable() {
        AtomicInteger destroys = new AtomicInteger();
        UiScreen screen = new TestScreen(ui -> new View(this.context)) {
            @Override
            public void onDestroy() {
                destroys.incrementAndGet();
                super.onDestroy();
            }
        };

        screen.onDestroy();

        assertEquals(1, destroys.get());
        assertThrows(IllegalStateException.class, screen::open);
    }

    private static class TestScreen extends UiScreen {

        private final ViewFactory factory;

        private TestScreen(ViewFactory factory) {
            this.factory = factory;
        }

        private TestScreen(ViewFactory factory, BiConsumer<UiScreen, Screen> opener) {
            super(opener);
            this.factory = factory;
        }

        @Override
        protected View create(Ui ui) {
            return this.factory.create(ui);
        }
    }

    @FunctionalInterface
    private interface ViewFactory {

        View create(Ui ui);
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

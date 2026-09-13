package fr.lacaleche.glue.mcsx.client;

import fr.lacaleche.glue.mcsx.client.internal.CursorRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CursorsTest {

    private final View view = new View(new HeadlessContext());

    @Test
    void assignedCursorCanBeClearedWithoutOwningTheView() {
        PointerIcon hand = Cursors.hand();

        assertSame(this.view, Cursors.set(this.view, hand));
        assertSame(hand, CursorRegistry.cursor(this.view));
        assertSame(this.view, Cursors.clear(this.view));
        assertNull(CursorRegistry.cursor(this.view));
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

package fr.lacaleche.glue.mcsx.client.dock.internal.view;

import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockThemeBindingTest {

    @Test
    void failedAttachClosesTheAcquiredSubscriptionAndCanRetry() {
        TrackableTheme source = new TrackableTheme(Themes.dark());
        int[] applications = {0};
        DockThemeBinding binding = new DockThemeBinding(null, source, ignored -> {
            applications[0]++;
            if (applications[0] == 2) throw new IllegalStateException("apply failed");
        });

        assertThrows(IllegalStateException.class, binding::attach);
        assertEquals(0, source.activeSubscriptions());

        binding.attach();
        assertEquals(1, source.activeSubscriptions());
        binding.detach();
        assertEquals(0, source.activeSubscriptions());
    }

    private static final class TrackableTheme implements Value<Theme> {

        private final Theme theme;
        private int activeSubscriptions;

        private TrackableTheme(Theme theme) {
            this.theme = theme;
        }

        @Override
        public Theme get() {
            return this.theme;
        }

        @Override
        public Subscription subscribe(Consumer<? super Theme> listener) {
            this.activeSubscriptions++;
            return () -> this.activeSubscriptions--;
        }

        private int activeSubscriptions() {
            return this.activeSubscriptions;
        }
    }
}

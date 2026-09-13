package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactiveViewTest {

    private final Context context = new HeadlessContext();

    @BeforeAll
    static void initializeModernUi() {
        if (ModernUI.getInstance() == null) {
            new ModernUI();
        }
    }

    @Test
    void subscribesOnAttachAndClosesOnDetach() {
        Signal<Integer> source = Signal.of(0);
        CountingView view = new CountingView(this.context, source);

        source.set(1);
        assertEquals(0, view.invalidations);

        view.onAttachedToWindow();
        int baseline = view.invalidations;
        source.set(2);
        assertEquals(baseline + 1, view.invalidations);

        view.onDetachedFromWindow();
        source.set(3);
        assertEquals(baseline + 1, view.invalidations);
    }

    @Test
    void reattachResubscribesEverySource() {
        Signal<Integer> first = Signal.of(0);
        Signal<String> second = Signal.of("start");
        CountingView view = new CountingView(this.context, first, second);

        view.onAttachedToWindow();
        view.onDetachedFromWindow();
        view.onAttachedToWindow();

        int baseline = view.invalidations;
        first.set(1);
        second.set("changed");
        assertEquals(baseline + 2, view.invalidations);
    }

    @Test
    void sourcesAddedWhileAttachedSubscribeImmediately() {
        Signal<Integer> late = Signal.of(0);
        CountingView view = new CountingView(this.context);

        view.onAttachedToWindow();
        view.observe(late);

        int baseline = view.invalidations;
        late.set(1);
        assertEquals(baseline + 1, view.invalidations);

        view.onDetachedFromWindow();
        late.set(2);
        assertEquals(baseline + 1, view.invalidations);
    }

    @Test
    void rejectsNullSources() {
        assertThrows(NullPointerException.class, () ->
                new CountingView(this.context, (Value<?>[]) null));
        assertThrows(NullPointerException.class, () ->
                new CountingView(this.context, (Value<?>) null));
    }

    @Test
    void subscriptionFailureClosesAcquiredSubscriptionsAndAttachCanRetry() {
        TrackingValue<Integer> first = new TrackingValue<>(1);
        TrackingValue<Integer> second = new TrackingValue<>(2);
        IllegalStateException failure = new IllegalStateException("subscription failure");
        second.subscribeFailure = failure;
        CountingView view = new CountingView(this.context, first, second);

        assertThrows(IllegalStateException.class, view::onAttachedToWindow);
        assertEquals(1, first.closeCount);

        view.onDetachedFromWindow();
        second.subscribeFailure = null;
        view.onAttachedToWindow();

        int baseline = view.invalidations;
        second.emit(3);
        assertEquals(baseline + 1, view.invalidations);
        assertEquals(2, first.subscribeCount);
        assertEquals(2, second.subscribeCount);
    }

    private static final class CountingView extends ReactiveView {

        private int invalidations;

        private CountingView(Context context, Value<?>... sources) {
            super(context);
            this.invalidateOn(sources);
        }

        private void observe(Value<?> source) {
            this.invalidateOn(source);
        }

        @Override
        public void invalidate() {
            this.invalidations++;
            super.invalidate();
        }
    }

    private static final class TrackingValue<T> implements Value<T> {

        private final List<Consumer<? super T>> listeners = new ArrayList<>();
        private T value;
        private RuntimeException subscribeFailure;
        private int subscribeCount;
        private int closeCount;

        private TrackingValue(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return this.value;
        }

        @Override
        public Subscription subscribe(Consumer<? super T> listener) {
            this.subscribeCount++;
            if (this.subscribeFailure != null) throw this.subscribeFailure;

            this.listeners.add(listener);
            return new Subscription() {

                private boolean active = true;

                @Override
                public void close() {
                    if (!this.active) return;

                    this.active = false;
                    TrackingValue.this.closeCount++;
                    TrackingValue.this.listeners.remove(listener);
                }
            };
        }

        private void emit(T value) {
            this.value = value;
            for (Consumer<? super T> listener : List.copyOf(this.listeners)) {
                listener.accept(value);
            }
        }
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

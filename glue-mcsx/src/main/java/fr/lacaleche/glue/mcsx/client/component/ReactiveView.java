package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base for custom canvas Views that redraw when reactive sources change. {@link #invalidateOn}
 * declares the sources — usually from the constructor — and this class scopes the subscriptions to
 * window attachment: it subscribes on attach, closes on detach, and resubscribes when a dock
 * mutation reparents the view. Subclasses overriding {@link #onAttachedToWindow} or
 * {@link #onDetachedFromWindow} must call the super implementation.
 *
 * <p>Subscribing is transactional: if one source fails to subscribe, the subscriptions already
 * acquired are closed before the failure propagates, so a later attach can retry.</p>
 */
@Environment(EnvType.CLIENT)
public abstract class ReactiveView extends View {

    private final List<Value<?>> redrawSources = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private boolean subscribed;

    protected ReactiveView(Context context) {
        super(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        try {
            for (Value<?> source : this.redrawSources) {
                this.subscriptions.add(source.subscribe(ignored -> this.invalidate()));
            }
        } catch (RuntimeException exception) {
            RuntimeException failure = this.closeSubscriptions(null);
            if (failure != null) exception.addSuppressed(failure);
            throw exception;
        }
        this.subscribed = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        this.subscribed = false;
        RuntimeException failure = this.closeSubscriptions(null);
        failure = LifecycleCleanup.attempt(failure, super::onDetachedFromWindow);
        LifecycleCleanup.finish(failure);
    }

    /**
     * Redraws this view whenever any source changes, for as long as the view is attached to a
     * window. Sources added while attached are subscribed immediately.
     */
    protected final void invalidateOn(Value<?>... sources) {
        Objects.requireNonNull(sources, "sources");
        for (int index = 0; index < sources.length; index++) {
            Value<?> source = Objects.requireNonNull(sources[index], "sources[" + index + "]");
            this.redrawSources.add(source);
            if (this.subscribed) {
                this.subscriptions.add(source.subscribe(ignored -> this.invalidate()));
            }
        }
    }

    private RuntimeException closeSubscriptions(RuntimeException failure) {
        for (Subscription subscription : this.subscriptions) {
            failure = LifecycleCleanup.attempt(failure, subscription::close);
        }
        this.subscriptions.clear();
        return failure;
    }
}

package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.Stylesheet;
import fr.lacaleche.glue.mcsx.client.style.internal.CompoundSelector;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleSelector;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.ViewParent;

import java.util.Objects;

final class StyleScope {

    private final TaffyLayout owner;
    private final Value<? extends Stylesheet> source;
    private Stylesheet current;
    private Subscription subscription;

    StyleScope(TaffyLayout owner, Value<? extends Stylesheet> source) {
        this.owner = owner;
        this.source = Objects.requireNonNull(source, "source");
        this.current = Objects.requireNonNull(source.get(), "stylesheet value");
    }

    void mount() {
        if (this.subscription != null) return;

        Subscription acquired = this.source.subscribe(this::apply);
        try {
            this.apply(Objects.requireNonNull(this.source.get(), "stylesheet value"));
            this.subscription = acquired;
        } catch (RuntimeException exception) {
            try {
                acquired.close();
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    void unmount() {
        Subscription acquired = this.subscription;
        if (acquired == null) return;

        this.subscription = null;
        acquired.close();
    }

    void recomputeSubtree(View view) {
        if (view != this.owner
                && view instanceof TaffyLayout layout
                && TaffyLayout.findStyleScope(layout) != this) return;

        this.recompute(view);
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                this.recomputeSubtree(group.getChildAt(index));
            }
        }
    }

    void clearSubtree(View view) {
        StyleMetadata metadata = StyleComponents.metadata(view);
        if (metadata != null) {
            metadata.clear(this);
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                this.clearSubtree(group.getChildAt(index));
            }
        }
    }

    static boolean matches(View view, StyleSelector selector) {
        if (!matchesCompound(view, selector.target())) return false;
        if (selector.parent() == null) return true;

        View parent = styledParent(view);
        return parent != null && matchesCompound(parent, selector.parent());
    }

    private void apply(Stylesheet stylesheet) {
        this.current = Objects.requireNonNull(stylesheet, "stylesheet");
        this.recomputeSubtree(this.owner);
    }

    private void recompute(View view) {
        StyleMetadata metadata = StyleComponents.metadata(view);
        if (metadata != null) {
            metadata.recompute(this.current, this);
        }
    }

    private static boolean matchesCompound(View view, CompoundSelector selector) {
        if (selector.part() == null) {
            return matchesMetadata(view, selector);
        }
        StyleMetadata target = StyleComponents.metadata(view);
        if (target == null || !target.hasPart(selector.part())) return false;

        View owner = styledParent(view);
        return owner != null && matchesMetadata(owner, selector);
    }

    /**
     * Returns the closest ancestor that participates in styling, so selectors traverse the styled
     * tree rather than the raw View tree. Layout-only containers such as the ScrollView inserted by
     * {@code Ui.screen} carry no metadata and are transparent to the child combinator.
     */
    private static View styledParent(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View parentView) {
            if (StyleComponents.metadata(parentView) != null) return parentView;
            parent = parentView.getParent();
        }
        return null;
    }

    private static boolean matchesMetadata(View view, CompoundSelector selector) {
        StyleMetadata metadata = StyleComponents.metadata(view);
        if (metadata == null) return false;
        if (selector.type() != null && !metadata.hasType(selector.type())) return false;
        for (String name : selector.classes()) {
            if (!metadata.hasClass(name)) return false;
        }
        for (String name : selector.states()) {
            if (!metadata.hasState(name)) return false;
        }
        return true;
    }
}

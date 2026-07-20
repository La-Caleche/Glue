package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.reactive.Effect;
import icyllis.modernui.view.View;

import java.util.List;

/**
 * A bound screen: the root {@code View} plus every {@link Effect} created while binding it.
 * {@link #close()} disposes them all — call it when the screen is torn down (see {@code McsxFragment}).
 */
public final class ViewInstance {

    private final View root;
    private final List<Effect> effects;

    public ViewInstance(View root, List<Effect> effects) {
        this.root = root;
        this.effects = effects;
    }

    public View root() {
        return root;
    }

    public void close() {
        for (Effect effect : effects) {
            effect.dispose();
        }
        effects.clear();
    }
}

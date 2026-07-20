package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.reactive.Effect;
import fr.lacaleche.glue.mcsx.core.reactive.Reactive;
import fr.lacaleche.glue.mcsx.core.theme.Theme;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import icyllis.modernui.view.View;

import java.util.function.Consumer;

/**
 * Binds a chrome view's colours to the active theme. {@code paint} runs once immediately
 * (untracked, so a caller inside an effect leaks no dependency on the theme) and then, while the
 * view is attached, re-runs plus an invalidate on every theme change. The subscription lives from
 * attach to detach — a dropped chrome view holds no effect on the global theme signal and stays
 * collectable; a re-attach subscribes afresh. Call from the view's constructor, before it is
 * first attached.
 */
final class Themed {

    private Themed() {
    }

    static void onTheme(View view, Consumer<Theme> paint) {
        Reactive.untracked(() -> paint.accept(Themes.active()));
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            private Effect effect;

            @Override
            public void onViewAttachedToWindow(View v) {
                if (effect == null) {
                    effect = Effect.of(() -> {
                        paint.accept(Themes.active());
                        v.invalidate();
                    });
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (effect != null) {
                    effect.dispose();
                    effect = null;
                }
            }
        });
    }
}

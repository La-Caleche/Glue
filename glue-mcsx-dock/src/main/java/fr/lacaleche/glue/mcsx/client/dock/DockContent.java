package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.Ui;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Objects;
import java.util.function.Function;

/** Creates one retained pane view whose lifecycle is owned by its Dockspace. */
@Environment(EnvType.CLIENT)
@FunctionalInterface
public interface DockContent {

    static DockContent ui(Function<? super Ui, ? extends View> factory) {
        Objects.requireNonNull(factory, "factory");
        return context -> factory.apply(Ui.with(context));
    }

    View create(Context context);

    default void dispose(View view) {
    }
}

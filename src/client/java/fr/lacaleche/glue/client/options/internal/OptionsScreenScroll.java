package fr.lacaleche.glue.client.options.internal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

/**
 * Scrolls the options pages that lay out their own rows, through Fabric's scroll event rather than an override
 * of the screen's {@code mouseScrolled}, which another mod's mixin on the same screen could also claim.
 */
@Environment(EnvType.CLIENT)
public final class OptionsScreenScroll {

    private OptionsScreenScroll() {
    }

    /** Listens to the wheel on every screen that is {@link Scrollable}. Call it once, at client init. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof Scrollable scrollable)) return;
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) ->
                    !scrollable.glue$scrollOptions(vertical));
        });
    }

    /** A screen whose page may scroll its own rows. */
    public interface Scrollable {

        /** Scrolls the page shown by {@code vertical} wheel notches; true when it took them. */
        boolean glue$scrollOptions(double vertical);
    }
}

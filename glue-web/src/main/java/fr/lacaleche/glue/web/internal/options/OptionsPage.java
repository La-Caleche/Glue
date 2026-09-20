package fr.lacaleche.glue.web.internal.options;

import fr.lacaleche.glue.web.WebSettings;
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.bridge.WebAction;
import fr.lacaleche.glue.web.host.WebScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Glue's own settings page. It is the only page the library ships, served from its resources like any
 * mod's, and it previews what it edits: the page is drawn at the very scale its slider sets.
 */
public final class OptionsPage {

    private static final String PATH = "options/index.html";

    /** Opens the page over the displayed screen, which it returns to when closed. */
    public static void open() {
        WebScreen.builder(WebApp.of("glue-web").page(PATH))
                .title(Component.translatable("gui.glue-web.options.title"))
                .background(true)
                .bind(new OptionsPage())
                .state("settings", OptionsPage::snapshot)
                .open();
    }

    @WebAction("settings.followGameScale")
    void followGameScale() {
        WebSettings.followGameScale();
    }

    @WebAction("settings.scale")
    void setScale(double scale) {
        WebSettings.setScale(scale);
    }

    private static Settings snapshot() {
        return new Settings(WebSettings.followsGameScale(), WebSettings.scale(),
                Minecraft.getInstance().getWindow().getGuiScale(),
                WebSettings.MIN_SCALE, WebSettings.MAX_SCALE, WebSettings.SCALE_STEP);
    }

    record Settings(boolean followsGameScale, double scale, double gameScale,
                    double minimum, double maximum, double step) {
    }
}

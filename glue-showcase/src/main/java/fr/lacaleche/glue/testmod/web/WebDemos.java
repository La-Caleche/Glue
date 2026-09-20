package fr.lacaleche.glue.testmod.web;

import fr.lacaleche.glue.testmod.gametest.web.WebGameTest;
import fr.lacaleche.glue.testmod.web.hub.HubActions;
import fr.lacaleche.glue.testmod.web.hud.Minimap;
import fr.lacaleche.glue.testmod.web.hud.PlayerVitals;
import fr.lacaleche.glue.testmod.web.inventory.InventoryPanel;
import fr.lacaleche.glue.testmod.web.lab.LabActions;
import fr.lacaleche.glue.testmod.web.toast.WebToasts;
import fr.lacaleche.glue.testmod.web.waypoint.WaypointActions;
import fr.lacaleche.glue.testmod.web.waypoint.Waypoints;
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.host.WebHud;
import fr.lacaleche.glue.web.host.WebScreen;
import net.minecraft.network.chat.Component;

/**
 * Glue Web demos. Every page lives in {@code assets/glue-showcase/web/} and is served from
 * {@code https://glue-showcase.glue/}. Layers are registered at startup; the two HUDs start disabled so
 * unrelated showcase scenarios keep the vanilla HUD, and F6 opens the hub that toggles them.
 */
public final class WebDemos {

    public static final String MOD_ID = "glue-showcase";

    private static WebApp app;
    private static WebHud vitals;
    private static WebHud minimap;
    private static WebToasts toasts;

    private WebDemos() {
    }

    public static void init() {
        if (app != null) throw new IllegalStateException("Web demos are already registered");
        app = WebApp.of(MOD_ID);
        BundleDemo.init();
        vitals = PlayerVitals.register(app);
        vitals.setEnabled(false);
        minimap = Minimap.register(app);
        minimap.setEnabled(false);
        toasts = WebToasts.register(app);
        Waypoints.register();
        InventoryPanel.register(app);
        WebCommands.register();
        WebGameTest.register();
    }

    public static WebApp app() {
        return app;
    }

    public static WebHud vitals() {
        return vitals;
    }

    public static WebHud minimap() {
        return minimap;
    }

    public static WebToasts toasts() {
        return toasts;
    }

    /** The hub lists every demo and toggles the layers. */
    public static WebScreen openHub() {
        return WebScreen.builder(app.page("index.html"))
                .title(Component.literal("Glue Web"))
                .bind(new HubActions())
                .state("layers", HubActions.Layers::capture)
                .open();
    }

    /** The input lab exercises native input, actions, state and events. */
    public static WebScreen openLab() {
        LabActions lab = new LabActions();
        return WebScreen.builder(app.page("lab.html"))
                .title(Component.literal("Glue Web lab"))
                .bind(lab)
                .state("game", LabActions::snapshot)
                .open();
    }

    public static WebScreen openWaypoints() {
        return WaypointActions.screen(app).open();
    }

    public static void toast(String title, String message) {
        toasts.show(title, message);
    }
}

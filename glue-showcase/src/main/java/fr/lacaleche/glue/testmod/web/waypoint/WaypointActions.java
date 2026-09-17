package fr.lacaleche.glue.testmod.web.waypoint;

import fr.lacaleche.glue.testmod.web.WebDemos;
import fr.lacaleche.glue.web.bridge.WebAction;
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.host.WebScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The waypoint manager screen and the removal dialog stacked above it. The dialog is a second web
 * screen; closing it returns to the manager without reloading its page.
 */
public final class WaypointActions {

    private final WebApp app;

    private WaypointActions(WebApp app) {
        this.app = app;
    }

    public static WebScreen.Builder screen(WebApp app) {
        return WebScreen.builder(app.page("waypoints.html"))
                .title(Component.literal("Waypoints"))
                .bind(new WaypointActions(app))
                .state("waypoints", Waypoints::views)
                .state("colors", () -> Waypoints.COLORS)
                .state("here", Here::capture);
    }

    private record Here(String position) {

        static Here capture() {
            LocalPlayer player = Minecraft.getInstance().player;
            return player == null ? null
                    : new Here(player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ());
        }
    }

    @WebAction("waypoints.create")
    Waypoints.View create(NewWaypoint request) {
        if (request == null) throw new IllegalArgumentException("Describe the waypoint to add");
        Waypoints.Waypoint waypoint = Waypoints.create(request.name(), request.color());
        WebDemos.toast("Waypoint added", waypoint.name() + " is now on your minimap.");
        return waypoint.view(Minecraft.getInstance().player);
    }

    @WebAction("waypoints.askRemoval")
    void askRemoval(WaypointId request) {
        if (request == null) throw new IllegalArgumentException("Choose a waypoint to remove");
        Waypoints.Waypoint waypoint = Waypoints.find(request.id())
                .orElseThrow(() -> new IllegalArgumentException("This waypoint no longer exists"));
        WebScreen.builder(this.app.page("confirm.html"))
                .title(Component.literal("Remove " + waypoint.name()))
                .state("removal", () -> Waypoints.find(waypoint.id())
                        .map(found -> found.view(Minecraft.getInstance().player)).orElse(null))
                .bind(new Removal(waypoint.id()))
                .open();
    }

    record NewWaypoint(String name, String color) {
    }

    record WaypointId(int id) {
    }

    /** Actions of one removal dialog. */
    static final class Removal {

        private final int id;

        Removal(int id) {
            this.id = id;
        }

        @WebAction("removal.confirm")
        void confirm() {
            Waypoints.Waypoint waypoint = Waypoints.find(this.id)
                    .orElseThrow(() -> new IllegalStateException("This waypoint was already removed"));
            Waypoints.remove(this.id);
            WebDemos.toast("Waypoint removed", waypoint.name() + " is no longer tracked.");
        }
    }
}

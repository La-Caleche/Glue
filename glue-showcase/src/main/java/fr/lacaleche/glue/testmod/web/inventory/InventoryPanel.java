package fr.lacaleche.glue.testmod.web.inventory;

import fr.lacaleche.glue.testmod.web.WebDemos;
import fr.lacaleche.glue.testmod.web.waypoint.Waypoints;
import fr.lacaleche.glue.web.bridge.WebAction;
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.host.WebWidget;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.RecipeBookType;

import java.util.Locale;

/**
 * A web widget beside the survival inventory. One widget is reused by every inventory screen; it
 * hides when the recipe book is open or the window is too narrow.
 */
public final class InventoryPanel {

    static final int WIDTH = 110;
    static final int HEIGHT = 166;
    private static final int INVENTORY_WIDTH = 176;
    private static final int GAP = 6;
    private static WebWidget widget;

    private InventoryPanel() {
    }

    public static void register(WebApp app) {
        widget = WebWidget.builder(app.page("panel.html"))
                .title(Component.literal("Field notes"))
                .bind(new InventoryPanel.Actions())
                .state("notes", Notes::capture)
                .build(0, 0, WIDTH, HEIGHT);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof InventoryScreen)) return;
            widget.setPosition((width - INVENTORY_WIDTH) / 2 - GAP - WIDTH, (height - HEIGHT) / 2);
            Screens.getButtons(screen).add(widget);
            ScreenEvents.beforeRender(screen).register((current, graphics, mouseX, mouseY, delta) ->
                    widget.visible = fits(current) && !isRecipeBookOpen(client.player));
        });
    }

    public static WebWidget widget() {
        return widget;
    }

    private static boolean fits(Screen screen) {
        return widget.getX() >= GAP && widget.getY() >= 0 && widget.getY() + HEIGHT <= screen.height;
    }

    private static boolean isRecipeBookOpen(LocalPlayer player) {
        return player != null && player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
    }

    static final class Actions {

        @WebAction("waypoints.open")
        void openWaypoints() {
            WebDemos.openWaypoints();
        }

        @WebAction("waypoints.markHere")
        String markHere() {
            Waypoints.Waypoint camp = Waypoints.createCamp();
            WebDemos.toast("Camp marked", camp.name() + " is saved at your position.");
            return camp.name();
        }
    }

    record Notes(String position, String biome, String time, int light, String weather, int waypoints) {

        static Notes capture() {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (player == null || client.level == null) return null;

            long dayTime = client.level.getDayTime();
            long ticks = (dayTime + 6000) % 24000;
            String time = String.format(Locale.ROOT, "Day %d, %02d:%02d", dayTime / 24000 + 1, ticks / 1000, ticks % 1000 * 60 / 1000);
            String weather = client.level.isThundering() ? "Thunder" : client.level.isRaining() ? "Rain" : "Clear";
            String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                    .map(key -> Component.translatable("biome." + key.location().getNamespace() + "." + key.location().getPath()).getString())
                    .orElse("");
            return new Notes(String.format(Locale.ROOT, "%d, %d, %d", player.getBlockX(), player.getBlockY(), player.getBlockZ()),
                    biome, time, client.level.getMaxLocalRawBrightness(player.blockPosition()), weather, Waypoints.all().size());
        }
    }
}

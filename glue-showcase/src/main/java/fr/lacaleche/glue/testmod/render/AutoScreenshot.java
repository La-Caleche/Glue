package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.testmod.lumos.DemoLights;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Opt-in periodic screenshots for headless-ish render verification: launching the client with
 * {@code -Dglue.showcase.autoshot=<seconds>} saves {@code screenshots/glue-autoshot-<n>.png}
 * (a rotating window of {@link #KEEP}) every interval once a world is loaded. With
 * {@code -Dglue.showcase.autotest=true} a few demo lights spawn around the player shortly after
 * join, so the shots show lit geometry without anyone at the keyboard. Off without the
 * properties; costs nothing in normal play.
 */
@Environment(EnvType.CLIENT)
public final class AutoScreenshot {

    private static final int KEEP = 8;
    /** Ticks after join before the autotest lights spawn: past quickplay's initial loading haze. */
    private static final int AUTOTEST_DELAY = 60;

    private AutoScreenshot() {
    }

    public static void init() {
        int intervalTicks = Integer.getInteger("glue.showcase.autoshot", 0) * 20;
        boolean autotest = Boolean.getBoolean("glue.showcase.autotest");
        if (intervalTicks <= 0 && !autotest) return;
        int[] state = new int[3];
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) {
                state[2] = 0;
                return;
            }
            if (autotest && state[2] >= 0 && ++state[2] >= AUTOTEST_DELAY) {
                state[2] = -1;
                Vec3 eye = client.player.getEyePosition();
                Vec3 look = client.player.getViewVector(1.0f);
                DemoLights.INSTANCE.spawn(client.player.level(), Light.point(
                        eye.x + look.x * 4.0, eye.y, eye.z + look.z * 4.0,
                        1.0f, 0.4f, 0.2f, 3.0f, 12.0f));
                DemoLights.INSTANCE.spawn(client.player.level(), Light.point(
                        eye.x - look.x * 3.0, eye.y + 1.0, eye.z - look.z * 3.0,
                        0.3f, 0.6f, 1.0f, 3.0f, 12.0f));
                // Midnight (so light contribution is visible) and hotbar items (so the shots show
                // whether GUI item models survive the lighting pass) -- straight onto the
                // in-process integrated server, no permissions involved.
                MinecraftServer server = client.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        server.overworld().setDayTime(18000L);
                        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                            player.getInventory().add(new ItemStack(Items.GLOWSTONE));
                            player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
                            player.getInventory().add(new ItemStack(Items.PRISMARINE));
                        }
                    });
                }
            }
            if (intervalTicks > 0 && ++state[0] >= intervalTicks) {
                state[0] = 0;
                String name = "glue-autoshot-" + (state[1]++ % KEEP) + ".png";
                Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1,
                        component -> {});
            }
        });
    }
}

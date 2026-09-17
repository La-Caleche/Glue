package fr.lacaleche.glue.testmod.web.hud;

import com.mojang.blaze3d.platform.NativeImage;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.web.waypoint.Waypoints;
import fr.lacaleche.glue.web.host.WebAnchor;
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.host.WebHud;
import fr.lacaleche.glue.web.bridge.WebSlot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.List;

/**
 * The minimap HUD. Java samples the terrain into a texture drawn behind the page; the page draws the
 * brass bezel, compass, player arrow and waypoint pins above it, from published state.
 */
public final class Minimap {

    static final int RADIUS = 48;
    private static final int SIZE = RADIUS * 2;
    private static final int REFRESH_TICKS = 40;
    private static final int MOVE_REFRESH_TICKS = 5;
    private static final int UNKNOWN = 0xFF1C2A2B;
    private static final ResourceLocation TEXTURE = TestmodClient.id("web_minimap");

    private static DynamicTexture texture;
    private static BlockPos center;
    private static ResourceKey<Level> dimension;
    private static int age;

    private Minimap() {
    }

    public static WebHud register(WebApp app) {
        WebHud hud = WebHud.builder(TestmodClient.id("web_minimap"), app.page("minimap.html"))
                .anchor(WebAnchor.TOP_RIGHT, 104, 124)
                .offset(4, 4)
                .state("map", Minimap::capture)
                .slotBehind("terrain", Minimap::draw)
                .register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (hud.isEnabled()) refreshIfNeeded(client);
        });
        return hud;
    }

    static State capture() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || center == null) return null;

        ResourceKey<Level> current = player.level().dimension();
        List<Pin> pins = Waypoints.all().stream()
                .filter(waypoint -> waypoint.dimension().equals(current))
                .map(waypoint -> new Pin(waypoint.id(), waypoint.name(), waypoint.color(),
                        waypoint.position().getX() - center.getX(), waypoint.position().getZ() - center.getZ(),
                        (int) Math.round(Math.sqrt(player.blockPosition().distSqr(waypoint.position())))))
                .toList();
        String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> Component.translatable("biome." + key.location().getNamespace() + "." + key.location().getPath()).getString())
                .orElse("");
        return new State(RADIUS, player.getBlockX() - center.getX(), player.getBlockZ() - center.getZ(),
                Math.round(player.getYRot()), player.getBlockX(), player.getBlockY(), player.getBlockZ(), biome, pins);
    }

    private static void refreshIfNeeded(Minecraft client) {
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            center = null;
            return;
        }

        age++;
        BlockPos position = player.blockPosition();
        boolean moved = !position.equals(center) || !level.dimension().equals(dimension);
        if (age < REFRESH_TICKS && !(moved && age >= MOVE_REFRESH_TICKS)) return;
        age = 0;
        center = position;
        dimension = level.dimension();
        sample(client, level, position);
    }

    private static void sample(Minecraft client, ClientLevel level, BlockPos origin) {
        if (texture == null) {
            texture = new DynamicTexture(() -> "Glue showcase minimap", SIZE, SIZE, true);
            client.getTextureManager().register(TEXTURE, texture);
        }
        NativeImage pixels = texture.getPixels();
        if (pixels == null) return;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int ceilingStart = level.dimensionType().hasCeiling() ? origin.getY() + 2 : Integer.MIN_VALUE;
        int[] northHeights = new int[SIZE];
        for (int column = 0; column < SIZE; column++) {
            northHeights[column] = surfaceY(level, cursor, origin.getX() - RADIUS + column, origin.getZ() - RADIUS - 1, ceilingStart);
        }
        for (int row = 0; row < SIZE; row++) {
            int z = origin.getZ() - RADIUS + row;
            for (int column = 0; column < SIZE; column++) {
                int x = origin.getX() - RADIUS + column;
                int y = surfaceY(level, cursor, x, z, ceilingStart);
                pixels.setPixel(column, row, color(level, cursor, x, y, z, northHeights[column]));
                northHeights[column] = y;
            }
        }
        texture.upload();
    }

    /** The top visible block, or the first floor below the player in dimensions with a ceiling. */
    private static int surfaceY(ClientLevel level, BlockPos.MutableBlockPos cursor, int x, int z, int ceilingStart) {
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) return Integer.MIN_VALUE;
        if (ceilingStart == Integer.MIN_VALUE) return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;

        int y = Math.min(ceilingStart, level.getMaxY());
        for (int step = 0; step < 32 && y > level.getMinY(); step++, y--) {
            if (!level.getBlockState(cursor.set(x, y, z)).isAir() && level.getBlockState(cursor.set(x, y + 1, z)).isAir()) return y;
        }
        return Integer.MIN_VALUE;
    }

    private static int color(ClientLevel level, BlockPos.MutableBlockPos cursor, int x, int y, int z, int northY) {
        if (y == Integer.MIN_VALUE) return UNKNOWN;

        BlockState state = level.getBlockState(cursor.set(x, y, z));
        MapColor color = state.getMapColor(level, cursor);
        for (int step = 0; color == MapColor.NONE && step < 8 && cursor.getY() > level.getMinY(); step++) {
            cursor.move(0, -1, 0);
            state = level.getBlockState(cursor);
            color = state.getMapColor(level, cursor);
        }
        if (color == MapColor.NONE) return UNKNOWN;

        MapColor.Brightness brightness;
        if (state.getFluidState().is(FluidTags.WATER)) {
            int depth = 0;
            while (depth < 8 && level.getFluidState(cursor.move(0, -1, 0)).is(FluidTags.WATER)) depth++;
            brightness = depth < 2 ? MapColor.Brightness.HIGH : depth < 5 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
        } else if (northY == Integer.MIN_VALUE || y == northY) {
            brightness = MapColor.Brightness.NORMAL;
        } else {
            brightness = y > northY ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
        }
        return color.calculateARGBColor(brightness);
    }

    private static void draw(GuiGraphics graphics, WebSlot slot) {
        if (texture == null || center == null) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, slot.x(), slot.y(), 0, 0,
                slot.width(), slot.height(), SIZE, SIZE, SIZE, SIZE);
    }

    /**
     * Offsets are in blocks from the texture center, which follows the player in whole-block steps.
     *
     * @param heading the player's yaw in degrees; 0 faces south
     */
    record State(int radius, int playerX, int playerZ, int heading, int x, int y, int z, String biome, List<Pin> pins) {
    }

    record Pin(int id, String name, String color, int dx, int dz, int distance) {
    }
}

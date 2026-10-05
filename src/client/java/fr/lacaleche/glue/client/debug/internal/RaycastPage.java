package fr.lacaleche.glue.client.debug.internal;

import fr.lacaleche.glue.client.debug.DebugManager;
import fr.lacaleche.glue.client.debug.RaycastDebugRenderer;
import fr.lacaleche.glue.client.ui.UiPage;
import fr.lacaleche.glue.client.ui.UiPageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The developer menu's Raycast page: a switch for Glue's raycast overlay, and the camera's last pick
 * as rows, traced again each tick while the page is shown. The overlay stays on after the menu closes,
 * because the pick needs the player to move and look.
 */
public final class RaycastPage implements UiPage {

    /** Glue's raycast overlay, drawn in the world and on the HUD while enabled. */
    public static final RaycastDebugRenderer OVERLAY = new RaycastDebugRenderer();

    private static final int REACHING_ROWS = 6;

    private @Nullable RaycastProbe probe;
    private long probedAt = Long.MIN_VALUE;

    /** Registers the overlay with the debug renderers; called once during client initialisation. */
    public static void register() {
        DebugManager.getInstance().register(OVERLAY);
    }

    @Override
    public Component title() {
        return text("title");
    }

    @Override
    public void build(UiPageBuilder builder) {
        builder.section(text("overlay"));
        builder.toggle(text("overlay.switch"), text("overlay.description"), () -> OVERLAY.enabled, enabled -> {
            OVERLAY.enabled = enabled;
            if (!enabled) OVERLAY.clear();
        });

        builder.section(text("ray"));
        builder.label(text("origin"), null, this.read(probe -> Component.literal(position(probe.origin()))));
        builder.label(text("target"), null, this.read(probe -> Component.literal(position(probe.target()))));
        builder.label(text("range"), null,
                () -> Component.literal(String.format(Locale.ROOT, "%.0f", RaycastProbe.MAX_DISTANCE)));

        builder.section(text("hits"));
        builder.label(text("vanilla"), text("vanilla.description"), this.read(probe -> hit(probe, probe.vanilla())));
        builder.label(text("cells"), text("cells.description"), this.read(probe -> hit(probe, probe.trace().cell())));
        builder.label(text("final"), text("final.description"),
                this.read(probe -> hit(probe, probe.trace().result())));
        builder.label(text("block"), null, this.read(probe -> {
            BlockHitResult result = probe.trace().result();
            if (result.getType() == HitResult.Type.MISS) return text("miss");

            return probe.level().getBlockState(result.getBlockPos()).getBlock().getName();
        }));
        builder.label(text("matches"), text("matches.description"),
                this.read(probe -> probe.matchesVanilla() ? CommonComponents.GUI_YES : CommonComponents.GUI_NO));

        builder.section(text("reaching"));
        builder.label(text("reaching.count"), text("reaching.description"),
                this.read(probe -> Component.literal(Integer.toString(probe.trace().reaching().size()))));
        for (int i = 0; i < REACHING_ROWS; i++) {
            int index = i;
            builder.label(Component.literal(Integer.toString(i + 1)), null, this.read(probe -> {
                List<BlockPos> reaching = probe.trace().reaching();
                if (index >= reaching.size()) return CommonComponents.EMPTY;

                BlockPos pos = reaching.get(index);
                return Component.literal(pos.toShortString() + "  ")
                        .append(probe.level().getBlockState(pos).getBlock().getName());
            }));
        }
    }

    @Override
    public void close() {
        this.probe = null;
        this.probedAt = Long.MIN_VALUE;
    }

    /** A label value read from the probe, traced at most once a tick; empty without a world. */
    private Supplier<Component> read(Function<RaycastProbe, Component> value) {
        return () -> {
            RaycastProbe current = this.current();
            return current == null ? CommonComponents.EMPTY : value.apply(current);
        };
    }

    private @Nullable RaycastProbe current() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;

        long time = client.level.getGameTime();
        if (this.probe == null || time != this.probedAt) {
            this.probe = RaycastProbe.probe();
            this.probedAt = time;
        }
        return this.probe;
    }

    private static Component hit(RaycastProbe probe, @Nullable BlockHitResult result) {
        if (result == null || result.getType() == HitResult.Type.MISS) return text("miss");

        return Component.literal(result.getBlockPos().toShortString() + "  " + result.getDirection().getSerializedName()
                + "  " + String.format(Locale.ROOT, "%.2f", result.getLocation().distanceTo(probe.origin())));
    }

    private static String position(Vec3 position) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", position.x, position.y, position.z);
    }

    private static Component text(String key) {
        return Component.translatable("glue.developer_menu.raycast." + key);
    }
}

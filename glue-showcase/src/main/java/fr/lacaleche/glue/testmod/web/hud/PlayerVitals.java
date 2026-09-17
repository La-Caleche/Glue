package fr.lacaleche.glue.testmod.web.hud;

import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.host.WebHud;
import fr.lacaleche.glue.web.bridge.WebSlot;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The web HUD that replaces the hotbar and the status bars. The page lays out every bar; items are
 * native slot content drawn above the page. Vanilla returns for spectators and while riding a
 * jumping mount, whose jump bar the page does not draw.
 */
public record PlayerVitals(boolean survival, boolean hardcore, float health, float maxHealth, float absorption, int food,
                    float saturation, int armor, int air, int maxAir, boolean underwater, int level, float progress,
                    int selected, boolean offhand, Mount mount) {

    public static WebHud register(WebApp app) {
        return WebHud.builder(TestmodClient.id("web_vitals"), app.page("hud.html"))
                .replaces(VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR,
                        VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
                        VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL)
                .when(PlayerVitals::isSupported)
                .state("vitals", PlayerVitals::capture)
                .slot("item", PlayerVitals::renderItem)
                .register();
    }

    static PlayerVitals capture() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || client.level == null) return null;

        Mount mount = player.getVehicle() instanceof LivingEntity vehicle
                ? new Mount(half(vehicle.getHealth()), vehicle.getMaxHealth()) : null;
        return new PlayerVitals(client.gameMode.canHurtPlayer(), client.level.getLevelData().isHardcore(),
                half(player.getHealth()), player.getMaxHealth(), half(player.getAbsorptionAmount()),
                player.getFoodData().getFoodLevel(), Math.round(player.getFoodData().getSaturationLevel() * 10) / 10f,
                player.getArmorValue(), Math.max(0, player.getAirSupply()), player.getMaxAirSupply(),
                player.isEyeInFluid(FluidTags.WATER), player.experienceLevel,
                Math.round(player.experienceProgress * 100) / 100f, player.getInventory().getSelectedSlot(),
                !player.getOffhandItem().isEmpty(), mount);
    }

    private static boolean isSupported() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && !player.isSpectator() && player.jumpableVehicle() == null;
    }

    private static void renderItem(GuiGraphics graphics, WebSlot slot) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        ItemStack stack = stack(player, slot.argument());
        if (stack.isEmpty()) return;
        int x = slot.x() + (slot.width() - 16) / 2;
        int y = slot.y() + (slot.height() - 16) / 2;
        graphics.renderItem(player, stack, x, y, 0);
        graphics.renderItemDecorations(client.font, stack, x, y);
    }

    private static ItemStack stack(LocalPlayer player, String argument) {
        if (argument.equals("offhand")) return player.getOffhandItem();
        try {
            int index = Integer.parseInt(argument);
            return index >= 0 && index < Inventory.getSelectionSize() ? player.getInventory().getItem(index) : ItemStack.EMPTY;
        } catch (NumberFormatException exception) {
            return ItemStack.EMPTY;
        }
    }

    /** Health regenerates in small fractions; half points keep the page from updating every tick. */
    private static float half(float value) {
        return Math.round(value * 2) / 2f;
    }

    record Mount(float health, float maxHealth) {
    }
}

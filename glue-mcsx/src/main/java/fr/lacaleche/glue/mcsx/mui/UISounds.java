package fr.lacaleche.glue.mcsx.mui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class UISounds {

    private UISounds() {}

    @Environment(EnvType.CLIENT)
    public static void play(SoundEvent event) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1));
    }

    @Environment(EnvType.CLIENT)
    public static void playButtonSound() {
        play(SoundEvents.UI_BUTTON_CLICK.value());
    }

    @Environment(EnvType.CLIENT)
    public static void playClickSound() {
        play(SoundEvents.UI_BUTTON_CLICK.value());
    }
}

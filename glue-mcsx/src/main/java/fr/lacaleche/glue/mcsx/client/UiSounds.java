package fr.lacaleche.glue.mcsx.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

@Environment(EnvType.CLIENT)
public final class UiSounds {

    private UiSounds() {
    }

    public static void playClick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
    }
}

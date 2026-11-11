package fr.lacaleche.glue.client.mixin.options;

import fr.lacaleche.glue.client.options.GraphicsOptions;
import fr.lacaleche.glue.client.options.GraphicsOptionsPage;
import fr.lacaleche.glue.client.options.internal.GraphicsOptionsScreen;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Adds the button of each registered graphics options page at the foot of the video settings, two to a line. */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {

    private VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void glue$addGraphicsOptionsButtons(CallbackInfo callback) {
        List<GraphicsOptionsPage<?>> pages = GraphicsOptions.pages();
        for (int i = 0; i < pages.size(); i += 2) {
            Button left = GraphicsOptionsScreen.openButton(this, pages.get(i));
            Button right = i + 1 < pages.size() ? GraphicsOptionsScreen.openButton(this, pages.get(i + 1)) : null;
            this.list.addSmall(left, right);
        }
    }
}

package fr.lacaleche.glue.web.internal.mixin;

import fr.lacaleche.glue.web.internal.options.OptionsPage;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds Glue's settings entry to Minecraft's options screen. Vanilla arranges its own grid through a
 * layout the screen keeps, so the button is anchored to the bottom-left corner instead and moved
 * again whenever the window is resized.
 */
@Mixin(OptionsScreen.class)
abstract class OptionsScreenMixin extends Screen {

    private static final int MARGIN = 6;
    private static final int WIDTH = 98;
    private static final int HEIGHT = 20;

    @Unique
    private Button glueWeb$entry;

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void glueWeb$addEntry(CallbackInfo info) {
        this.glueWeb$entry = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.glue-web.options.title"), button -> OptionsPage.open())
                        .bounds(MARGIN, this.height - HEIGHT - MARGIN, WIDTH, HEIGHT)
                        .build());
    }

    @Inject(method = "repositionElements", at = @At("RETURN"))
    private void glueWeb$moveEntry(CallbackInfo info) {
        if (this.glueWeb$entry != null) this.glueWeb$entry.setPosition(MARGIN, this.height - HEIGHT - MARGIN);
    }
}

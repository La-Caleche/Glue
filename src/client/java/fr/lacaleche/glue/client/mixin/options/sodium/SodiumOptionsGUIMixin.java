package fr.lacaleche.glue.client.mixin.options.sodium;

import fr.lacaleche.glue.client.options.GraphicsOptions;
import fr.lacaleche.glue.client.options.GraphicsOptionsPage;
import fr.lacaleche.glue.client.options.internal.OptionsScreenScroll;
import fr.lacaleche.glue.client.options.internal.sodium.SodiumGraphicsOptions;
import fr.lacaleche.glue.client.options.internal.sodium.SodiumGraphicsPage;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Sodium replaces the video settings with its own screen, so each registered graphics options page joins it as a
 * page. Those pages lay out their own rows, under titled rules, and scroll with the wheel, which Sodium's do not.
 * Sodium's screen is not an API: an update that renames it or moves what this injects into fails the game at
 * launch. Not {@code @Pseudo}, which would skip a missing class silently; Glue's mixin plugin already skips this
 * mixin without Sodium.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI", remap = false)
public abstract class SodiumOptionsGUIMixin extends Screen implements OptionsScreenScroll.Scrollable {

    @Shadow
    @Final
    private List<OptionPage> pages;

    @Shadow
    @Final
    private List<ControlElement<?>> controls;

    @Shadow
    private OptionPage currentPage;

    @Unique
    private int glue$scroll;

    protected SodiumOptionsGUIMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void glue$addGraphicsOptionsPages(CallbackInfo callback) {
        for (GraphicsOptionsPage<?> page : GraphicsOptions.pages()) this.pages.add(SodiumGraphicsOptions.page(page));
    }

    @Inject(method = "setPage", at = @At("HEAD"))
    private void glue$scrollToTop(OptionPage page, CallbackInfo callback) {
        this.glue$scroll = 0;
    }

    @Inject(method = "rebuildGUIOptions", at = @At("HEAD"), cancellable = true)
    private void glue$layOutGraphicsOptionsPage(CallbackInfo callback) {
        if (!(this.currentPage instanceof SodiumGraphicsPage page)) return;
        callback.cancel();
        this.glue$scroll = Math.clamp(this.glue$scroll, 0, page.maxScroll(this.height));
        page.layOut(this.glue$scroll, this.height, this::rebuildWidgets, element -> {
            this.addRenderableWidget(element);
            this.controls.add(element);
        }, this::addRenderableOnly);
    }

    @Override
    public boolean glue$scrollOptions(double vertical) {
        if (!(this.currentPage instanceof SodiumGraphicsPage page) || vertical == 0) return false;
        int scroll = Math.clamp(this.glue$scroll - Math.round(vertical * SodiumGraphicsPage.scrollStep()), 0,
                page.maxScroll(this.height));
        if (scroll != this.glue$scroll) {
            this.glue$scroll = scroll;
            this.rebuildWidgets();
        }
        return true;
    }
}

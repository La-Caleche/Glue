package fr.lacaleche.glue.client.mixin.viewport.axiom;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.Window;
import fr.lacaleche.glue.client.viewport.internal.GameViewportStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Carries Axiom's screenless context menu through the same viewport transform as vanilla screens. */
@Pseudo
@Mixin(targets = "com.moulberry.axiom.ContextMenuManager", remap = false)
public abstract class AxiomContextMenuViewportMixin {

    @Shadow
    private int lastScreenWidth;
    @Shadow
    private int lastScreenHeight;
    @Shadow
    private Screen activeScreen;

    @WrapMethod(method = "render")
    private void glue$renderInViewport(GuiGraphics graphics, int screenWidth, int screenHeight,
                                       float partialTick, Operation<Void> original) {
        if (!GameViewportStage.confinesGui()) {
            original.call(graphics, screenWidth, screenHeight, partialTick);
            return;
        }

        boolean enteredScope = !GameViewportStage.isGuiScoped();
        if (enteredScope) GameViewportStage.beginScreen(graphics);
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Window window = minecraft.getWindow();
            int width = GameViewportStage.effectiveGuiWidth(window);
            int height = GameViewportStage.effectiveGuiHeight(window);
            if (this.activeScreen != null) {
                if (this.lastScreenWidth != width || this.lastScreenHeight != height) {
                    this.activeScreen.resize(minecraft, width, height);
                }

                int mouseX = (int) minecraft.mouseHandler.getScaledXPos(window);
                int mouseY = (int) minecraft.mouseHandler.getScaledYPos(window);
                this.activeScreen.renderWithTooltip(graphics, mouseX, mouseY, partialTick);
            }

            this.lastScreenWidth = width;
            this.lastScreenHeight = height;
        } finally {
            if (enteredScope) GameViewportStage.endScreen(graphics);
        }
    }

    @WrapMethod(method = "mouseMoved")
    private void glue$moveInViewport(double mouseX, double mouseY, Operation<Void> original) {
        if (!GameViewportStage.confinesGui()) {
            original.call(mouseX, mouseY);
            return;
        }

        original.call(glue$mouseX(), glue$mouseY());
    }

    @WrapMethod(method = "mouseClicked")
    private void glue$clickInViewport(double mouseX, double mouseY, int button,
                                      Operation<Void> original) {
        if (!GameViewportStage.confinesGui()) {
            original.call(mouseX, mouseY, button);
            return;
        }

        original.call(glue$mouseX(), glue$mouseY(), button);
    }

    @WrapMethod(method = "mouseReleased")
    private void glue$releaseInViewport(double mouseX, double mouseY, int button,
                                        Operation<Void> original) {
        if (!GameViewportStage.confinesGui()) {
            original.call(mouseX, mouseY, button);
            return;
        }

        original.call(glue$mouseX(), glue$mouseY(), button);
    }

    @WrapMethod(method = "mouseDragged")
    private void glue$dragInViewport(double mouseX, double mouseY, int button,
                                     double deltaX, double deltaY, Operation<Void> original) {
        if (!GameViewportStage.confinesGui()) {
            original.call(mouseX, mouseY, button, deltaX, deltaY);
            return;
        }

        original.call(glue$mouseX(), glue$mouseY(), button,
                glue$deltaX(deltaX), glue$deltaY(deltaY));
    }

    @WrapMethod(method = "mouseScrolled")
    private boolean glue$scrollInViewport(double mouseX, double mouseY,
                                          double scrollX, double scrollY,
                                          Operation<Boolean> original) {
        if (!GameViewportStage.confinesGui()) {
            return original.call(mouseX, mouseY, scrollX, scrollY);
        }

        return original.call(glue$mouseX(), glue$mouseY(), scrollX, scrollY);
    }

    @Unique
    private static double glue$mouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
    }

    @Unique
    private static double glue$mouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
    }

    @Unique
    private static double glue$deltaX(double axiomDelta) {
        Window window = Minecraft.getInstance().getWindow();
        double rawDelta = axiomDelta * window.getScreenWidth() / window.getGuiScaledWidth();
        return GameViewportStage.viewportGuiDeltaX(window, rawDelta);
    }

    @Unique
    private static double glue$deltaY(double axiomDelta) {
        Window window = Minecraft.getInstance().getWindow();
        double rawDelta = axiomDelta * window.getScreenHeight() / window.getGuiScaledHeight();
        return GameViewportStage.viewportGuiDeltaY(window, rawDelta);
    }
}

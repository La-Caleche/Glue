package fr.lacaleche.glue.testmod.ui;

import com.mojang.blaze3d.opengl.GlTexture;
import fr.lacaleche.glue.client.debug.DeveloperMenu;
import fr.lacaleche.glue.client.debug.FboDebugHud;
import fr.lacaleche.glue.client.ui.UiPage;
import fr.lacaleche.glue.client.ui.UiPageBuilder;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The showcase's page in Glue's developer menu, the example for a mod adding its own: F8 lists it
 * under the showcase's name. It also lists a texture on the menu's Framebuffers page.
 */
public final class ShowcaseDeveloperPage implements UiPage {

    public static final String TEXTURE_NAME = "Showcase main colour";

    /** Registers the page and the texture; called during client initialisation. */
    public static void register() {
        DeveloperMenu.register(TestmodClient.id("showcase"), true, ShowcaseDeveloperPage::new);
        FboDebugHud.registerTexture(TEXTURE_NAME, ShowcaseDeveloperPage::mainColour);
    }

    @Override
    public Component title() {
        return Component.literal("Showcase");
    }

    @Override
    public void build(UiPageBuilder builder) {
        TestPostShaderHandler effects = TestPostShaderHandler.INSTANCE;
        builder.section(Component.literal("Effects"));
        builder.toggle(Component.literal("Blur"), Component.literal("The showcase's switched post effect."),
                () -> effects.isToggled(TestShaders.BLUR), value -> {
                    if (value != effects.isToggled(TestShaders.BLUR)) effects.toggleByHandle(TestShaders.BLUR);
                });
        builder.section(Component.literal("Pointer"));
        builder.label(Component.literal("Block"), Component.literal("The block under the crosshair."),
                ShowcaseDeveloperPage::target);
    }

    private static Component target() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS
                || minecraft.level == null) {
            return Component.literal("None");
        }

        return minecraft.level.getBlockState(hit.getBlockPos()).getBlock().getName();
    }

    private static int mainColour() {
        return Minecraft.getInstance().getMainRenderTarget().getColorTexture() instanceof GlTexture texture
                ? texture.glId() : 0;
    }
}

package fr.lacaleche.glue.testmod.ui;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import fr.lacaleche.glue.client.debug.DeveloperMenu;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.client.ui.UiPage;
import fr.lacaleche.glue.client.ui.UiPageBuilder;
import fr.lacaleche.glue.client.ui.UiPanelScreen;
import fr.lacaleche.glue.client.ui.UiScreen;
import fr.lacaleche.glue.client.ui.UiTextureView;
import fr.lacaleche.glue.math.Color;
import fr.lacaleche.glue.testmod.Testmod;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import fr.lacaleche.glue.testmod.scene.SceneDemos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * {@code /showcase}: the showcase's demos as pages of a {@link UiScreen}, then every kit widget on one
 * page, which also opens as a {@link UiPanelScreen}, and a borrowed texture on another.
 */
public final class ShowcaseMenu {

    private static final Component NEEDS_WORLD = Component.literal("Join a world to use this page.");

    private static boolean outlines = true;
    private static Quality quality = Quality.MEDIUM;
    private static double radius = 8;
    private static double opacity = 0.75;
    private static double height = 64;
    private static String name = "Lantern";
    private static int presses;

    private ShowcaseMenu() {
    }

    public static void open() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new UiScreen(Component.literal("Glue Showcase"), client.screen, List.of(
                new UiScreen.Group(Component.literal("Demos"), List.of(new ScenesPage(), new EffectsPage(),
                        new DebugPage())),
                new UiScreen.Group(Component.literal("UI kit"), List.of(new WidgetsPage(), new TexturePage())))));
    }

    private static @Nullable Component needsWorld() {
        return Minecraft.getInstance().level == null ? NEEDS_WORLD : null;
    }

    private enum Quality {
        LOW, MEDIUM, HIGH;

        private Component title() {
            return Component.literal(this.name().charAt(0) + this.name().substring(1).toLowerCase(Locale.ROOT));
        }
    }

    private static final class ScenesPage implements UiPage {

        @Override
        public Component title() {
            return Component.literal("Scenes");
        }

        @Override
        public @Nullable Component unavailableReason() {
            return needsWorld();
        }

        @Override
        public void build(UiPageBuilder builder) {
            builder.section(Component.literal("Previews of the blocks around you"));
            builder.button(Component.literal("Orbit"), Component.literal("Orbit, pan and zoom; the region is "
                    + "resized from its panel."), Component.literal("Open"), SceneDemos::openOrbit);
            builder.button(Component.literal("Free flight"), Component.literal("A camera that flies through the "
                    + "preview and its entities while the player stays put."), Component.literal("Open"),
                    SceneDemos::openFps);
            builder.button(Component.literal("Gizmo"), Component.literal("Pick a block, move, rotate and scale it, "
                    + "and undo. The world is never changed."), Component.literal("Open"), SceneDemos::openGizmo);
        }
    }

    private static final class EffectsPage implements UiPage {

        @Override
        public Component title() {
            return Component.literal("Effects");
        }

        @Override
        public @Nullable Component unavailableReason() {
            return needsWorld();
        }

        @Override
        public void build(UiPageBuilder builder) {
            TestPostShaderHandler effects = TestPostShaderHandler.INSTANCE;
            builder.section(Component.literal("Switched"));
            toggle(builder, "Blur", "A post effect that stays on until switched off.", TestShaders.BLUR);
            toggle(builder, "Grayscale", "Another switched post effect, stacked after the blur.",
                    TestShaders.GRAYSCALE);

            builder.section(Component.literal("Timed, built in code"));
            play(builder, "Chromatic", "Splits the colours for a moment.", TestPostShaderHandler.CHROMATIC::trigger);
            play(builder, "Shattered", "Breaks the screen, then fades.", TestPostShaderHandler.SHATTERED::trigger);
            play(builder, "Impact", "A single impact frame.", TestPostShaderHandler.IMPACT::trigger);

            builder.section(Component.literal("Timed, loaded from data"));
            play(builder, "Chromatic", "The same split, declared in a JSON file.",
                    () -> effects.triggerFromRegistry(Testmod.id("chromatic")));
            play(builder, "Vortex", "A departure vortex, declared in a JSON file.",
                    () -> effects.triggerFromRegistry(Testmod.id("departure_vortex")));
            play(builder, "Pulse", "A denial pulse, declared in a JSON file.",
                    () -> effects.triggerFromRegistry(Testmod.id("denial_pulse")));
        }

        private static void toggle(UiPageBuilder builder, String label, String description, PostShaderHandle handle) {
            TestPostShaderHandler effects = TestPostShaderHandler.INSTANCE;
            builder.toggle(Component.literal(label), Component.literal(description), () -> effects.isToggled(handle),
                    value -> {
                        if (value != effects.isToggled(handle)) effects.toggleByHandle(handle);
                    });
        }

        private static void play(UiPageBuilder builder, String label, String description, Runnable effect) {
            builder.button(Component.literal(label), Component.literal(description), Component.literal("Play"),
                    effect);
        }
    }

    private static final class DebugPage implements UiPage {

        @Override
        public Component title() {
            return Component.literal("Debug");
        }

        @Override
        public @Nullable Component unavailableReason() {
            return needsWorld();
        }

        @Override
        public void build(UiPageBuilder builder) {
            builder.section(Component.literal("Glue's developer menu"));
            builder.button(Component.literal("Developer menu"), Component.literal("Glue's own tools, also on F8: "
                    + "its Raycast page shows how the pick crosses composite cells and switches an overlay of it, "
                    + "and its Framebuffers page shows the render targets."), Component.literal("Open"),
                    DeveloperMenu::open);
        }
    }

    private static final class WidgetsPage implements UiPage {

        @Override
        public Component title() {
            return Component.literal("Widgets");
        }

        @Override
        public void build(UiPageBuilder builder) {
            builder.section(Component.literal("Values"));
            builder.toggle(Component.literal("Outlines"), Component.literal("A boolean, read each frame."),
                    () -> outlines, value -> outlines = value);
            builder.cycle(Component.literal("Quality"), Component.literal("One of an enum; Shift steps back."),
                    List.of(Quality.values()), Quality::title, () -> quality, value -> quality = value);
            builder.slider(Component.literal("Radius"), Component.literal("Whole blocks, from 1 to 16."),
                    1, 16, 1, () -> radius, value -> radius = value);
            builder.slider(Component.literal("Opacity"), Component.literal("From 0 to 1 by 0.05, shown as a percentage."),
                    0, 1, 0.05, () -> opacity, value -> opacity = value)
                    .setFormat(value -> Component.literal(Math.round(value * 100) + "%"));
            builder.number(Component.literal("Height"), Component.literal("Any number by 0.5: drag it sideways, "
                    + "scroll it while focused, or click it to type; Shift for a tenth, Control for ten steps."),
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.5, () -> height, value -> height = value)
                    .setFormat(value -> Component.literal(value + " m"));
            builder.textField(Component.literal("Name"), Component.literal("Free text."),
                    () -> name, value -> name = value);

            builder.section(Component.literal("Read only"));
            builder.label(Component.literal("Presses"), null, () -> Component.literal(Integer.toString(presses)));
            builder.color(Component.literal("Tint"), Component.literal("Follows the radius and opacity."),
                    () -> Color.ofTransparent((int) Math.round(opacity * 255) << 24
                            | Color.hsbToRgb((float) (radius / 16.0), 0.6F, 1.0F) & 0xFFFFFF));
            builder.button(Component.literal("Action"), Component.literal("Runs an action."),
                    Component.literal("Press"), () -> presses++);

            builder.section(Component.literal("Panel"));
            builder.button(Component.literal("Panel"), Component.literal("These widgets in a panel over the world, "
                    + "which keeps running: the pointer is free and the game is not paused."), Component.literal("Open"),
                    () -> Minecraft.getInstance().setScreen(new UiPanelScreen(this.title(), new WidgetsPage())));
        }
    }

    private static final class TexturePage implements UiPage {

        private static final ResourceLocation BLOCK_ATLAS =
                ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");

        @Override
        public Component title() {
            return Component.literal("Texture");
        }

        @Override
        public void build(UiPageBuilder builder) {
            ScreenRectangle area = builder.area();
            int padding = 6;
            UiTextureView view = builder.add(new UiTextureView(Component.literal("Block atlas"), TexturePage::atlas));
            view.setRectangle(area.width() - 2 * padding, area.height() - 2 * padding,
                    area.left() + padding, area.top() + padding);
        }

        private static @Nullable UiTextureView.Texture atlas() {
            GpuTexture texture = Minecraft.getInstance().getTextureManager().getTexture(BLOCK_ATLAS)
                    .getTexture();
            if (!(texture instanceof GlTexture gl)) return null;

            return new UiTextureView.Texture(gl.glId(), texture.getWidth(0), texture.getHeight(0), false);
        }
    }
}

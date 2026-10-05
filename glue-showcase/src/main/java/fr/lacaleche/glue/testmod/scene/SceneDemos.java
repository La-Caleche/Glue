package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.client.ui.UiPanelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

import java.util.function.Function;

/** Client-thread entry points behind {@code /showcase scene}. */
public final class SceneDemos {

    private SceneDemos() {
    }

    public static BlockSceneTestScreen openOrbit() {
        return open(BlockSceneTestScreen::new);
    }

    public static FpsViewportTestScreen openFps() {
        return open(FpsViewportTestScreen::new);
    }

    public static GizmoTestScreen openGizmo() {
        return open(GizmoTestScreen::new);
    }

    public static UiPanelScreen openWorldGizmo() {
        return open(parent -> {
            LocalPlayer player = Minecraft.getInstance().player;
            return WorldGizmoDemo.screen(player.getEyePosition(), player.getLookAngle());
        });
    }

    private static <S extends Screen> S open(Function<Screen, S> factory) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) throw new IllegalStateException("Join a world to open a scene preview");
        S screen = factory.apply(client.screen);
        client.setScreen(screen);
        return screen;
    }
}

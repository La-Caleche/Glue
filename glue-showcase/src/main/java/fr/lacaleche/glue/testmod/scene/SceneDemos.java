package fr.lacaleche.glue.testmod.scene;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Function;

/** Client-thread entry points shared by the showcase commands and the F6 hub. */
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

    private static <S extends Screen> S open(Function<Screen, S> factory) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) throw new IllegalStateException("Join a world to open a scene preview");
        S screen = factory.apply(client.screen);
        client.setScreen(screen);
        return screen;
    }
}

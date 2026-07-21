package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.DocumentLoader;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxDocument;
import fr.lacaleche.glue.mcsx.view.ClasspathDocumentResolver;
import fr.lacaleche.glue.mcsx.view.ComponentRegistry;
import fr.lacaleche.glue.mcsx.view.dock.DockConfig;
import fr.lacaleche.glue.mcsx.view.dock.DockPane;
import fr.lacaleche.glue.mcsx.view.dock.McsxDockspace;
import icyllis.modernui.core.Core;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * The showcase's debug workspace (F12): an MCSX dockspace with the game embedded in the center
 * pane and the Lights list, its Properties editor, and the Post FX inspector docked around it —
 * replacing the hand-drawn GLFW/GuiGraphics debug HUDs with the UI library Glue now ships.
 *
 * <p>The tick pump is the threading seam: each client tick gathers plain snapshots where the data
 * lives (Lumos, the effect handlers), then applies them to the panes' signals on the UI thread —
 * the reactive runtime is single-threaded by contract. Pane actions hop the opposite way.</p>
 */
@Environment(EnvType.CLIENT)
public final class GlueDebugDock {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-test/debug-dock");

    /** Whether the currently open dockspace is ours (the MCSX demo dock uses the same host). */
    private static volatile boolean open;
    /** Bumped per open; a close callback carrying an older generation raced a reopen. */
    private static volatile int generation;

    private GlueDebugDock() {
    }

    public static void toggle() {
        McsxDockspace current = McsxDockspace.current();
        if (current != null) {
            boolean ours = open;
            current.close();
            if (ours) return;
        }
        // One controller behind both light panes: the list drives the selection the editor edits.
        Supplier<ScreenController> lights = sharedLightsController();
        int gen = ++generation;
        try {
            McsxDockspace.open(DockConfig.builder("glue_debug")
                    .pane(pane("lights", "Lights", "sun", lights))
                    .pane(pane("properties", "Properties", "sliders", lights))
                    .pane(pane("effects", "Post FX", "palette", PostEffectsPaneController::new))
                    .defaultLayoutAsset("mcsx:glue_debug")
                    .onClose(() -> closed(gen))
                    .build());
            open = true;
        } catch (RuntimeException e) {
            LOGGER.error("Failed to open the debug dockspace", e);
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.literal("Debug dock failed: " + e.getMessage()), false);
            }
        }
    }

    /** Client tick: gather on this thread, write signals on the UI thread. */
    public static void tick() {
        if (!open || Core.getUiHandlerAsync() == null) return;
        LightsPaneController.Snapshot lights = LightsPaneController.snapshot();
        List<PostEffectsPaneController.EffectRow> effects = PostEffectsPaneController.snapshotRows();
        Core.postOnUiThread(() -> {
            LightsPaneController lightsPane = LightsPaneController.active();
            if (lightsPane != null) lightsPane.applySnapshot(lights);
            PostEffectsPaneController effectsPane = PostEffectsPaneController.active();
            if (effectsPane != null) effectsPane.applySnapshot(effects);
        });
    }

    /**
     * The dockspace posts its disposal to the UI thread, so after a quick close-reopen this
     * callback arrives once the next dockspace is already up — a stale generation must not kill
     * the new one's pump.
     */
    private static void closed(int gen) {
        if (gen != generation) return;
        open = false;
        LightsPaneController.deactivate();
        PostEffectsPaneController.deactivate();
    }

    /**
     * Memoizes the controller so the Lights and Properties panes bind the same instance; panes
     * bind lazily on the UI thread, which is where the controller's effects must be created.
     */
    private static Supplier<ScreenController> sharedLightsController() {
        return new Supplier<>() {
            private LightsPaneController instance;

            @Override
            public ScreenController get() {
                if (instance == null) instance = new LightsPaneController();
                return instance;
            }
        };
    }

    private static DockPane pane(String id, String title, String icon,
                                 Supplier<ScreenController> controller) {
        McsxDocument document = DocumentLoader.loadFromClasspath("mcsx:debug/" + id);
        return DockPane.ofDocument(id, title, icon, document, controller,
                new ComponentRegistry(), new ClasspathDocumentResolver());
    }
}

package fr.lacaleche.glue.testmod.mcsx;

import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.DocumentLoader;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxDocument;
import fr.lacaleche.glue.mcsx.mui.MuiModApi;
import fr.lacaleche.glue.mcsx.surface.ExternalSurfaceView;
import fr.lacaleche.glue.mcsx.view.ClasspathDocumentResolver;
import fr.lacaleche.glue.mcsx.view.ComponentRegistry;
import fr.lacaleche.glue.mcsx.view.McsxFragment;
import fr.lacaleche.glue.mcsx.view.ViewBinder;
import fr.lacaleche.glue.mcsx.view.dock.DockConfig;
import fr.lacaleche.glue.mcsx.view.dock.DockPane;
import fr.lacaleche.glue.mcsx.view.dock.McsxDockspace;
import fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding;
import fr.lacaleche.glue.mcsx.viewport.ViewportInput;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

/**
 * Demonstrates MCSX: registers {@code /mcsx ui}, which builds a {@link McsxFragment} from
 * {@code mcsx:demo} + {@link DemoController} and opens it through MCSX's own self-hosted ModernUI
 * runtime — proving the whole stack renders standalone.
 */
public final class McsxDemos {

    public static final Logger LOGGER = LoggerFactory.getLogger("glue-test/mcsx");

    private McsxDemos() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("mcsx")
                        .then(ClientCommandManager.literal("ui")
                                .then(argument("file", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            Minecraft.getInstance().schedule(() -> openDemo(StringArgumentType.getString(context, "file")));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(ClientCommandManager.literal("inspect")
                                .then(argument("file", StringArgumentType.string())
                                        .executes(context -> {
                                            Minecraft.getInstance().schedule(() -> openDemo(
                                                    StringArgumentType.getString(context, "file"), true));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("viewport")
                                .executes(context -> {
                                    Minecraft.getInstance().schedule(McsxDemos::toggleViewport);
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(ClientCommandManager.literal("dockinspect")
                                .executes(context -> {
                                    Minecraft.getInstance().schedule(() -> toggleDock(true));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("dock")
                                .executes(context -> {
                                    Minecraft.getInstance().schedule(McsxDemos::toggleDock);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(ClientCommandManager.literal("reset")
                                        .executes(context -> {
                                            Minecraft.getInstance().schedule(McsxDemos::resetDockLayout);
                                            return Command.SINGLE_SUCCESS;
                                        })))));
    }

    /**
     * The controller each demo screen binds against. {@code @UIController} is not read by the runtime
     * — nothing scans for it — so the mapping is explicit here rather than implied by the annotation.
     */
    private static final Map<String, Supplier<ScreenController>> CONTROLLERS = Map.of(
            "glass", GlassController::new,
            "editor", EditorController::new,
            "theme_stress", ThemeStressController::new);

    /**
     * The consumer's native components. A {@code <surface>} tag builds an {@link ExternalSurfaceView}
     * over a fresh {@link DemoSurfaceSource} — this is how a mod plugs a live viewport into markup
     * without MCSX ever knowing about Blaze3D.
     */
    private static final ComponentRegistry COMPONENTS = new ComponentRegistry()
            .register("surface", (context, element, binder) -> {
                DemoSurfaceSource source = new DemoSurfaceSource();
                ExternalSurfaceView view = new ExternalSurfaceView(context, source);
                view.setGestureListener(source);
                return view;
            });

    /**
     * Bare-metal proof of the game-embedding pipeline, with no dock UI in the loop: pins the game
     * into a fixed centered pane at 60% of the window. The game stays captured (ALWAYS mode), so
     * it plays normally inside the pane; Escape releases the cursor (re-centered into the pane),
     * and running the command again restores the full window.
     */
    private static void toggleViewport() {
        if (McsxDockspace.current() != null) {
            LOGGER.warn("Close the dockspace before toggling the bare viewport demo");
            return;
        }
        if (ViewportEmbedding.isActive()) {
            ViewportEmbedding.deactivate();
            ViewportInput.end();
            LOGGER.info("Viewport embedding OFF");
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        int screenW = window.getScreenWidth();
        int screenH = window.getScreenHeight();
        int paneW = screenW * 3 / 5;
        int paneH = screenH * 3 / 5;
        ViewportEmbedding.activate();
        ViewportEmbedding.setPaneBounds((screenW - paneW) / 2, (screenH - paneH) / 2,
                paneW, paneH);
        ViewportInput.begin(ViewportInput.Mode.ALWAYS, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        LOGGER.info("Viewport embedding ON ({}x{} pane)", paneW, paneH);
    }

    /**
     * The dockspace demo: the game embedded in the central viewport pane, the four mock editor
     * panes docked around it (layout from {@code assets/mcsx/dock/default.json}, mutations
     * persisted to {@code config/mcsx/dock/demo.json}). Run again to close; {@code reset}
     * restores the default layout.
     */
    private static void toggleDock() {
        toggleDock(false);
    }

    private static void toggleDock(boolean inspect) {
        McsxDockspace open = McsxDockspace.current();
        if (open != null) {
            open.close();
            return;
        }
        try {
            McsxDockspace.open(DockConfig.builder("demo")
                    .pane(dockPane("hierarchy", "Hierarchy", "layout"))
                    .pane(dockPane("inspector", "Inspector", "sliders"))
                    .pane(dockPane("console", "Console", "terminal"))
                    .pane(dockPane("profiler", "Profiler", "info"))
                    .defaultLayoutAsset("mcsx:default")
                    .build(), inspect);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to open the dockspace demo", e);
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.literal("Dockspace failed: " + e.getMessage()), false);
            }
        }
    }

    private static void resetDockLayout() {
        McsxDockspace open = McsxDockspace.current();
        if (open != null) {
            open.resetLayout();
        }
    }

    private static DockPane dockPane(String id, String title, String icon) {
        McsxDocument document = DocumentLoader.loadFromClasspath("mcsx:dock/" + id);
        return DockPane.ofDocument(id, title, icon, document, DockDemoController::new,
                COMPONENTS, new ClasspathDocumentResolver());
    }

    private static void openDemo(String file) {
        openDemo(file, false);
    }

    private static void openDemo(String file, boolean inspect) {
        try {
            McsxDocument document = DocumentLoader.loadFromClasspath("mcsx:" + file);
            ViewBinder.DocumentResolver resolver = new ClasspathDocumentResolver();
            ScreenController controller =
                    CONTROLLERS.getOrDefault(file, DemoController::new).get();
            McsxFragment fragment =
                    new McsxFragment(document, controller, COMPONENTS, resolver, inspect);
            MuiModApi.openScreen(fragment);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to open the MCSX demo", e);
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.literal("MCSX demo failed: " + e.getMessage()), false);
            }
        }
    }
}

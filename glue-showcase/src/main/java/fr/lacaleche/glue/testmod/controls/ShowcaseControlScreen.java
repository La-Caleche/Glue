package fr.lacaleche.glue.testmod.controls;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.UiOverlay;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.file.FileDialogTestScreen;
import fr.lacaleche.glue.testmod.lumos.DemoLights;
import fr.lacaleche.glue.testmod.mcsx.ShowcaseUiScreen;
import fr.lacaleche.glue.testmod.mcsx.expedition.ExpeditionDemo;
import fr.lacaleche.glue.testmod.mcsx.playground.ModernUiDemo;
import fr.lacaleche.glue.testmod.mcsx.studio.GlueStudio;
import fr.lacaleche.glue.testmod.jcef.JcefDemo;
import fr.lacaleche.glue.testmod.registries.TestKeybinds;
import fr.lacaleche.glue.testmod.scene.BlockSceneTestScreen;
import fr.lacaleche.glue.testmod.scene.FpsViewportTestScreen;
import fr.lacaleche.glue.testmod.scene.GizmoTestScreen;
import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** MCSX entry point for the showcase's screens, workspaces, and live light actions. */
public final class ShowcaseControlScreen extends ShowcaseUiScreen {

    public static final String TAG_ROOT = "showcase.controls";
    public static final String TAG_STATUS = "showcase.controls.status";
    public static final String TAG_FPS = "showcase.controls.fps";
    public static final String TAG_ORBIT = "showcase.controls.orbit";
    public static final String TAG_GIZMO = "showcase.controls.gizmo";
    public static final String TAG_FILES = "showcase.controls.files";
    public static final String TAG_CLOSE = "showcase.controls.close";
    public static final String TAG_OPEN_KEY = "showcase.controls.key.open";
    public static final String TAG_RAYCAST_KEY = "showcase.controls.key.raycast";

    private static GlueStudio activeStudio;

    private final Signal<String> status = Signal.of(translated("showcase.controls.status.ready"));

    public static ShowcaseControlScreen open(Minecraft client) {
        GlueStudio studio = activeStudio;
        if (studio != null) {
            if (studio.dockspace().isOpen()) studio.dockspace().close();
            activeStudio = null;
        }
        ShowcaseControlScreen screen = new ShowcaseControlScreen();
        screen.show(client.screen);
        return screen;
    }

    @Override
    protected View create(Ui ui) {
        return ui.screen(
                Signal.of(Themes.mcsx()),
                Stylesheets.resource(TestmodClient.id("showcase-controls")),
                ui.card(
                        ui.column(
                                ui.heading("showcase.controls.title"),
                                ui.copy("showcase.controls.subtitle"),
                                ui.row(
                                        this.keybind(
                                                ui,
                                                TestKeybinds.openShowcaseKey(),
                                                TAG_OPEN_KEY,
                                                "showcase.controls.keybind.open"
                                        ),
                                        this.keybind(
                                                ui,
                                                TestKeybinds.toggleRaycastDebugKey(),
                                                TAG_RAYCAST_KEY,
                                                "showcase.controls.keybind.raycast"
                                        )
                                ).classes("control-keybinds")
                        ).classes("control-header"),
                        ui.column(
                                ui.row(
                                        ui.section(
                                                "showcase.controls.scenes",
                                                ui.secondaryButton("showcase.controls.fps", this::openFps).tag(TAG_FPS),
                                                ui.secondaryButton("showcase.controls.orbit", this::openOrbit).tag(TAG_ORBIT),
                                                ui.secondaryButton("showcase.controls.gizmo", this::openGizmo).tag(TAG_GIZMO)
                                        ).classes("control-section", "scenes"),
                                        ui.section(
                                                "showcase.controls.tools",
                                                ui.secondaryButton("showcase.controls.studio", this::openStudio),
                                                ui.secondaryButton("showcase.controls.playground", this::openPlayground),
                                                ui.secondaryButton("showcase.controls.expedition", this::openExpedition),
                                                ui.literalButton("JCEF / Chromium experiment", () -> Minecraft.getInstance().schedule(() -> JcefDemo.open(Minecraft.getInstance(), false))),
                                                ui.literalButton("JCEF browser / La Calèche", () -> Minecraft.getInstance().schedule(() -> JcefDemo.open(Minecraft.getInstance(), true))),
                                                ui.secondaryButton("showcase.controls.files", this::openFileDialogs)
                                                        .tag(TAG_FILES)
                                        ).classes("control-section", "tools")
                                ).classes("control-columns"),
                                ui.section(
                                        "showcase.controls.lighting",
                                        ui.actions(
                                                ui.secondaryButton("showcase.controls.flashlight", this::toggleFlashlight),
                                                ui.secondaryButton("showcase.controls.stress", this::toggleStressRing),
                                                ui.secondaryButton("showcase.controls.spot", this::spawnSpot)
                                        )
                                ).classes("lighting-section")
                        ).classes("control-body"),
                        ui.row(
                                ui.text(this.status).tag(TAG_STATUS).classes("control-status"),
                                ui.quietButton("showcase.controls.close", this::back).tag(TAG_CLOSE)
                        ).classes("control-footer")
                ).classes("control-card")
        ).tag(TAG_ROOT).classes("showcase-controls");
    }

    private void openFps() {
        this.openVanillaScreen(FpsViewportTestScreen::new);
    }

    private void openOrbit() {
        this.openVanillaScreen(BlockSceneTestScreen::new);
    }

    private void openGizmo() {
        this.openVanillaScreen(GizmoTestScreen::new);
    }

    private void openPlayground() {
        this.openShowcaseScreen(ModernUiDemo::new);
    }

    private void openFileDialogs() {
        this.openShowcaseScreen(FileDialogTestScreen::new);
    }

    private void openShowcaseScreen(Supplier<? extends ShowcaseUiScreen> factory) {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> factory.get().show(client.screen));
    }

    private void openVanillaScreen(Supplier<? extends Screen> factory) {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> client.setScreen(factory.get()));
    }

    private void openStudio() {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> {
            if (UiOverlay.isOccupied()) {
                this.publishStatus("showcase.controls.status.overlay_busy");
                return;
            }

            GlueStudio studio = new GlueStudio(true);
            activeStudio = studio;
            try {
                studio.open();
                client.setScreen(null);
            } catch (RuntimeException | Error failure) {
                if (activeStudio == studio) activeStudio = null;
                throw failure;
            }
        });
    }

    private void openExpedition() {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> {
            if (UiOverlay.isOccupied() && !ExpeditionDemo.INSTANCE.hud().isMounted()) {
                this.publishStatus("showcase.controls.status.overlay_busy");
                return;
            }
            ExpeditionDemo.INSTANCE.openPlanner(client);
        });
    }

    private void toggleFlashlight() {
        Minecraft.getInstance().schedule(() -> {
            DemoLights.INSTANCE.toggleFlashlight();
            this.publishStatus(DemoLights.INSTANCE.isFlashlightOn()
                    ? "showcase.controls.status.flashlight_on"
                    : "showcase.controls.status.flashlight_off");
        });
    }

    private void toggleStressRing() {
        Minecraft.getInstance().schedule(() -> {
            DemoLights.INSTANCE.toggleStaticLights();
            this.publishStatus(DemoLights.INSTANCE.isStressRingEnabled()
                    ? "showcase.controls.status.stress_on"
                    : "showcase.controls.status.stress_off");
        });
    }

    private void spawnSpot() {
        Minecraft.getInstance().schedule(() -> {
            DemoLights.INSTANCE.spawnSpot();
            this.publishStatus("showcase.controls.status.spot");
        });
    }

    private void publishStatus(String translationKey) {
        this.status.postSet(translated(translationKey));
    }

    private View keybind(Ui ui, Component key, String tag, String descriptionKey) {
        return ui.row(
                ui.column(ui.text(key).tag(tag).classes("control-keybind-key"))
                        .classes("control-keybind-keycap"),
                ui.text(descriptionKey).classes("control-keybind-label")
        ).classes("control-keybind");
    }

    private static String translated(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }
}

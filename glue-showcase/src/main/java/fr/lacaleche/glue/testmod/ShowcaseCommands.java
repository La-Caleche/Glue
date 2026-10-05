package fr.lacaleche.glue.testmod;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import fr.lacaleche.glue.testmod.scene.SceneDemos;
import fr.lacaleche.glue.testmod.ui.ShowcaseMenu;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Client-thread controls for post effects and scene previews; {@code /showcase} alone opens the menu. */
final class ShowcaseCommands {

    private ShowcaseCommands() {
    }

    static void register() {
        TestPostShaderHandler effects = TestPostShaderHandler.INSTANCE;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("showcase")
                        .executes(context -> {
                            Minecraft.getInstance().schedule(ShowcaseMenu::open);
                            return 1;
                        })
                        .then(literal("scene")
                                .then(scene("orbit", SceneDemos::openOrbit))
                                .then(scene("fps", SceneDemos::openFps))
                                .then(scene("gizmo", SceneDemos::openGizmo))
                                .then(scene("world-gizmo", SceneDemos::openWorldGizmo)))
                        .then(literal("effects")
                                .then(action("blur", () -> effects.toggleByHandle(TestShaders.BLUR)))
                                .then(action("grayscale", () -> effects.toggleByHandle(TestShaders.GRAYSCALE)))
                                .then(action("chromatic", TestPostShaderHandler.CHROMATIC::trigger))
                                .then(action("shattered", TestPostShaderHandler.SHATTERED::trigger))
                                .then(action("impact", TestPostShaderHandler.IMPACT::trigger))
                                .then(action("chromatic-registry", () -> effects.triggerFromRegistry(Testmod.id("chromatic"))))
                                .then(action("vortex", () -> effects.triggerFromRegistry(Testmod.id("departure_vortex"))))
                                .then(action("pulse", () -> effects.triggerFromRegistry(Testmod.id("denial_pulse")))))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> action(String name, Runnable action) {
        return literal(name).executes(context -> {
            action.run();
            return 1;
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> scene(String name, Runnable open) {
        // Chat closes after command dispatch; open the scene on the next client task instead.
        return action(name, () -> Minecraft.getInstance().schedule(open));
    }
}

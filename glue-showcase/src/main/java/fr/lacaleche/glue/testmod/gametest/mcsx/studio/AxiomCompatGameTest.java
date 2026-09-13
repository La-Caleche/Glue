package fr.lacaleche.glue.testmod.gametest.mcsx.studio;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lacaleche.glue.client.viewport.GameViewport;
import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.mcsx.client.GameFocus;
import fr.lacaleche.glue.mcsx.client.dock.Dockspace;
import fr.lacaleche.glue.testmod.gametest.mcsx.RealInput;
import fr.lacaleche.glue.testmod.mcsx.studio.GlueStudio;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.require;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.requireEquals;

/** Exercises MCSX's optional Axiom editor and screenless context-menu integration. */
public final class AxiomCompatGameTest {

    private AxiomCompatGameTest() {
    }

    public static void register() {
        GameTests.register("glue-test:mcsx-axiom", AxiomCompatGameTest::create);
    }

    private static GameTest create() {
        AxiomDriver axiom = AxiomDriver.load();
        GlueStudio studio = new GlueStudio(false);
        GlueStudio lateStudio = new GlueStudio(false);
        Dockspace dockspace = studio.dockspace();
        AtomicReference<Screen> contextScreen = new AtomicReference<>();

        return GameTest.create("glue-test:mcsx-axiom")
                .waitForWorld()
                .run("open glue studio", ctx -> studio.open())
                .waitUntil("glue studio is open", ctx -> dockspace.getView() != null)
                .waitTicks(10)
                .waitUntil("the world is confined to glue studio", ctx -> GameViewport.bounds() != null)
                .run("focus the game viewport", ctx -> GameFocus.request())
                .waitUntil("the game viewport owns input", ctx ->
                        GameFocus.isHeld() && ctx.client().mouseHandler.isMouseGrabbed())
                .run("open the Axiom context menu", ctx -> contextScreen.set(axiom.openContextMenu()))
                .waitUntil("the Axiom context menu is active", ctx -> axiom.isContextMenuActive())
                .waitTicks(5)
                .run("assert Axiom uses the game viewport", ctx -> {
                    GameViewport.Bounds bounds = GameViewport.bounds();
                    require(bounds != null, "Axiom opened after the game viewport disappeared");
                    require(dockspace.isOpen(), "Axiom's context menu closed Glue Studio");
                    require(GameFocus.isHeld(), "Axiom's context menu dropped logical game focus");
                    require(!ctx.client().mouseHandler.isMouseGrabbed(),
                            "Axiom's context menu did not release the cursor");
                    requireEquals(Math.ceilDiv(bounds.width(), ctx.client().getWindow().getGuiScale()),
                            contextScreen.get().width, "Axiom context-menu width");
                    requireEquals(Math.ceilDiv(bounds.height(), ctx.client().getWindow().getGuiScale()),
                            contextScreen.get().height, "Axiom context-menu height");
                })
                .screenshot("mcsx-axiom-context")
                .run("click through Axiom's viewport-local input", ctx -> {
                    GameViewport.Bounds bounds = GameViewport.bounds();
                    require(bounds != null, "Game viewport vanished before Axiom input");
                    RealInput.moveToFramebuffer(ctx.client(), bounds.x() + 10, bounds.y() + 10);
                    RealInput.leftButton(ctx.client(), true);
                    RealInput.leftButton(ctx.client(), false);
                })
                .run("assert Axiom retained the click stream", ctx ->
                        require(axiom.isContextMenuActive(),
                                "Axiom's context menu closed during viewport-local input"))
                .run("close the Axiom context menu", ctx -> axiom.closeContextMenu())
                .waitUntil("Axiom restores mouselook", ctx ->
                        !axiom.isContextMenuActive() && ctx.client().mouseHandler.isMouseGrabbed())
                .run("assert context-menu focus restoration", ctx -> {
                    require(GameFocus.isHeld(), "Axiom returned the cursor to MCSX instead of the game");
                    require(dockspace.isOpen(), "Glue Studio closed with Axiom's context menu");
                })
                .run("return input to glue studio", ctx -> GameFocus.release())
                .waitUntil("glue studio owns input", ctx ->
                        !GameFocus.isHeld() && !ctx.client().mouseHandler.isMouseGrabbed())
                .run("open Axiom's full editor through a rebound toggle", ctx -> {
                    InputConstants.Key previous = axiom.rebindEditorToggle(GLFW.GLFW_KEY_F13);
                    try {
                        RealInput.tap(ctx.client(), GLFW.GLFW_KEY_F13);
                    } finally {
                        axiom.restoreEditorToggle(previous);
                    }
                })
                .waitUntil("Axiom replaces glue studio", ctx ->
                        axiom.isEditorEnabled() && !dockspace.isOpen())
                .run("assert editor takeover cleanup", ctx ->
                        require(GameViewport.bounds() == null,
                                "Closing Glue Studio for Axiom left the game viewport active"))
                .run("mount glue studio behind the active Axiom editor", ctx -> lateStudio.open())
                .waitUntil("Axiom dismisses a late glue studio mount", ctx -> !lateStudio.dockspace().isOpen())
                .run("assert late editor takeover cleanup", ctx ->
                        require(GameViewport.bounds() == null,
                                "A workspace mounted behind Axiom left the game viewport active"))
                .run("close Axiom's full editor", ctx -> axiom.disableEditor())
                .waitUntil("Axiom's editor closes", ctx -> !axiom.isEditorEnabled());
    }

    private record AxiomDriver(Object contextMenu, KeyMapping contextKey, KeyMapping editorToggleKey,
                               Constructor<?> contextScreen,
                               Method openContext, Method closeContext, Method contextActive,
                               Method editorDisable, Method editorIsEnabled) {

        private static AxiomDriver load() {
            try {
                Class<?> managerClass = Class.forName("com.moulberry.axiom.ContextMenuManager");
                Class<?> screenClass = Class.forName("com.moulberry.axiom.screen.SwitchHotbarScreen");
                Class<?> editorClass = Class.forName("com.moulberry.axiom.editor.EditorUI");
                Class<?> clientEventsClass = Class.forName("com.moulberry.axiom.ClientEvents");
                Object manager = managerClass.getMethod("getInstance").invoke(null);
                return new AxiomDriver(
                        manager,
                        (KeyMapping) clientEventsClass.getField("contextMenuKeyBind").get(null),
                        (KeyMapping) clientEventsClass.getField("toggleEditorUiKeyBind").get(null),
                        screenClass.getConstructor(),
                        managerClass.getMethod("open", Screen.class),
                        managerClass.getMethod("close"),
                        managerClass.getMethod("isActive"),
                        editorClass.getMethod("disable"),
                        editorClass.getMethod("isEnabled")
                );
            } catch (ReflectiveOperationException | LinkageError exception) {
                throw new IllegalStateException("glue-test:mcsx-axiom requires Axiom 5.4.x", exception);
            }
        }

        private Screen openContextMenu() {
            try {
                Screen screen = (Screen) this.contextScreen.newInstance();
                this.contextKey.setDown(true);
                invoke(this.openContext, this.contextMenu, screen);
                return screen;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not construct Axiom's context menu", exception);
            }
        }

        private void closeContextMenu() {
            this.contextKey.setDown(false);
            invoke(this.closeContext, this.contextMenu);
        }

        private boolean isContextMenuActive() {
            return (Boolean) invoke(this.contextActive, this.contextMenu);
        }

        private InputConstants.Key rebindEditorToggle(int key) {
            InputConstants.Key previous = InputConstants.getKey(this.editorToggleKey.saveString());
            this.editorToggleKey.setKey(InputConstants.getKey(key, 0));
            KeyMapping.resetMapping();
            return previous;
        }

        private void restoreEditorToggle(InputConstants.Key key) {
            this.editorToggleKey.setKey(key);
            KeyMapping.resetMapping();
        }

        private void disableEditor() {
            invoke(this.editorDisable, null);
        }

        private boolean isEditorEnabled() {
            return (Boolean) invoke(this.editorIsEnabled, null);
        }

        private static Object invoke(Method method, Object receiver, Object... arguments) {
            try {
                return method.invoke(receiver, arguments);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Axiom compatibility method is inaccessible", exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("Axiom compatibility method failed", cause);
            }
        }
    }
}

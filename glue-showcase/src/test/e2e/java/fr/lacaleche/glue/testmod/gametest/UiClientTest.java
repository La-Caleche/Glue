package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.client.debug.internal.Framebuffers;
import fr.lacaleche.glue.client.ui.UiButton;
import fr.lacaleche.glue.client.ui.UiRowList;
import fr.lacaleche.glue.client.ui.UiScreen;
import fr.lacaleche.glue.client.ui.UiSlider;
import fr.lacaleche.glue.client.ui.UiToggle;
import fr.lacaleche.glue.gametest.ClientTest;
import fr.lacaleche.glue.gametest.ClientTestSpec;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.ui.ShowcaseDeveloperPage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The developer menu: F8 on the title screen with its world pages disabled, then in a world switching
 * pages, a toggle and a slider driven through real input, the framebuffer viewer listing a registered
 * texture, Show on HUD, and the texture locations released when the menu closes.
 */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
@ClientTestSpec("ui")
public final class UiClientTest implements FabricClientGameTest {

    private static final String MENU_TITLE = "glue.developer_menu.title";

    private ClientGameTestContext context;
    private ClientTest game;

    @Override
    public void runTest(ClientGameTestContext context) {
        this.context = context;
        this.game = new ClientTest(context);
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeDisplay();
        });
        this.titleScreen();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            this.inWorld();
        } finally {
            context.setScreen(() -> null);
        }
    }

    private void titleScreen() {
        this.context.waitForScreen(TitleScreen.class);
        this.context.getInput().pressKey(GLFW.GLFW_KEY_F8);
        this.context.waitFor(client -> menu(client) != null);
        this.context.waitTicks(2);
        this.game.expect("world pages disabled", client -> require(menu(client).children().size() == 2
                && Framebuffers.INSTANCE.buffers().isEmpty(), "A page that needs a world was built on the title screen"));
        this.context.takeScreenshot("ui-menu-title-screen");
        this.context.getInput().pressKey(GLFW.GLFW_KEY_F8);
        this.context.waitForScreen(TitleScreen.class);
    }

    private void inWorld() {
        this.context.getInput().pressKey(GLFW.GLFW_KEY_F8);
        this.context.waitFor(client -> menu(client) != null);
        this.selectPage("glue.developer_menu.framebuffers.title");
        this.game.expect("registered texture listed", client -> require(Framebuffers.INSTANCE.buffers().stream()
                .anyMatch(buffer -> ShowcaseDeveloperPage.TEXTURE_NAME.equals(buffer.name())),
                "The showcase's texture is missing from the framebuffer viewer"));

        UiSlider slider = this.control(UiSlider.class, widget -> true);
        double[] track = this.context.computeOnClient(client -> new double[] {
                slider.getX() + 1, slider.getRight() - 1, slider.getY() + slider.getHeight() / 2.0});
        this.game.movePointer(track[0], track[2]);
        this.context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.game.expect("slider pressed at its start", client -> require(Framebuffers.INSTANCE.gridSize() == 1,
                "Pressing the grid slider's start must give a 1 by 1 grid"));
        // Minecraft drops pointer motion while its window is unfocused, as it is under the test runner.
        this.context.runOnClient(client -> client.screen.mouseDragged(track[1], track[2],
                GLFW.GLFW_MOUSE_BUTTON_LEFT, track[1] - track[0], 0));
        this.context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.game.expect("grid slider dragged", client -> require(Framebuffers.INSTANCE.gridSize() == 4,
                "Dragging the grid slider to its end must give a 4 by 4 grid"));
        this.context.waitTicks(3);
        this.game.expect("grid textures registered", client -> require(textureViews(client) > 0,
                "The grid registered no texture location"));
        this.context.takeScreenshot("ui-framebuffers");

        this.selectPage("Showcase");
        this.game.expect("hidden page released", client -> require(Framebuffers.INSTANCE.buffers().isEmpty()
                && textureViews(client) == 0, "Leaving the Framebuffers page must free its copies and locations"));
        boolean raycast = this.context.computeOnClient(client -> TestmodClient.getInstance().isRaycastDebugEnabled());
        this.click(this.control(UiToggle.class, widget -> true));
        this.game.expect("toggle clicked", client -> require(TestmodClient.getInstance().isRaycastDebugEnabled() != raycast,
                "Clicking the Raycast toggle must switch the overlay"));
        this.click(this.control(UiToggle.class, widget -> true));

        this.selectPage("glue.developer_menu.framebuffers.title");
        this.click(this.control(UiButton.class,
                button -> button.getMessage().getString().equals(text("glue.developer_menu.framebuffers.show_on_hud"))));
        this.context.waitForScreen(null);
        this.game.waitForWorldFrames(3);
        this.game.expect("grid on the HUD", client -> require(!Framebuffers.INSTANCE.buffers().isEmpty()
                && textureViews(client) > 0, "Show on HUD must keep the viewer capturing and drawing"));
        this.context.takeScreenshot("ui-framebuffers-hud");

        this.context.getInput().pressKey(GLFW.GLFW_KEY_F8);
        this.context.waitFor(client -> menu(client) != null);
        this.context.getInput().pressKey(GLFW.GLFW_KEY_F8);
        this.context.waitForScreen(null);
        this.game.expect("closed menu released", client -> require(Framebuffers.INSTANCE.buffers().isEmpty()
                && textureViews(client) == 0, "Closing the menu must take the grid off the HUD and free it"));
    }

    private void selectPage(String title) {
        AbstractButton tab = this.context.computeOnClient(client -> menu(client).children().stream()
                .filter(UiRowList.class::isInstance).map(UiRowList.class::cast).findFirst().orElseThrow()
                .children().stream().flatMap(entry -> entry.children().stream())
                .filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                .filter(button -> button.getMessage().getString().equals(text(title)))
                .findFirst().orElseThrow(() -> new AssertionError("No page tab named " + text(title))));
        this.click(tab);
        this.context.waitTicks(2);
    }

    private void click(AbstractWidget widget) {
        double[] centre = this.context.computeOnClient(client -> new double[] {
                widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0});
        this.game.movePointer(centre[0], centre[1]);
        this.context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** The first control of a type in the shown page's rows, after a frame has laid them out. */
    private <T extends AbstractWidget> T control(Class<T> type, Predicate<T> filter) {
        this.context.waitTicks(2);
        return this.context.computeOnClient(client -> menu(client).children().stream()
                .filter(UiRowList.class::isInstance).map(UiRowList.class::cast).skip(1)
                .flatMap(list -> list.children().stream())
                .flatMap(entry -> entry.children().stream())
                .filter(type::isInstance).map(type::cast).filter(filter)
                .findFirst().orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " on the page")));
    }

    private static Screen menu(Minecraft client) {
        if (client.screen instanceof UiScreen screen && screen.getTitle().getString().equals(text(MENU_TITLE))) {
            return screen;
        }
        return null;
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    /** How many texture locations the kit's texture views hold in the texture manager. */
    @SuppressWarnings({"unchecked", "PMD.AvoidAccessibilityAlteration"})
    private static long textureViews(Minecraft client) {
        try {
            Field field = TextureManager.class.getDeclaredField("byPath");
            field.setAccessible(true);
            Map<ResourceLocation, AbstractTexture> textures = (Map<ResourceLocation, AbstractTexture>) field.get(
                    client.getTextureManager());
            return textures.keySet().stream()
                    .filter(location -> "glue".equals(location.getNamespace())
                            && location.getPath().startsWith("ui/texture_view_"))
                    .count();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The texture manager's map is not readable", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

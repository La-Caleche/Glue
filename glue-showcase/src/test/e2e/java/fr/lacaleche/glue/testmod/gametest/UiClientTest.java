package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.client.debug.internal.Framebuffers;
import fr.lacaleche.glue.client.debug.internal.RaycastPage;
import fr.lacaleche.glue.client.ui.UiButton;
import fr.lacaleche.glue.client.ui.UiNumberField;
import fr.lacaleche.glue.client.ui.UiPage;
import fr.lacaleche.glue.client.ui.UiPageBuilder;
import fr.lacaleche.glue.client.ui.UiPanelScreen;
import fr.lacaleche.glue.client.ui.UiRowList;
import fr.lacaleche.glue.client.ui.UiScreen;
import fr.lacaleche.glue.client.ui.UiSlider;
import fr.lacaleche.glue.client.ui.UiToggle;
import fr.lacaleche.glue.gametest.ClientTest;
import fr.lacaleche.glue.gametest.ClientTestSpec;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * The developer menu: F8 on the title screen with its world pages disabled, then in a world switching
 * pages, a toggle and a slider driven through real input, the framebuffer viewer listing a registered
 * texture, the raycast overlay switched from its page, Show on HUD, and the texture locations released
 * when the menu closes. Last, a panel screen over the world that keeps running, takes clicks on its rows,
 * hands clicks beside it to its owner and rebuilds its page in place, and its number field, dragged, scrolled and
 * typed into.
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
            this.panel();
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
        this.game.expect("glue listed first", client -> require(tabs(client).getFirst().getMessage().getString()
                .equals(text("glue.developer_menu.framebuffers.title")), "Glue's pages must head the menu"));
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
        boolean blur = this.context.computeOnClient(client -> blurred());
        this.click(this.control(UiToggle.class, widget -> true));
        this.game.expect("toggle clicked", client -> require(blurred() != blur,
                "Clicking the Blur toggle must switch the effect"));
        this.click(this.control(UiToggle.class, widget -> true));

        this.selectPage("glue.developer_menu.raycast.title");
        this.click(this.control(UiToggle.class, widget -> true));
        this.game.expect("raycast overlay on", client -> require(RaycastPage.OVERLAY.enabled,
                "The Raycast page's toggle must switch Glue's overlay on"));
        this.game.waitForWorldFrames(3);
        this.context.takeScreenshot("ui-raycast");
        this.click(this.control(UiToggle.class, widget -> true));
        this.game.expect("raycast overlay off", client -> require(!RaycastPage.OVERLAY.enabled,
                "The Raycast page's toggle must switch Glue's overlay off"));

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

    private void panel() {
        AtomicBoolean value = new AtomicBoolean();
        AtomicInteger builds = new AtomicInteger();
        AtomicInteger outside = new AtomicInteger();
        AtomicReference<Double> number = new AtomicReference<>(0.0);
        UiPage page = new UiPage() {
            @Override
            public Component title() {
                return Component.literal("Panel");
            }

            @Override
            public void build(UiPageBuilder builder) {
                builds.incrementAndGet();
                builder.toggle(Component.literal("Value"), Component.literal("A toggle in the panel."),
                        value::get, value::set);
                builder.number(Component.literal("Number"), Component.literal("A number field in the panel."),
                        Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.5, number::get, number::set);
            }
        };
        this.context.setScreen(() -> new UiPanelScreen(Component.literal("Panel"), page) {
            @Override
            protected boolean clickedOutside(double mouseX, double mouseY, int button) {
                outside.incrementAndGet();
                return true;
            }
        });
        this.context.waitTicks(2);
        long time = this.context.computeOnClient(client -> client.level.getGameTime());
        this.context.waitTicks(10);
        this.game.expect("world runs behind the panel", client -> require(client.level.getGameTime() > time
                && !client.isPaused(), "A panel screen must not pause the game"));

        UiToggle toggle = this.context.computeOnClient(client -> panelControl(client, UiToggle.class));
        this.click(toggle);
        this.game.expect("panel toggle clicked", client -> require(value.get(), "Clicking the panel's toggle must set it"));
        this.game.movePointer(20, 20);
        this.context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.game.expect("click beside the panel", client -> require(outside.get() == 1,
                "A click beside the panel must reach clickedOutside"));
        this.context.runOnClient(client -> ((UiPanelScreen) client.screen).refresh());
        this.game.expect("panel refreshed", client -> require(builds.get() == 2
                && panelControl(client, UiToggle.class) != toggle, "Refreshing the panel must build its page again"));
        this.numberField(number);
        this.context.takeScreenshot("ui-panel");
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        this.context.waitForScreen(null);
    }

    /** Drags the panel's number field ten steps right, scrolls it a step up, then types a value into it. */
    private void numberField(AtomicReference<Double> number) {
        // The refreshed page lays its rows out when it is next drawn.
        this.context.waitTicks(2);
        UiNumberField field = this.context.computeOnClient(client -> panelControl(client, UiNumberField.class));
        double[] centre = this.context.computeOnClient(client -> new double[] {
                field.getX() + field.getWidth() / 2.0, field.getY() + field.getHeight() / 2.0});
        this.game.movePointer(centre[0], centre[1]);
        this.context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        // Minecraft drops pointer motion while its window is unfocused, as it is under the test runner.
        this.context.runOnClient(client -> client.screen.mouseDragged(centre[0] + 20, centre[1],
                GLFW.GLFW_MOUSE_BUTTON_LEFT, 20, 0));
        this.context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.game.expect("number field dragged", client -> require(number.get() == 5.0 && !field.isEditing(),
                "Dragging the number field twenty pixels must move it ten half steps"));
        this.context.runOnClient(client -> client.screen.mouseScrolled(centre[0], centre[1], 0, 1));
        this.game.expect("number field scrolled", client -> require(number.get() == 5.5,
                "Scrolling up over the focused number field must move it a step"));

        this.game.movePointer(centre[0], centre[1]);
        this.context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        this.game.expect("number field typing", client -> require(field.isEditing(),
                "Clicking the number field without a drag must start typing"));
        this.context.getInput().typeChars("-12.25");
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        this.game.expect("number field typed", client -> require(number.get() == -12.25 && !field.isEditing(),
                "Enter must set the typed value"));
    }

    private static <T extends AbstractWidget> T panelControl(Minecraft client, Class<T> type) {
        return client.screen.children().stream()
                .filter(UiRowList.class::isInstance).map(UiRowList.class::cast)
                .flatMap(list -> list.children().stream())
                .flatMap(entry -> entry.children().stream())
                .filter(type::isInstance).map(type::cast)
                .findFirst().orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " in the panel"));
    }

    private void selectPage(String title) {
        AbstractButton tab = this.context.computeOnClient(client -> tabs(client).stream()
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

    /** The sidebar's page tabs, in order. */
    private static List<AbstractButton> tabs(Minecraft client) {
        return menu(client).children().stream()
                .filter(UiRowList.class::isInstance).map(UiRowList.class::cast).findFirst().orElseThrow()
                .children().stream().flatMap(entry -> entry.children().stream())
                .filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                .toList();
    }

    private static Screen menu(Minecraft client) {
        if (client.screen instanceof UiScreen screen && screen.getTitle().getString().equals(text(MENU_TITLE))) {
            return screen;
        }
        return null;
    }

    private static boolean blurred() {
        return TestPostShaderHandler.INSTANCE.isToggled(TestShaders.BLUR);
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

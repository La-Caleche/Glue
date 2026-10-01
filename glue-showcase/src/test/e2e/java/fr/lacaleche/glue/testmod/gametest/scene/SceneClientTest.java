package fr.lacaleche.glue.testmod.gametest.scene;

import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.gizmo.GizmoOperation;
import fr.lacaleche.glue.client.render.gizmo.GizmoSpace;
import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.gametest.ClientTestSpec;
import fr.lacaleche.glue.testmod.gametest.WorldClientTest;
import fr.lacaleche.glue.testmod.gametest.web.WebTestPage;
import fr.lacaleche.glue.testmod.scene.BlockSceneTestScreen;
import fr.lacaleche.glue.testmod.scene.FpsViewportTestScreen;
import fr.lacaleche.glue.testmod.scene.GizmoTestScreen;
import fr.lacaleche.glue.testmod.scene.SceneTestController;
import fr.lacaleche.glue.testmod.scene.UpdateBlockCommand;
import fr.lacaleche.glue.web.host.WebScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** Hub navigation, three scene previews, camera input, history and target disposal. */
@SuppressWarnings({"PMD.TestClassWithoutTestCases", "PMD.CompareObjectsWithEquals"})
@ClientTestSpec("scenes")
public final class SceneClientTest extends WorldClientTest {

    @Override
    protected void test() {
        this.context.getInput().pressKey(GLFW.GLFW_KEY_F6);
        this.context.waitForScreen(WebScreen.class);
        WebScreen hub = this.context.computeOnClient(client -> (WebScreen) client.screen);
        WebTestPage page = new WebTestPage(this.context, () -> hub.surface().orElse(null));
        page.ready(true);
        this.screenshot("scene-hub");
        this.orbit(hub, page);
        this.fps(hub, page);
        this.gizmo(hub, page);
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        this.context.waitForScreen(null);
        page.disposed();
    }

    private void orbit(WebScreen hub, WebTestPage page) {
        BlockSceneTestScreen screen = this.open(page, "orbit", BlockSceneTestScreen.class);
        this.waitUntil("orbit preview rendered", client -> rendered(client, screen.getSceneRenderer()));
        this.context.runOnClient(client -> {
            OrbitCameraController camera = screen.getCameraController();
            float yaw = camera.getRotationY();
            Vector3f pivot = new Vector3f(camera.getPivot());
            float zoom = camera.getZoom();
            screen.mouseClicked(100, 100, 0);
            screen.mouseDragged(124, 110, 0, 24, 10);
            screen.mouseReleased(124, 110, 0);
            require(camera.getRotationY() != yaw, "Orbit drag must rotate the camera");
            screen.mouseClicked(100, 100, 1);
            screen.mouseDragged(112, 106, 1, 12, 6);
            screen.mouseReleased(112, 106, 1);
            require(!camera.getPivot().equals(pivot), "Right drag must pan the camera");
            screen.mouseScrolled(100, 100, 0, 2);
            require(camera.getZoom() < zoom, "Wheel must zoom in");
        });
        this.context.getInput().pressKey(GLFW.GLFW_KEY_EQUAL);
        this.game.expect("expanded preview", client -> require(screen.getSceneRenderer().getHalfExtentX() == 6, "Region shortcut"));
        this.screenshot("scene-orbit");
        this.context.getInput().pressKey(GLFW.GLFW_KEY_HOME);
        this.game.expect("reset camera", client -> require(screen.getCameraController().getZoom() == 5, "Default zoom"));
        this.backToHub(hub, page);
        this.game.expect("orbit disposed", client -> require(screen.getSceneRenderer().getFramebuffer() == null, "Orbit target leaked"));
    }

    private void fps(WebScreen hub, WebTestPage page) {
        FpsViewportTestScreen screen = this.open(page, "fps", FpsViewportTestScreen.class);
        this.waitUntil("FPS preview rendered", client -> rendered(client, screen.getSceneRenderer()));
        Vec3 playerPosition = this.context.computeOnClient(client -> client.player.position());
        this.context.runOnClient(client -> screen.mouseClicked(screen.width / 2.0, screen.height / 2.0, 0));
        this.game.expect("FPS pointer captured", client -> require(screen.isCapturing()
                && GLFW.glfwGetInputMode(client.getWindow().getWindow(), GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED,
                "The FPS preview must own the pointer"));
        Vec3 cameraPosition = this.context.computeOnClient(client -> screen.getCameraController().getPosition());
        this.context.runOnClient(client -> {
            float speed = screen.getCameraController().getMoveSpeed();
            screen.getCameraController().move(1, 1, 0, false);
            screen.mouseScrolled(0, 0, 0, 1);
            require(screen.getCameraController().getMoveSpeed() > speed, "Wheel changes fly speed");
        });
        this.game.expect("independent camera", client -> require(!screen.getCameraController().getPosition().equals(cameraPosition)
                && client.player.position().distanceToSqr(playerPosition) < 0.0001, "Scene flight moved the player"));
        this.screenshot("scene-fps");
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        this.game.expect("Escape releases capture first", client -> require(client.screen == screen && !screen.isCapturing(), "FPS screen closed"));
        this.context.runOnClient(client -> {
            screen.mouseClicked(100, 100, 0);
            screen.onClose();
        });
        this.waitUntil("FPS returns to hub", client -> client.screen == hub);
        page.ready(true);
        this.game.expect("FPS disposal", client -> require(!screen.isCapturing() && screen.getSceneRenderer().getFramebuffer() == null
                && GLFW.glfwGetInputMode(client.getWindow().getWindow(), GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_NORMAL,
                "FPS pointer or render target leaked"));
    }

    private void gizmo(WebScreen hub, WebTestPage page) {
        GizmoTestScreen screen = this.open(page, "gizmo", GizmoTestScreen.class);
        this.waitUntil("gizmo preview rendered", client -> rendered(client, screen.getSceneRenderer()));
        this.context.runOnClient(client -> {
            screen.mouseClicked(screen.width / 2.0, screen.height / 2.0, 0);
            screen.mouseReleased(screen.width / 2.0, screen.height / 2.0, 0);
        });
        this.game.expect("picked block", client -> require(screen.getSceneController().getSelectedBlockPos() != null, "No selected block"));
        this.context.getInput().pressKey(GLFW.GLFW_KEY_R);
        this.game.expect("rotation shortcut", client -> require(screen.getSceneController().getGizmoController().getCurrentOperation() == GizmoOperation.ROTATE, "Rotation"));
        this.context.getInput().pressKey(GLFW.GLFW_KEY_S);
        this.game.expect("scale shortcut", client -> require(screen.getSceneController().getGizmoController().getCurrentOperation() == GizmoOperation.SCALE, "Scale"));
        this.context.getInput().pressKey(GLFW.GLFW_KEY_T);
        GizmoSpace space = this.context.computeOnClient(client -> screen.getSceneController().getGizmoController().getCurrentMode());
        boolean snap = this.context.computeOnClient(client -> screen.getSceneController().getGizmoController().isUsingSnap());
        this.context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
        this.context.getInput().pressKey(GLFW.GLFW_KEY_G);
        this.game.expect("space and snap shortcuts", client -> require(screen.getSceneController().getGizmoController().getCurrentMode() != space
                && screen.getSceneController().getGizmoController().isUsingSnap() != snap, "Space/snap did not toggle"));
        this.screenshot("scene-gizmo-selected");
        BlockPos selected = this.context.computeOnClient(client -> screen.getSceneController().getSelectedBlockPos());
        BlockState worldState = this.context.computeOnClient(client -> client.level.getBlockState(selected));
        TransformationComponent before = this.context.computeOnClient(client -> screen.getSceneController().getBlockTransform(selected));
        TransformationComponent after = new TransformationComponent(new Vector3f(before.translation()).add(0, 2, 0),
                new Quaternionf(before.leftRotation()), new Vector3f(before.scale()), new Quaternionf(before.rightRotation()));
        this.context.runOnClient(client -> {
            SceneTestController controller = screen.getSceneController();
            controller.getHistoryManager().execute(new UpdateBlockCommand(controller, selected, before, after));
        });
        this.screenshot("scene-gizmo-transformed");
        this.context.runOnClient(client -> screen.getSceneController().undo());
        this.game.expect("undo", client -> require(before.equals(screen.getSceneController().getBlockTransform(selected)), "Undo did not restore the preview"));
        this.context.runOnClient(client -> screen.getSceneController().redo());
        this.game.expect("redo only affects preview", client -> require(after.equals(screen.getSceneController().getBlockTransform(selected))
                && worldState.equals(client.level.getBlockState(selected)), "Redo changed the world or lost the preview"));
        this.context.getInput().pressKey(GLFW.GLFW_KEY_DELETE);
        this.game.expect("deselect", client -> require(screen.getSceneController().getSelectedBlockPos() == null, "Selection remains"));
        this.backToHub(hub, page);
        this.game.expect("gizmo disposed", client -> require(screen.getSceneRenderer().getFramebuffer() == null, "Gizmo target leaked"));
    }

    private <T extends Screen> T open(WebTestPage page, String name, Class<T> screenType) {
        page.click("[data-scene=" + name + "]");
        this.context.waitForScreen(screenType);
        return this.context.computeOnClient(client -> screenType.cast(client.screen));
    }

    private void backToHub(WebScreen hub, WebTestPage page) {
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        this.waitUntil("return to the same hub", client -> client.screen == hub);
        page.ready(true);
    }

    private static boolean rendered(Minecraft client, BlockSceneRenderer renderer) {
        return renderer.getFramebuffer() != null && renderer.getFramebuffer().width == client.getWindow().getWidth()
                && renderer.getFramebuffer().height == client.getWindow().getHeight();
    }
}

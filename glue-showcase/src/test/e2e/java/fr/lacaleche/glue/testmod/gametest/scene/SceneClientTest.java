package fr.lacaleche.glue.testmod.gametest.scene;

import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.gizmo.Gizmo;
import fr.lacaleche.glue.client.render.gizmo.GizmoHandle;
import fr.lacaleche.glue.client.render.gizmo.GizmoPose;
import fr.lacaleche.glue.client.render.gizmo.GizmoOperation;
import fr.lacaleche.glue.client.render.gizmo.GizmoSpace;
import fr.lacaleche.glue.client.render.gizmo.GizmoView;
import fr.lacaleche.glue.client.render.gizmo.WorldGizmos;
import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.client.ui.UiPanelScreen;
import fr.lacaleche.glue.client.ui.UiRowList;
import fr.lacaleche.glue.client.ui.UiToggle;
import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.gametest.ClientTestSpec;
import fr.lacaleche.glue.testmod.gametest.WorldClientTest;
import fr.lacaleche.glue.testmod.scene.BlockSceneTestScreen;
import fr.lacaleche.glue.testmod.scene.FpsViewportTestScreen;
import fr.lacaleche.glue.testmod.scene.GizmoTestScreen;
import fr.lacaleche.glue.testmod.scene.SceneDemos;
import fr.lacaleche.glue.testmod.scene.SceneTestController;
import fr.lacaleche.glue.testmod.scene.UpdateBlockCommand;
import fr.lacaleche.glue.testmod.scene.WorldGizmoDemo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/**
 * The scene previews behind {@code /showcase scene}: camera input, history and target disposal, and the
 * gizmo dragged in a preview and in the world.
 */
@SuppressWarnings({"PMD.TestClassWithoutTestCases", "PMD.CompareObjectsWithEquals"})
@ClientTestSpec("scenes")
public final class SceneClientTest extends WorldClientTest {

    @Override
    protected void test() {
        this.orbit();
        this.fps();
        this.gizmo();
        this.worldGizmo();
    }

    private void orbit() {
        BlockSceneTestScreen screen = this.open(SceneDemos::openOrbit);
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
        this.close();
        this.game.expect("orbit disposed", client -> require(screen.getSceneRenderer().getFramebuffer() == null, "Orbit target leaked"));
    }

    private void fps() {
        FpsViewportTestScreen screen = this.open(SceneDemos::openFps);
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
        this.context.waitForScreen(null);
        this.game.expect("FPS disposal", client -> require(!screen.isCapturing() && screen.getSceneRenderer().getFramebuffer() == null
                && GLFW.glfwGetInputMode(client.getWindow().getWindow(), GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_NORMAL,
                "FPS pointer or render target leaked"));
    }

    private void gizmo() {
        GizmoTestScreen screen = this.open(SceneDemos::openGizmo);
        this.waitUntil("gizmo preview rendered", client -> rendered(client, screen.getSceneRenderer()));
        this.context.runOnClient(client -> {
            screen.mouseClicked(screen.width / 2.0, screen.height / 2.0, 0);
            screen.mouseReleased(screen.width / 2.0, screen.height / 2.0, 0);
        });
        this.game.expect("picked block", client -> require(screen.getSceneController().getSelectedBlockPos() != null, "No selected block"));
        this.context.getInput().pressKey(GLFW.GLFW_KEY_R);
        this.game.expect("rotation shortcut", client -> require(screen.getGizmo().operation() == GizmoOperation.ROTATE, "Rotation"));
        this.context.getInput().pressKey(GLFW.GLFW_KEY_S);
        this.game.expect("scale shortcut", client -> require(screen.getGizmo().operation() == GizmoOperation.SCALE, "Scale"));
        this.context.getInput().pressKey(GLFW.GLFW_KEY_T);
        GizmoSpace space = this.context.computeOnClient(client -> screen.getGizmo().space());
        boolean snap = this.context.computeOnClient(client -> screen.getGizmo().isSnapping());
        this.context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
        this.context.getInput().pressKey(GLFW.GLFW_KEY_G);
        this.game.expect("space and snap shortcuts", client -> require(screen.getGizmo().space() != space
                && screen.getGizmo().isSnapping() != snap, "Space/snap did not toggle"));
        BlockPos selected = this.context.computeOnClient(client -> screen.getSceneController().getSelectedBlockPos());
        this.context.runOnClient(client -> {
            UiToggle toggle = control(screen, UiToggle.class);
            double x = toggle.getX() + toggle.getWidth() / 2.0;
            double y = toggle.getY() + toggle.getHeight() / 2.0;
            screen.mouseClicked(x, y, 0);
            screen.mouseReleased(x, y, 0);
        });
        this.game.expect("panel click", client -> require(screen.getGizmo().isSnapping() == snap
                && selected.equals(screen.getSceneController().getSelectedBlockPos()), "The panel must take the click before the camera"));
        this.screenshot("scene-gizmo-selected");
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
        int commands = this.context.computeOnClient(client -> screen.getSceneController().getHistoryManager().getCursor());
        this.context.runOnClient(client -> this.dragAxis(screen, screen.getGizmo(), screen.getGizmoView(), GizmoHandle.X));
        this.game.expect("X arrow drag", client -> {
            TransformationComponent dragged = screen.getSceneController().getBlockTransform(selected);
            require(dragged.translation().x() > after.translation().x() + 0.1f
                    && Math.abs(dragged.translation().y() - after.translation().y()) < 1.0E-4f
                    && Math.abs(dragged.translation().z() - after.translation().z()) < 1.0E-4f, "The drag must move along X only");
            require(screen.getSceneController().getHistoryManager().getCursor() == commands + 1, "The drag must be one undoable step");
        });
        this.screenshot("scene-gizmo-dragged");
        this.context.runOnClient(client -> screen.getSceneController().undo());
        this.game.expect("drag undo", client -> require(after.equals(screen.getSceneController().getBlockTransform(selected)), "Undo did not restore the drag"));
        this.context.getInput().pressKey(GLFW.GLFW_KEY_DELETE);
        this.game.expect("deselect", client -> require(screen.getSceneController().getSelectedBlockPos() == null, "Selection remains"));
        this.close();
        this.game.expect("gizmo disposed", client -> require(screen.getSceneRenderer().getFramebuffer() == null, "Gizmo target leaked"));
    }

    private void worldGizmo() {
        UiPanelScreen screen = this.open(SceneDemos::openWorldGizmo);
        Gizmo gizmo = this.context.computeOnClient(client -> screen.gizmo());
        WorldGizmoDemo demo = this.context.computeOnClient(client -> (WorldGizmoDemo) gizmo.target());
        this.context.waitTicks(2);
        GizmoPose start = this.context.computeOnClient(client -> demo.pose());
        GizmoHandle handle = this.context.computeOnClient(client -> {
            GizmoView view = GizmoView.world();
            require(view != null && WorldGizmos.isShown(gizmo), "The gizmo must be shown in the world");
            return gizmo.grabPoint(view, GizmoHandle.X) != null ? GizmoHandle.X : GizmoHandle.Z;
        });
        int axis = handle == GizmoHandle.X ? 0 : 2;
        this.context.runOnClient(client -> this.dragAxis(screen, gizmo, GizmoView.world(), handle));
        this.game.expect("world drag", client -> {
            Vector3d moved = demo.pose().position().sub(start.position());
            require(moved.get(axis) > 0.1 && Math.abs(moved.get(1)) < 1.0E-6 && Math.abs(moved.get(2 - axis)) < 1.0E-6,
                    "The drag must move the box along its axis only");
            require(demo.commits() == 1 && !gizmo.isDragging(), "The drag must commit once");
        });
        this.screenshot("scene-world-gizmo");
        GizmoPose dragged = this.context.computeOnClient(client -> demo.pose());
        this.context.runOnClient(client -> {
            GizmoView view = GizmoView.world();
            Vector2f arrow = gizmo.grabPoint(view, handle);
            require(screen.mouseClicked(arrow.x, arrow.y, 0), "The arrow must take the press");
            screen.mouseDragged(arrow.x + 40, arrow.y + 40, 0, 40, 40);
            screen.mouseClicked(arrow.x + 40, arrow.y + 40, 1);
        });
        this.game.expect("world cancel", client -> require(dragged.equals(demo.pose()) && demo.commits() == 1
                && !gizmo.isDragging(), "A right click must put the box back without a commit"));
        this.close();
        this.game.expect("world gizmo hidden", client -> require(!WorldGizmos.isShown(gizmo), "The gizmo outlived its screen"));
    }

    /** Drags a handle thirty pixels outward, away from the gizmo's centre on screen. */
    private void dragAxis(Screen screen, Gizmo gizmo, GizmoView view, GizmoHandle handle) {
        Vector2f center = gizmo.grabPoint(view, GizmoHandle.CENTER);
        Vector2f arrow = gizmo.grabPoint(view, handle);
        require(center != null && arrow != null, "The arrow must be on screen");
        Vector2f to = new Vector2f(arrow).sub(center).normalize().mul(30).add(arrow);
        require(screen.mouseClicked(arrow.x, arrow.y, 0) && gizmo.isDragging(), "The arrow must take the press");
        screen.mouseDragged(to.x, to.y, 0, to.x - arrow.x, to.y - arrow.y);
        screen.mouseReleased(to.x, to.y, 0);
    }

    private <T extends Screen> T open(Supplier<T> scene) {
        T screen = this.context.computeOnClient(client -> scene.get());
        this.context.waitForScreen(screen.getClass());
        return screen;
    }

    private void close() {
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        this.context.waitForScreen(null);
    }

    private static <T> T control(Screen screen, Class<T> type) {
        return screen.children().stream()
                .filter(UiRowList.class::isInstance).map(UiRowList.class::cast)
                .flatMap(list -> list.children().stream())
                .flatMap(entry -> entry.children().stream())
                .filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static boolean rendered(Minecraft client, BlockSceneRenderer renderer) {
        return renderer.getFramebuffer() != null && renderer.getFramebuffer().width == client.getWindow().getWidth()
                && renderer.getFramebuffer().height == client.getWindow().getHeight();
    }
}

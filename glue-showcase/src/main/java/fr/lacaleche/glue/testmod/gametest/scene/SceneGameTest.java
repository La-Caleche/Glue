package fr.lacaleche.glue.testmod.gametest.scene;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lacaleche.glue.client.camera.OrbitCameraController;
import fr.lacaleche.glue.client.render.gizmo.GizmoOperation;
import fr.lacaleche.glue.client.render.gizmo.GizmoSpace;
import fr.lacaleche.glue.client.render.scene.BlockSceneRenderer;
import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.gametest.TestContext;
import fr.lacaleche.glue.testmod.gametest.RealInput;
import fr.lacaleche.glue.testmod.scene.BlockSceneTestScreen;
import fr.lacaleche.glue.testmod.scene.FpsViewportTestScreen;
import fr.lacaleche.glue.testmod.scene.GizmoTestScreen;
import fr.lacaleche.glue.testmod.scene.SceneTestController;
import fr.lacaleche.glue.testmod.scene.UpdateBlockCommand;
import fr.lacaleche.glue.testmod.web.WebDemos;
import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.host.WebScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

/** Live recovery regression: hub entry points, camera controls, preview history and render-target cleanup. */
public final class SceneGameTest {

    private WebScreen hub;
    private BlockSceneTestScreen orbit;
    private FpsViewportTestScreen fps;
    private GizmoTestScreen gizmo;
    private Vec3 playerPosition;
    private Vec3 cameraPosition;
    private BlockPos selected;
    private BlockState worldState;
    private TransformationComponent before;
    private TransformationComponent after;

    private SceneGameTest() {
    }

    public static void register() {
        GameTests.register("glue-test:scenes", () -> new SceneGameTest().build());
    }

    private GameTest build() {
        GameTest test = GameTest.create("glue-test:scenes").waitForWorld()
                .run("focus the client window", ctx -> GLFW.glfwFocusWindow(ctx.client().getWindow().getWindow()))
                .waitUntil("the client window is focused", ctx -> ctx.client().isWindowActive())
                .run("open the F6 hub", ctx -> this.hub = WebDemos.openHub())
                .waitUntil("the hub is connected", ctx -> this.hubReady(), GameTest.LONG_TIMEOUT)
                .waitTicks(5).screenshot("scene-hub");
        this.orbit(test);
        this.fps(test);
        this.gizmo(test);
        return test.run("return from the hub to gameplay", ctx -> this.hub.onClose())
                .expect("all scene targets have been released", ctx -> this.orbit.getSceneRenderer().getFramebuffer() == null
                        && this.fps.getSceneRenderer().getFramebuffer() == null && this.gizmo.getSceneRenderer().getFramebuffer() == null);
    }

    private void orbit(GameTest test) {
        this.openFromHub(test, "orbit");
        test.waitUntil("the orbit preview has rendered", ctx -> {
                    if (!(ctx.client().screen instanceof BlockSceneTestScreen screen)) return false;
                    this.orbit = screen;
                    return rendered(screen.getSceneRenderer());
                })
                .run("orbit, pan and zoom with the screen input handlers", ctx -> {
                    OrbitCameraController camera = this.orbit.getCameraController();
                    float yaw = camera.getRotationY();
                    Vector3f pivot = new Vector3f(camera.getPivot());
                    float zoom = camera.getZoom();
                    this.orbit.mouseClicked(100, 100, 0);
                    this.orbit.mouseDragged(124, 110, 0, 24, 10);
                    this.orbit.mouseReleased(124, 110, 0);
                    require(camera.getRotationY() != yaw, "Orbit drag must rotate the camera");
                    this.orbit.mouseClicked(100, 100, 1);
                    this.orbit.mouseDragged(112, 106, 1, 12, 6);
                    this.orbit.mouseReleased(112, 106, 1);
                    require(!camera.getPivot().equals(pivot), "Right drag must pan the camera");
                    this.orbit.mouseScrolled(100, 100, 0, 2);
                    require(camera.getZoom() < zoom, "Wheel must zoom in");
                })
                .run("expand the preview region", ctx -> RealInput.tap(ctx.client(), GLFW.GLFW_KEY_EQUAL))
                .expect("region controls reach the scene renderer", ctx -> this.orbit.getSceneRenderer().getHalfExtentX() == 6)
                .waitTicks(5).screenshot("scene-orbit")
                .run("reset the orbit camera", ctx -> RealInput.tap(ctx.client(), GLFW.GLFW_KEY_HOME))
                .expect("camera reset restores the default zoom", ctx -> this.orbit.getCameraController().getZoom() == 5)
                .run("close orbit with Escape", ctx -> RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE))
                .waitUntil("orbit returns to the hub", ctx -> ctx.client().screen == this.hub && this.hubReady())
                .expect("orbit target is disposed", ctx -> this.orbit.getSceneRenderer().getFramebuffer() == null);
    }

    private void fps(GameTest test) {
        this.openFromHub(test, "fps");
        test.waitUntil("the FPS preview has rendered", ctx -> {
                    if (!(ctx.client().screen instanceof FpsViewportTestScreen screen)) return false;
                    this.fps = screen;
                    return rendered(screen.getSceneRenderer());
                })
                .run("capture the FPS pointer", ctx -> {
                    this.playerPosition = ctx.player().position();
                    this.fps.mouseClicked(this.fps.width / 2.0, this.fps.height / 2.0, 0);
                })
                .expect("FPS capture disables the GLFW cursor", ctx -> this.fps.isCapturing()
                        && GLFW.glfwGetInputMode(ctx.client().getWindow().getWindow(), GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED)
                .run("move the independent FPS camera and change speed", ctx -> {
                    this.cameraPosition = this.fps.getCameraController().getPosition();
                    float speed = this.fps.getCameraController().getMoveSpeed();
                    this.fps.getCameraController().move(1, 1, 0, false);
                    this.fps.mouseScrolled(0, 0, 0, 1);
                    require(this.fps.getCameraController().getMoveSpeed() > speed, "Wheel must change fly speed");
                })
                .expect("scene flight does not move the real player", ctx -> !this.fps.getCameraController().getPosition().equals(this.cameraPosition)
                        && ctx.player().position().distanceToSqr(this.playerPosition) < 0.0001)
                .waitTicks(5).screenshot("scene-fps")
                .run("Escape releases FPS capture first", ctx -> RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE))
                .expect("release keeps the FPS screen open", ctx -> ctx.client().screen == this.fps && !this.fps.isCapturing())
                .run("remove the FPS screen while capturing again", ctx -> {
                    this.fps.mouseClicked(100, 100, 0);
                    this.fps.onClose();
                })
                .waitUntil("FPS returns to the hub", ctx -> ctx.client().screen == this.hub && this.hubReady())
                .expect("removal releases capture and target", ctx -> !this.fps.isCapturing()
                        && this.fps.getSceneRenderer().getFramebuffer() == null
                        && GLFW.glfwGetInputMode(ctx.client().getWindow().getWindow(), GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_NORMAL);
    }

    private void gizmo(GameTest test) {
        this.openFromHub(test, "gizmo");
        test.waitUntil("the gizmo preview has rendered", ctx -> {
                    if (!(ctx.client().screen instanceof GizmoTestScreen screen)) return false;
                    this.gizmo = screen;
                    return rendered(screen.getSceneRenderer());
                })
                .run("pick the block at the center of the preview", ctx -> {
                    double x = this.gizmo.width / 2.0;
                    double y = this.gizmo.height / 2.0;
                    this.gizmo.mouseClicked(x, y, 0);
                    this.gizmo.mouseReleased(x, y, 0);
                })
                .expect("scene picking selects a block", ctx -> this.gizmo.getSceneController().getSelectedBlockPos() != null)
                .run("switch gizmo operations, space and snap", ctx -> {
                    RealInput.tap(ctx.client(), GLFW.GLFW_KEY_R);
                    require(this.gizmo.getSceneController().getGizmoController().getCurrentOperation() == GizmoOperation.ROTATE, "Rotation shortcut");
                    RealInput.tap(ctx.client(), GLFW.GLFW_KEY_S);
                    require(this.gizmo.getSceneController().getGizmoController().getCurrentOperation() == GizmoOperation.SCALE, "Scale shortcut");
                    RealInput.tap(ctx.client(), GLFW.GLFW_KEY_T);
                    GizmoSpace space = this.gizmo.getSceneController().getGizmoController().getCurrentMode();
                    boolean snap = this.gizmo.getSceneController().getGizmoController().isUsingSnap();
                    RealInput.tap(ctx.client(), GLFW.GLFW_KEY_TAB);
                    RealInput.tap(ctx.client(), GLFW.GLFW_KEY_G);
                    require(this.gizmo.getSceneController().getGizmoController().getCurrentMode() != space, "Space shortcut");
                    require(this.gizmo.getSceneController().getGizmoController().isUsingSnap() != snap, "Snap shortcut");
                })
                .waitTicks(5).screenshot("scene-gizmo-selected")
                .run("record a preview-only block transformation", ctx -> {
                    SceneTestController controller = this.gizmo.getSceneController();
                    this.selected = controller.getSelectedBlockPos();
                    this.worldState = ctx.client().level.getBlockState(this.selected);
                    this.before = controller.getBlockTransform(this.selected);
                    this.after = new TransformationComponent(new Vector3f(this.before.translation()).add(0, 2, 0),
                            new Quaternionf(this.before.leftRotation()), new Vector3f(this.before.scale()),
                            new Quaternionf(this.before.rightRotation()));
                    controller.getHistoryManager().execute(new UpdateBlockCommand(controller, this.selected, this.before, this.after));
                })
                .waitTicks(5).screenshot("scene-gizmo-transformed")
                .run("undo the preview transform", ctx -> this.gizmo.getSceneController().undo())
                .expect("undo restores the block preview", ctx -> this.before.equals(this.gizmo.getSceneController().getBlockTransform(this.selected)))
                .run("redo the preview transform", ctx -> this.gizmo.getSceneController().redo())
                .expect("redo changes only the preview", ctx -> this.after.equals(this.gizmo.getSceneController().getBlockTransform(this.selected))
                        && this.worldState.equals(ctx.client().level.getBlockState(this.selected)))
                .run("deselect with Delete", ctx -> RealInput.tap(ctx.client(), GLFW.GLFW_KEY_DELETE))
                .expect("deselection clears the selection", ctx -> this.gizmo.getSceneController().getSelectedBlockPos() == null)
                .run("close the gizmo scene", ctx -> RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE))
                .waitUntil("gizmo returns to the hub", ctx -> ctx.client().screen == this.hub && this.hubReady())
                .expect("gizmo target is disposed", ctx -> this.gizmo.getSceneRenderer().getFramebuffer() == null);
    }

    private void openFromHub(GameTest test, String name) {
        test.step("open " + name + " through its hub button", GameTest.DEFAULT_TIMEOUT, new GameTest.StepTick() {
            private CompletableFuture<String> rectangle;
            private int phase;

            @Override
            public boolean tick(TestContext context) {
                if (this.phase == 0) {
                    if (this.rectangle == null) {
                        WebSurface page = hub.surface().orElseThrow();
                        this.rectangle = page.evaluate("(()=>{const button=document.querySelector('[data-scene=" + name
                                + "]');if(!button)return null;button.scrollIntoView({block:'center'});return button.getBoundingClientRect().toJSON();})()");
                    }
                    if (!this.rectangle.isDone()) return false;
                    String json = this.rectangle.join();
                    if (json.equals("null")) {
                        this.rectangle = null;
                        return false;
                    }
                    JsonObject bounds = JsonParser.parseString(json).getAsJsonObject();
                    double scale = context.client().getWindow().getGuiScale();
                    double x = (bounds.get("x").getAsDouble() + bounds.get("width").getAsDouble() / 2) * scale;
                    double y = (bounds.get("y").getAsDouble() + bounds.get("height").getAsDouble() / 2) * scale;
                    RealInput.moveToFramebuffer(context.client(), x + 1, y);
                    RealInput.moveToFramebuffer(context.client(), x, y);
                } else if (this.phase == 1) {
                    RealInput.leftButton(context.client(), true);
                } else {
                    RealInput.leftButton(context.client(), false);
                    return true;
                }
                this.phase++;
                return false;
            }
        });
    }

    private boolean hubReady() {
        return this.hub.surface().filter(page -> !page.isClosed() && page.isConnected() && page.hasFrame()).isPresent();
    }

    private static boolean rendered(BlockSceneRenderer renderer) {
        Minecraft client = Minecraft.getInstance();
        return renderer.getFramebuffer() != null && renderer.getFramebuffer().width == client.getWindow().getWidth()
                && renderer.getFramebuffer().height == client.getWindow().getHeight();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

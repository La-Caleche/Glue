package fr.lacaleche.glue.testmod.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.events.DebugEvents;
import fr.lacaleche.glue.client.render.gizmo.Gizmo;
import fr.lacaleche.glue.client.render.gizmo.GizmoOperation;
import fr.lacaleche.glue.client.render.gizmo.GizmoPose;
import fr.lacaleche.glue.client.render.gizmo.GizmoSpace;
import fr.lacaleche.glue.client.render.gizmo.GizmoTarget;
import fr.lacaleche.glue.client.render.gizmo.WorldGizmos;
import fr.lacaleche.glue.client.ui.UiPage;
import fr.lacaleche.glue.client.ui.UiPageBuilder;
import fr.lacaleche.glue.client.ui.UiPanelScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /showcase scene world-gizmo}: a box in front of the player, moved, turned and stretched in the
 * world by a gizmo while a panel is open. Nothing is placed in the level; the box is a debug outline.
 */
public final class WorldGizmoDemo implements GizmoTarget, UiPage {

    private static final List<WorldGizmoDemo> SHOWN = new ArrayList<>();
    private static boolean registered;

    private final Gizmo gizmo = new Gizmo();
    private final GizmoPose start;
    private GizmoPose pose;
    private int commits;

    private WorldGizmoDemo(GizmoPose start) {
        this.start = start;
        this.pose = start;
        this.gizmo.setTarget(this);
    }

    /** A panel screen with a box four blocks ahead of the eyes, its gizmo shown while the panel is open. */
    static UiPanelScreen screen(Vec3 eyes, Vec3 look) {
        if (!registered) {
            DebugEvents.WORLD_DEBUG.register(WorldGizmoDemo::renderAll);
            registered = true;
        }
        Vec3 at = eyes.add(look.scale(4.0));
        WorldGizmoDemo demo = new WorldGizmoDemo(GizmoPose.at(at.x, at.y, at.z));
        UiPanelScreen screen = new UiPanelScreen(Component.literal("World gizmo"), demo) {
            @Override
            public void added() {
                super.added();
                SHOWN.add(demo);
            }

            @Override
            public void removed() {
                super.removed();
                SHOWN.remove(demo);
            }
        };
        screen.setGizmo(demo.gizmo);
        return screen;
    }

    @Override
    public GizmoPose pose() {
        return this.pose;
    }

    @Override
    public void preview(GizmoPose pose) {
        this.pose = pose;
    }

    @Override
    public void commit(GizmoPose before, GizmoPose after) {
        this.pose = after;
        this.commits++;
    }

    /** How many drags changed the box. */
    public int commits() {
        return this.commits;
    }

    public Gizmo gizmo() {
        return this.gizmo;
    }

    @Override
    public Component title() {
        return Component.literal("World gizmo");
    }

    @Override
    public void build(UiPageBuilder builder) {
        builder.section(Component.literal("Gizmo"));
        builder.cycle(Component.literal("Operation"), Component.literal("What dragging a handle does."),
                List.of(GizmoOperation.values()), WorldGizmoDemo::name, this.gizmo::operation, this.gizmo::setOperation);
        builder.cycle(Component.literal("Space"), Component.literal("Axes along the box or along the world."),
                List.of(GizmoSpace.values()), WorldGizmoDemo::name, this.gizmo::space, this.gizmo::setSpace);
        builder.toggle(Component.literal("Snap"), Component.literal("Whole steps: half a block, 15 degrees or a "
                + "quarter of the scale. Ctrl inverts it while dragging."), this.gizmo::isSnapping, this.gizmo::setSnapping);

        builder.section(Component.literal("Box"));
        builder.label(Component.literal("Position"), null, () -> {
            Vector3d position = this.pose.position();
            return Component.literal(String.format(Locale.ROOT, "%.2f %.2f %.2f", position.x, position.y, position.z));
        });
        builder.label(Component.literal("Drags"), Component.literal("Drags that changed the box; a right click or "
                + "Escape cancels one."), () -> Component.literal(Integer.toString(this.commits)));
        builder.button(Component.literal("Box"), Component.literal("Back where it began."), Component.literal("Reset"),
                () -> this.pose = this.start);
    }

    private static Component name(Enum<?> value) {
        String name = value.name();
        return Component.literal(name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT));
    }

    private static void renderAll(PoseStack matrices, MultiBufferSource buffers, double cameraX, double cameraY,
                                  double cameraZ) {
        for (WorldGizmoDemo demo : SHOWN) {
            if (!WorldGizmos.isShown(demo.gizmo)) continue;

            Vector3d position = demo.pose.position();
            Vector3f scale = demo.pose.scale();
            matrices.pushPose();
            matrices.translate(position.x - cameraX, position.y - cameraY, position.z - cameraZ);
            matrices.mulPose(demo.pose.rotation());
            matrices.scale(scale.x, scale.y, scale.z);
            ShapeRenderer.renderLineBox(matrices, buffers.getBuffer(RenderType.lines()), -0.5, -0.5, -0.5, 0.5, 0.5,
                    0.5, 1.0F, 0.85F, 0.3F, 1.0F);
            matrices.popPose();
        }
    }
}

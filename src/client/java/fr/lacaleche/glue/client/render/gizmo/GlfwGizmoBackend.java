package fr.lacaleche.glue.client.render.gizmo;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

/**
 * GLFW/Minecraft implementation of the GizmoRenderBackend.
 * Buffers all draw commands during manipulate() and renders them
 * through the modern Minecraft GUI rendering pipeline via GuiElementRenderState.
 */
public class GlfwGizmoBackend implements GizmoRenderBackend {

    private final List<DrawCommand> commands = new ArrayList<>();

    @Override
    public void drawLine(float x1, float y1, float x2, float y2, int color, float thickness) {
        commands.add(new LineCommand(x1, y1, x2, y2, color, thickness));
    }

    @Override
    public void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        commands.add(new TriangleCommand(x1, y1, x2, y2, x3, y3, color));
    }

    @Override
    public void drawQuad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {
        commands.add(new QuadCommand(x1, y1, x2, y2, x3, y3, x4, y4, color));
    }

    /**
     * Submits all buffered draw commands to the GUI render pipeline and clears the buffer.
     *
     * @param graphics the GuiGraphics context from the current render pass
     */
    public void render(GuiGraphics graphics) {
        if (commands.isEmpty()) return;

        // Push a new stratum so gizmo renders on top of scene texture
        graphics.nextStratum();

        Matrix3x2f pose = new Matrix3x2f(graphics.pose());

        for (DrawCommand cmd : commands) {
            if (cmd instanceof TriangleCommand(float x7, float y7, float x8, float y8, float x9, float y9, int color2)) {
                // Degenerate quad: duplicate last vertex
                GizmoQuadRenderState state = new GizmoQuadRenderState(pose,
                        x7, y7,
                        x8, y8,
                        x9, y9,
                        x9, y9,
                        color2);
                graphics.guiRenderState.submitGuiElement(state);
            } else if (cmd instanceof QuadCommand(
                    float x3, float y3, float x4, float y4, float x5, float y5, float x6, float y6, int color1
            )) {
                GizmoQuadRenderState state = new GizmoQuadRenderState(pose,
                        x3, y3,
                        x4, y4,
                        x5, y5,
                        x6, y6,
                        color1);
                graphics.guiRenderState.submitGuiElement(state);
            } else if (cmd instanceof LineCommand(float x1, float y1, float x2, float y2, int color, float thickness)) {
                // Line as a quad with thickness
                float dx = x2 - x1;
                float dy = y2 - y1;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 1e-6f) continue;
                float halfThick = thickness * 0.5f;
                float nx = (-dy / len) * halfThick;
                float ny = (dx / len) * halfThick;

                GizmoQuadRenderState state = new GizmoQuadRenderState(pose,
                        x1 + nx, y1 + ny,
                        x1 - nx, y1 - ny,
                        x2 - nx, y2 - ny,
                        x2 + nx, y2 + ny,
                        color);
                graphics.guiRenderState.submitGuiElement(state);
            }
        }

        commands.clear();
    }

    /**
     * Clears all buffered draw commands without rendering.
     */
    public void clear() {
        commands.clear();
    }

    /**
     * Returns the number of buffered draw commands.
     */
    public int getCommandCount() {
        return commands.size();
    }

    // --- Draw command types ---

    private sealed interface DrawCommand permits LineCommand, TriangleCommand, QuadCommand {
    }

    private record LineCommand(float x1, float y1, float x2, float y2, int color,
                               float thickness) implements DrawCommand {
    }

    private record TriangleCommand(float x1, float y1, float x2, float y2, float x3, float y3,
                                   int color) implements DrawCommand {
    }

    private record QuadCommand(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                               int color) implements DrawCommand {
    }
}

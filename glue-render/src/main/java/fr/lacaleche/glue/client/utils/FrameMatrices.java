package fr.lacaleche.glue.client.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The exact view and projection matrices the level was rendered with this frame,
 * captured from {@code LevelRenderer.renderLevel}'s own arguments.
 *
 * <p>This is not the same as rebuilding a view matrix from {@code camera.rotation()}.
 * Minecraft folds <strong>view bobbing</strong>, hurt-tilt and camera roll into the
 * matrix it actually renders with, so a matrix derived from the camera's rotation
 * alone disagrees with the depth buffer whenever the player is moving &mdash; and any
 * pass that unprojects that depth buffer will see the world slide against its own
 * reconstruction. Use these matrices for depth reconstruction; anything else drifts.</p>
 *
 * <p>The view matrix is <em>camera-relative</em>: the level's geometry is submitted at
 * {@code worldPos - cameraPos}, so {@code inverse(proj * view) * clip} yields a
 * camera-relative world position.</p>
 */
@Environment(EnvType.CLIENT)
public final class FrameMatrices {

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static boolean captured = false;

    private FrameMatrices() {
    }

    /** Called from {@code LevelRendererMixin} at the head of {@code renderLevel}. */
    public static void capture(Matrix4f view, Matrix4f projection) {
        VIEW.set(view);
        PROJECTION.set(projection);
        captured = true;
    }

    /** Camera-relative world -&gt; clip, exactly as the level was drawn, or {@code null}. */
    @Nullable
    public static Matrix4f getViewProjection() {
        if (!captured) return null;
        return new Matrix4f(PROJECTION).mul(VIEW);
    }

    /** The frame's view matrix (includes view bobbing), or {@code null} if none yet. */
    @Nullable
    public static Matrix4f getView() {
        return captured ? new Matrix4f(VIEW) : null;
    }

    /** The frame's projection matrix, or {@code null} if none yet. */
    @Nullable
    public static Matrix4f getProjection() {
        return captured ? new Matrix4f(PROJECTION) : null;
    }
}

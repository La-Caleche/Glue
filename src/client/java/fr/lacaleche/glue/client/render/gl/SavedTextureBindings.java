package fr.lacaleche.glue.client.render.gl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * The texture bound to one target on each of a few named units, saved before a pass binds its own and
 * put back after it.
 *
 * <p>{@link SavedGlState} saves the 2D target on units 0 to 12 for every pass, since every pass may
 * touch them. A pass that binds a 2D-array or 3D texture, or a 2D texture on a higher unit, names the
 * units it uses here instead, so no pass pays for the units it leaves alone:</p>
 *
 * <pre>{@code
 * SavedGlState saved = SavedGlState.save();
 * SavedTextureBindings arrays = SavedTextureBindings.arrays(2, 3);
 * try {
 *     // bind, draw
 * } finally {
 *     arrays.restore();
 *     saved.restore();
 * }
 * }</pre>
 *
 * <p>Restore these before the {@link SavedGlState}: restoring here changes the active unit, which that
 * restore puts back. The bindings go through raw GL, so the 2D target on a unit {@code GlStateManager}
 * caches belongs to {@link SavedGlState}, not here. Save and restore on the render thread.</p>
 */
@Environment(EnvType.CLIENT)
public final class SavedTextureBindings {

    private final int target;
    private final int[] units;
    private final int[] textures;

    private SavedTextureBindings(int target, int[] units, int[] textures) {
        this.target = target;
        this.units = units;
        this.textures = textures;
    }

    /** The 2D-array texture bound on each of {@code units}. */
    public static SavedTextureBindings arrays(int... units) {
        return save(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_BINDING_2D_ARRAY, units);
    }

    /** The 3D texture bound on each of {@code units}. */
    public static SavedTextureBindings volumes(int... units) {
        return save(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_BINDING_3D, units);
    }

    /** The 2D texture bound on each of {@code units}, for units past those {@link SavedGlState} saves. */
    public static SavedTextureBindings flat(int... units) {
        return save(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BINDING_2D, units);
    }

    private static SavedTextureBindings save(int target, int binding, int[] units) {
        int[] textures = new int[units.length];
        for (int index = 0; index < units.length; index++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + units[index]);
            textures[index] = GL11.glGetInteger(binding);
        }
        return new SavedTextureBindings(target, units.clone(), textures);
    }

    /** Binds every saved texture back on its unit, leaving the last unit active. */
    public void restore() {
        for (int index = 0; index < units.length; index++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + units[index]);
            GL11.glBindTexture(target, textures[index]);
        }
    }
}

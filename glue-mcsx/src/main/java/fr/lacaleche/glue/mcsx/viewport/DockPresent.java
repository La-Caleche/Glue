package fr.lacaleche.glue.mcsx.viewport;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.textures.GpuTextureView;
import fr.lacaleche.glue.mcsx.surface.ExternalSurfaceHost;
import icyllis.arc3d.opengl.GLTexture;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL33C;

import java.util.List;

/**
 * The embedding-mode present path, replacing what the vanilla GUI pipeline does for Modern UI
 * when the framebuffer is pinned to the viewport pane.
 *
 * <p>Per frame: {@code presentTexture} is cancelled and {@link #blitGameToPane} clears the real
 * backbuffer and blits the pane-sized game image into the pane's sub-rectangle; then, after
 * {@code RenderTarget.blitToScreen}, {@link #drawFullRes} draws any external-surface textures and
 * the Modern UI layer as textured quads over the whole window at full resolution. The UI layer is
 * premultiplied (same reason the normal path uses {@code GUI_TEXTURED_PREMULTIPLIED_ALPHA}) and
 * V-flipped once — Arc3D targets are upper-left origin, the backbuffer lower-left.
 *
 * <p>The layer texture is ref'd at {@link #submitUiLayer} and unref'd after the draw: the host
 * closes its own wrapper at end-of-render-tick, which runs before this draw, so this ref is what
 * keeps the texture alive across the gap.
 */
public final class DockPresent {

    private static final Logger LOGGER = LogManager.getLogger("MCSX-DockPresent");

    /** The backdrop behind the dock UI where nothing else draws. */
    private static final float CLEAR_R = 0.039f;
    private static final float CLEAR_G = 0.055f;
    private static final float CLEAR_B = 0.078f;

    private static final String VERTEX_SHADER = """
            #version 330 core
            uniform vec4 uRect;
            uniform vec2 uV;
            out vec2 texCoord;
            void main() {
                vec2 unit = vec2(gl_VertexID & 1, (gl_VertexID >> 1) & 1);
                gl_Position = vec4(mix(uRect.xy, uRect.zw, unit), 0.0, 1.0);
                texCoord = vec2(unit.x, mix(uV.x, uV.y, unit.y));
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            uniform sampler2D uTex;
            in vec2 texCoord;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTex, texCoord);
            }
            """;

    private static GLTexture uiLayer;
    private static int program;
    private static int vao;
    private static int sampler;
    private static int uRect;
    private static int uV;
    private static boolean initFailed;

    private DockPresent() {
    }

    /** Takes a ref on this frame's UI layer; consumed (and released) by {@link #drawFullRes}. */
    public static void submitUiLayer(GLTexture layer) {
        layer.ref();
        if (uiLayer != null) {
            uiLayer.unref();
        }
        uiLayer = layer;
    }

    /**
     * The redirected present: clears the backbuffer to the dock backdrop and blits the game into
     * the viewport pane. Runs inside the cancelled {@code presentTexture}, so scissor is already
     * being torn down and nothing else touches the backbuffer until {@link #drawFullRes}.
     */
    public static void blitGameToPane(GpuTextureView texture, GlDevice device, int scratchFbo) {
        ViewportBounds b = ViewportEmbedding.bounds();
        if (b == null) {
            return;
        }
        // the GL bottom edge comes from the window's height *now*, so an OS resize can never
        // displace the pane while the UI is still relaying out
        int glY = b.glY(Minecraft.getInstance().getWindow().getScreenHeight());
        GlStateManager._disableScissorTest();
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._glBindFramebuffer(GL33C.GL_FRAMEBUFFER, 0);
        GL33C.glClearColor(CLEAR_R, CLEAR_G, CLEAR_B, 1.0f);
        GlStateManager._clear(GL33C.GL_COLOR_BUFFER_BIT);
        GlStateManager._viewport(b.x(), glY, b.width(), b.height());
        device.directStateAccess().bindFrameBufferTextures(scratchFbo,
                ((GlTexture) texture.texture()).glId(), 0, 0, 0);
        device.directStateAccess().blitFrameBuffers(scratchFbo, 0,
                0, 0, texture.getWidth(0), texture.getHeight(0),
                b.x(), glY, b.x() + b.width(), glY + b.height(),
                GL33C.GL_COLOR_BUFFER_BIT, GL33C.GL_NEAREST);
    }

    /**
     * Draws external surfaces and then the UI layer over the whole window. Runs after the main
     * target's blit (i.e. after {@link #blitGameToPane}), directly before the buffer swap.
     */
    public static void drawFullRes(Minecraft minecraft, float deltaTick) {
        GLTexture layer = uiLayer;
        uiLayer = null;
        try {
            if (!ViewportEmbedding.isActive() || !ensureProgram()) {
                return;
            }
            // render surface sources first: they run their own passes and would trample the
            // quad-drawing state set up below
            List<ExternalSurfaceHost.SurfaceDraw> surfaces =
                    ExternalSurfaceHost.getInstance().renderDirect(deltaTick);

            Window window = minecraft.getWindow();
            int screenW = window.getScreenWidth();
            int screenH = window.getScreenHeight();
            GlStateManager._glBindFramebuffer(GL33C.GL_FRAMEBUFFER, 0);
            GlStateManager._viewport(0, 0, screenW, screenH);
            GlStateManager._disableScissorTest();
            GlStateManager._disableDepthTest();
            GlStateManager._disableCull();
            GlStateManager._colorMask(true, true, true, true);
            GlStateManager._enableBlend();
            GlStateManager._glUseProgram(program);
            GL33C.glBindVertexArray(vao);
            GlStateManager._activeTexture(GL33C.GL_TEXTURE0);
            GL33C.glBindSampler(0, sampler);

            // surfaces sit below the UI (straight alpha); their targets are lower-left origin
            GlStateManager._blendFuncSeparate(GL33C.GL_SRC_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA,
                    GL33C.GL_ONE, GL33C.GL_ZERO);
            for (ExternalSurfaceHost.SurfaceDraw draw : surfaces) {
                GlStateManager._bindTexture(((GlTexture) draw.texture().texture()).glId());
                setQuad(draw.x(), draw.y(), draw.w(), draw.h(), screenW, screenH, 0f, 1f);
                GL33C.glDrawArrays(GL33C.GL_TRIANGLE_STRIP, 0, 4);
            }

            // the UI layer on top, premultiplied, upper-left origin
            if (layer != null) {
                GlStateManager._blendFuncSeparate(GL33C.GL_ONE, GL33C.GL_ONE_MINUS_SRC_ALPHA,
                        GL33C.GL_ONE, GL33C.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager._bindTexture(layer.getHandle());
                setQuad(0, 0, screenW, screenH, screenW, screenH, 1f, 0f);
                GL33C.glDrawArrays(GL33C.GL_TRIANGLE_STRIP, 0, 4);
            }
        } finally {
            if (layer != null) {
                layer.unref();
            }
            GL33C.glBindVertexArray(0);
            GlStateManager._glUseProgram(0);
            BlazeStateSync.resetSamplersBlendTextures();
            // this pass and the surface sources above it ran raw GL; leave the caches truthful
            BlazeStateSync.resyncAfterRawGl();
        }
    }

    /** Rect in UI window pixels (top-left origin) to NDC, with the V range for this texture. */
    private static void setQuad(int x, int y, int w, int h, int screenW, int screenH,
                                float v0, float v1) {
        float x0 = 2f * x / screenW - 1f;
        float x1 = 2f * (x + w) / screenW - 1f;
        float yTop = 1f - 2f * y / screenH;
        float yBottom = 1f - 2f * (y + h) / screenH;
        GL33C.glUniform4f(uRect, x0, yBottom, x1, yTop);
        GL33C.glUniform2f(uV, v0, v1);
    }

    private static boolean ensureProgram() {
        if (program != 0) {
            return true;
        }
        if (initFailed) {
            return false;
        }
        try {
            int vs = compile(GL33C.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fs = compile(GL33C.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            int p = GL33C.glCreateProgram();
            GL33C.glAttachShader(p, vs);
            GL33C.glAttachShader(p, fs);
            GL33C.glLinkProgram(p);
            GL33C.glDeleteShader(vs);
            GL33C.glDeleteShader(fs);
            if (GL33C.glGetProgrami(p, GL33C.GL_LINK_STATUS) == GL33C.GL_FALSE) {
                throw new IllegalStateException(GL33C.glGetProgramInfoLog(p));
            }
            uRect = GL33C.glGetUniformLocation(p, "uRect");
            uV = GL33C.glGetUniformLocation(p, "uV");
            GlStateManager._glUseProgram(p);
            GL33C.glUniform1i(GL33C.glGetUniformLocation(p, "uTex"), 0);
            GlStateManager._glUseProgram(0);
            vao = GL33C.glGenVertexArrays();
            sampler = GL33C.glGenSamplers();
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
            GL33C.glSamplerParameteri(sampler, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
            program = p;
            return true;
        } catch (RuntimeException e) {
            initFailed = true;
            LOGGER.error("Failed to build the dock present program; the dock UI will not draw", e);
            return false;
        }
    }

    private static int compile(int type, String source) {
        int shader = GL33C.glCreateShader(type);
        GL33C.glShaderSource(shader, source);
        GL33C.glCompileShader(shader);
        if (GL33C.glGetShaderi(shader, GL33C.GL_COMPILE_STATUS) == GL33C.GL_FALSE) {
            String log = GL33C.glGetShaderInfoLog(shader);
            GL33C.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }
}

package fr.lacaleche.glue.gametest;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import fr.lacaleche.glue.gametest.internal.film.FilmView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Films the game from scripted camera paths into a video file, without a person at the keyboard.
 *
 * <p>Film time drives the game: before each frame the recorder ticks the game up to that frame's
 * moment, then renders it with a fixed partial tick, reads it back and hands it to ffmpeg. The output
 * is the same whatever the machine's speed; recording runs at real time or slower. While recording,
 * the window has the film's size, the HUD and hand are hidden and view bobbing is off; closing the
 * recorder restores them. Call it on the Fabric test thread, with a world loaded and no screen open.
 */
@Environment(EnvType.CLIENT)
@SuppressWarnings("PMD.TestClassWithoutTestCases") // Test support, not a JUnit test class.
public final class FilmRecorder implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-gametest");
    private static final int TICKS_PER_SECOND = 20;

    private final ClientGameTestContext context;
    private final Path output;
    private final int width;
    private final int height;
    private final int fps;
    private final int restoredWidth;
    private final int restoredHeight;
    private final boolean restoredHideGui;
    private final boolean restoredBobView;
    private final byte[] pixels;
    private GpuBuffer readback;
    private FilmEncoder encoder;
    private long frames;
    private long ticks;
    private volatile double seconds;
    private boolean closed;

    private FilmRecorder(ClientGameTestContext context, Path output, int width, int height, int fps) {
        this.context = context;
        this.output = output;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.pixels = new byte[width * height * 4];
        int[] window = context.computeOnClient(client -> new int[]{client.getWindow().getWidth(), client.getWindow().getHeight()});
        this.restoredWidth = window[0];
        this.restoredHeight = window[1];
        this.restoredHideGui = context.computeOnClient(client -> client.options.hideGui);
        this.restoredBobView = context.computeOnClient(client -> client.options.bobView().get());
    }

    /** Finishes the file and restores the window and options, even after a failed recording. */
    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        try {
            if (this.encoder != null) this.encoder.close();
        } finally {
            this.context.runOnClient(client -> {
                FilmView.clear();
                if (this.readback != null) this.readback.close();
                client.options.hideGui = this.restoredHideGui;
                client.options.bobView().set(this.restoredBobView);
            });
            this.context.getInput().resizeWindow(this.restoredWidth, this.restoredHeight);
        }
        LOGGER.info("Filmed {} frames ({} s) to {}", this.frames, this.seconds, this.output);
    }

    /** Film time recorded so far, readable from any thread: scripted scenes can follow it. */
    public double seconds() {
        return this.seconds;
    }

    /**
     * Points the camera at {@code pose} without filming, so the chunks a shot will see can build
     * before it starts: Minecraft only compiles the sections its camera can see.
     */
    public void aim(CameraPose pose) {
        Objects.requireNonNull(pose, "pose");
        if (this.closed) throw new IllegalStateException("The recorder is closed");
        float ticksPerFrame = (float) TICKS_PER_SECOND / this.fps;
        this.context.runOnClient(client -> FilmView.show(pose, 0, ticksPerFrame));
    }

    /** Appends {@code path} to the film, one frame per {@code 1 / fps} seconds of it. */
    public void record(CameraPath path) {
        Objects.requireNonNull(path, "path");
        if (this.closed) throw new IllegalStateException("The recorder is closed");
        long count = Math.max(1, Math.round(path.seconds() * this.fps));
        for (long frame = 0; frame < count; frame++) {
            this.shoot(path.at((double) frame / this.fps));
        }
    }

    private void shoot(CameraPose pose) {
        double tickTime = (double) this.frames * TICKS_PER_SECOND / this.fps;
        long tick = (long) Math.floor(tickTime);
        while (this.ticks < tick) {
            this.context.waitTick();
            this.ticks++;
        }
        float partialTick = (float) (tickTime - tick);
        float ticksPerFrame = (float) TICKS_PER_SECOND / this.fps;
        this.context.runOnClient(client -> {
            FilmView.show(pose, partialTick, ticksPerFrame);
            this.render(client);
        });
        this.encoder.write(this.pixels);
        this.frames++;
        this.seconds = (double) this.frames / this.fps;
    }

    @SuppressWarnings("PMD.CloseResource") // The main render target owns its color texture.
    private void render(Minecraft client) {
        RenderTarget target = client.getMainRenderTarget();
        if (target.width != this.width || target.height != this.height) {
            throw new IllegalStateException("The framebuffer is " + target.width + "x" + target.height
                    + ", not the film's " + this.width + "x" + this.height);
        }
        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder commands = device.createCommandEncoder();
        GpuTexture color = target.getColorTexture();
        commands.clearColorAndDepthTextures(color, 0, target.getDepthTexture(), 1.0);
        client.gameRenderer.render(FilmView.delta(), true);
        boolean[] read = new boolean[1];
        commands.copyTextureToBuffer(color, this.readback, 0, () -> {
            try (GpuBuffer.MappedView view = commands.mapBuffer(this.readback, true, false)) {
                view.data().get(0, this.pixels);
            }
            read[0] = true;
        }, 0);
        GL11.glFinish();
        RenderSystem.executePendingTasks();
        if (!read[0]) throw new IllegalStateException("The frame readback did not complete");
    }

    private void open() {
        this.context.getInput().resizeWindow(this.width, this.height);
        this.context.runOnClient(client -> {
            client.options.hideGui = true;
            client.options.bobView().set(false);
            RenderTarget target = client.getMainRenderTarget();
            GpuTexture color = target.getColorTexture();
            if (color == null || color.getFormat().pixelSize() != 4) {
                throw new IllegalStateException("The main framebuffer is not RGBA8");
            }
            this.readback = RenderSystem.getDevice().createBuffer(() -> "Glue film readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, this.pixels.length);
        });
        this.encoder = FilmEncoder.open(this.output, this.width, this.height, this.fps);
    }

    /**
     * Starts a film. A relative {@code output} resolves against the game directory; its extension
     * picks the container: mp4, mov, mkv (H.264) or avi (MJPEG). ffmpeg must be on the PATH or named
     * by the {@code glue.film.ffmpeg} system property.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException") // Any failure must restore the window before it propagates.
    public static FilmRecorder start(ClientGameTestContext context, Path output, int width, int height, int fps) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(output, "output");
        if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) {
            throw new IllegalArgumentException("Film size must be positive and even: " + width + "x" + height);
        }
        if (fps <= 0 || fps > TICKS_PER_SECOND * 10) throw new IllegalArgumentException("fps must lie in [1, 200]: " + fps);
        FilmEncoder.command("ffmpeg", output, width, height, fps);
        Path file = output.isAbsolute() ? output : context.computeOnClient(client -> client.gameDirectory.toPath()).resolve(output);
        FilmRecorder recorder = new FilmRecorder(context, file.normalize(), width, height, fps);
        try {
            recorder.open();
        } catch (RuntimeException | Error failure) {
            try {
                recorder.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        return recorder;
    }
}

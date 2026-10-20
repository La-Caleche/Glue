package fr.lacaleche.glue.gametest;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * An ffmpeg process fed raw RGBA frames on its standard input, bottom row first as OpenGL reads them.
 * The container follows the output's extension; ffmpeg's messages go to a log beside the output.
 */
final class FilmEncoder implements AutoCloseable {

    /** System property naming the ffmpeg executable; {@code ffmpeg} on the PATH otherwise. */
    static final String FFMPEG_PROPERTY = "glue.film.ffmpeg";

    private static final long FINISH_MINUTES = 10;

    private final Process process;
    private final OutputStream input;
    private final Path log;

    private FilmEncoder(Process process, Path log) {
        this.process = process;
        this.input = process.getOutputStream();
        this.log = log;
    }

    @Override
    public void close() {
        try {
            this.input.close();
            if (!this.process.waitFor(FINISH_MINUTES, TimeUnit.MINUTES)) {
                this.process.destroyForcibly();
                throw new IllegalStateException("ffmpeg did not finish; see " + this.log);
            }
        } catch (IOException exception) {
            this.process.destroyForcibly();
            throw new UncheckedIOException("ffmpeg stopped early; see " + this.log, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.process.destroyForcibly();
            throw new IllegalStateException("Interrupted while ffmpeg finished", exception);
        }
        if (this.process.exitValue() != 0) {
            throw new IllegalStateException("ffmpeg failed with " + this.process.exitValue() + "; see " + this.log);
        }
    }

    void write(byte[] frame) {
        try {
            this.input.write(frame);
        } catch (IOException exception) {
            throw new UncheckedIOException("ffmpeg stopped accepting frames; see " + this.log, exception);
        }
    }

    static FilmEncoder open(Path output, int width, int height, int fps) {
        List<String> command = command(System.getProperty(FFMPEG_PROPERTY, "ffmpeg"), output, width, height, fps);
        Path log = output.resolveSibling(output.getFileName() + ".log");
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(log.toFile())
                    .start();
            return new FilmEncoder(process, log);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start ffmpeg (" + command.getFirst() + "); install it or set -D"
                    + FFMPEG_PROPERTY, exception);
        }
    }

    /** The ffmpeg command line for {@code output}; rejects containers it has no codec for. */
    static List<String> command(String ffmpeg, Path output, int width, int height, int fps) {
        String name = output.getFileName().toString();
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        List<String> command = new ArrayList<>(List.of(ffmpeg, "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "rgba", "-s", width + "x" + height, "-r", Integer.toString(fps),
                "-i", "-", "-vf", "vflip"));
        switch (extension) {
            case "mp4", "mov", "mkv" -> command.addAll(List.of("-c:v", "libx264", "-preset", "medium", "-crf", "16",
                    "-pix_fmt", "yuv420p"));
            case "avi" -> command.addAll(List.of("-c:v", "mjpeg", "-q:v", "3", "-pix_fmt", "yuvj420p"));
            default -> throw new IllegalArgumentException("Unsupported film container (mp4, mov, mkv or avi): " + name);
        }
        if (!"mkv".equals(extension) && !"avi".equals(extension)) command.addAll(List.of("-movflags", "+faststart"));
        command.add(output.toString());
        return command;
    }
}

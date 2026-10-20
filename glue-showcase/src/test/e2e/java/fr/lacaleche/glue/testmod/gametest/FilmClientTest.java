package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.gametest.CameraPath;
import fr.lacaleche.glue.gametest.CameraPose;
import fr.lacaleche.glue.gametest.ClientTestSpec;
import fr.lacaleche.glue.gametest.FilmRecorder;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A half orbit round the spawn point, then a crane up, filmed to {@code films/showcase.mp4}; needs ffmpeg. */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
@ClientTestSpec(value = "film", explicitOnly = true)
public final class FilmClientTest extends WorldClientTest {

    private static final Path OUTPUT = Path.of("films", "showcase.mp4");

    @Override
    protected void test() {
        Vec3 center = this.context.computeOnClient(client -> client.player.position());
        CameraPath orbit = CameraPath.orbit(center, 12, 6, 0, 180, 70, 3);
        CameraPose top = CameraPose.lookingAt(center.add(0, 30, -4), center, 70);
        CameraPath film = orbit.then(CameraPath.move(orbit.at(orbit.seconds()), top, 2));
        try (FilmRecorder recorder = FilmRecorder.start(this.context, OUTPUT, 1280, 720, 30)) {
            recorder.aim(film.at(0));
            this.world.getClientWorld().waitForChunksRender();
            recorder.record(film);
            require(Math.abs(recorder.seconds() - film.seconds()) < 0.05, "Filmed " + recorder.seconds() + " s");
        }
        Path file = this.context.computeOnClient(client -> client.gameDirectory.toPath().resolve(OUTPUT));
        try {
            require(Files.size(file) > 0, "The film is empty: " + file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}

package fr.lacaleche.glue.gametest;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilmEncoderTest {

    @Test
    void flipsRawFramesIntoH264ForMp4() {
        List<String> command = FilmEncoder.command("ffmpeg", Path.of("films", "a.MP4"), 1920, 1080, 30);

        assertTrue(command.containsAll(List.of("rgba", "1920x1080", "vflip", "libx264", "+faststart")));
        assertTrue(command.getLast().endsWith("a.MP4"));
    }

    @Test
    void writesMkvAndAviWithoutTheMp4Atom() {
        List<String> mkv = FilmEncoder.command("ffmpeg", Path.of("a.mkv"), 64, 64, 30);
        List<String> avi = FilmEncoder.command("ffmpeg", Path.of("a.avi"), 64, 64, 30);

        assertTrue(mkv.contains("libx264"));
        assertFalse(mkv.contains("-movflags"));
        assertTrue(avi.contains("mjpeg"));
        assertFalse(avi.contains("-movflags"));
    }

    @Test
    void rejectsAContainerWithoutCodec() {
        assertThrows(IllegalArgumentException.class, () -> FilmEncoder.command("ffmpeg", Path.of("a.gif"), 64, 64, 30));
        assertThrows(IllegalArgumentException.class, () -> FilmEncoder.command("ffmpeg", Path.of("film"), 64, 64, 30));
    }
}

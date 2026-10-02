package fr.lacaleche.glue.client.file;

import org.junit.jupiter.api.Test;
import org.lwjgl.util.nfd.NativeFileDialog;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two decisions NFD hands back that must not be guessed at: whether a dialog was cancelled or
 * failed, and which filters can be given to the native library at all. Both are pure functions, so
 * they are checked here without a native picker; the result codes are compile-time constants, so
 * nothing in this test loads LWJGL's natives.
 */
class NFDFileDialogTest {

    private static final Supplier<String> NO_DETAIL = () -> null;

    @Test
    void cancellationIsAnEmptyResult() {
        Optional<String> result = NFDFileDialog.cancellationOrFailure(
                NativeFileDialog.NFD_CANCEL, "opening a file", NO_DETAIL);

        assertEquals(Optional.empty(), result);
    }

    @Test
    void aNativeErrorFailsAndKeepsItsDetail() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> NFDFileDialog.cancellationOrFailure(
                        NativeFileDialog.NFD_ERROR, "saving a file", () -> "GDBus call timed out"));

        assertTrue(failure.getMessage().contains("saving a file"), failure.getMessage());
        assertTrue(failure.getMessage().contains("GDBus call timed out"), failure.getMessage());
    }

    @Test
    void aNativeErrorWithoutDetailStillFails() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> NFDFileDialog.cancellationOrFailure(NativeFileDialog.NFD_ERROR, "selecting a folder", NO_DETAIL));

        assertEquals("Native file dialog failed while selecting a folder", failure.getMessage());
    }

    @Test
    void anUnknownResultIsAFailureRatherThanACancellation() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> NFDFileDialog.cancellationOrFailure(42, "opening a file", NO_DETAIL));

        assertTrue(failure.getMessage().contains("42"), failure.getMessage());
    }

    @Test
    void ordinaryExtensionsBecomeOneSpecification() {
        List<NFDFileDialog.FilterSpec> specs = NFDFileDialog.normalize(
                new FileDialogs.FileFilter[]{new FileDialogs.FileFilter("Images", "png", "jpg")});

        assertEquals(List.of(new NFDFileDialog.FilterSpec("Images", "png,jpg")), specs);
    }

    @Test
    void decoratedExtensionsKeepTheirNormalization() {
        List<NFDFileDialog.FilterSpec> specs = NFDFileDialog.normalize(
                new FileDialogs.FileFilter[]{new FileDialogs.FileFilter("Images", "*.png", ".jpg", "*gif", " bmp ")});

        assertEquals(List.of(new NFDFileDialog.FilterSpec("Images", "png,jpg,gif,bmp")), specs);
    }

    @Test
    void aBareWildcardProducesNoFilterAtAll() {
        List<NFDFileDialog.FilterSpec> specs = NFDFileDialog.normalize(
                new FileDialogs.FileFilter[]{new FileDialogs.FileFilter("All Files", "*")});

        assertEquals(List.of(), specs);
    }

    @Test
    void aFilterWithoutUsableExtensionsIsDropped() {
        List<NFDFileDialog.FilterSpec> specs = NFDFileDialog.normalize(new FileDialogs.FileFilter[]{
                new FileDialogs.FileFilter("Nothing"),
                new FileDialogs.FileFilter("Blanks", "", "  ", ".")
        });

        assertEquals(List.of(), specs);
    }

    @Test
    void aMixedListKeepsOnlyTheUsableFilters() {
        List<NFDFileDialog.FilterSpec> specs = NFDFileDialog.normalize(new FileDialogs.FileFilter[]{
                new FileDialogs.FileFilter("Text Files", "txt", "", "md"),
                new FileDialogs.FileFilter("All Files", "*")
        });

        assertEquals(List.of(new NFDFileDialog.FilterSpec("Text Files", "txt,md")), specs);
    }

    @Test
    void everySpellingOfAllFilesProducesNoFilter() {
        for (String wildcard : List.of("*", "*.*", ".*", "**", ".", ",", ",,")) {
            assertEquals(List.of(),
                    NFDFileDialog.normalize(new FileDialogs.FileFilter[]{new FileDialogs.FileFilter("All", wildcard)}),
                    wildcard);
        }
    }

    @Test
    void commaSeparatedExtensionsAreSplitRatherThanPassedOn() {
        assertEquals(List.of(new NFDFileDialog.FilterSpec("Images", "png,jpg")),
                NFDFileDialog.normalize(new FileDialogs.FileFilter[]{new FileDialogs.FileFilter("Images", "png,jpg")}));
    }

    @Test
    void emptyCommaTokensNeverReachTheSpecification() {
        for (String written : List.of("png,", ",png", "png,,", " , png , ")) {
            assertEquals(List.of(new NFDFileDialog.FilterSpec("Images", "png")),
                    NFDFileDialog.normalize(new FileDialogs.FileFilter[]{new FileDialogs.FileFilter("Images", written)}),
                    written);
        }
    }

    @Test
    void noSpecificationIsMalformed() {
        List<NFDFileDialog.FilterSpec> specs = NFDFileDialog.normalize(new FileDialogs.FileFilter[]{
                new FileDialogs.FileFilter("Mixed", "*.png", "*", ",", "jpg,", "", "*.*"),
                new FileDialogs.FileFilter("All Files", "*.*")
        });

        assertEquals(List.of(new NFDFileDialog.FilterSpec("Mixed", "png,jpg")), specs);
        for (NFDFileDialog.FilterSpec spec : specs) {
            assertFalse(spec.spec().isBlank(), spec.toString());
            assertFalse(spec.spec().startsWith(","), spec.toString());
            assertFalse(spec.spec().endsWith(","), spec.toString());
            assertFalse(spec.spec().contains(",,"), spec.toString());
        }
    }

    @Test
    void noFiltersMeansNoNativeBuffer() {
        assertEquals(List.of(), NFDFileDialog.normalize(new FileDialogs.FileFilter[0]));
        assertEquals(List.of(), NFDFileDialog.normalize(null));
    }
}

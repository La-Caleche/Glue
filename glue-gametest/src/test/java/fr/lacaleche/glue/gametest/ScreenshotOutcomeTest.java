package fr.lacaleche.glue.gametest;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vanilla reports a written and a failed capture through the same consumer, so the classification
 * below is the only thing standing between a failed capture and a step that passes without a PNG.
 */
class ScreenshotOutcomeTest {

    @Test
    void readsAWrittenCapture() {
        TestContext.ScreenshotOutcome outcome = GameTestRunner.outcomeOf("01-hud.png",
                Component.translatable("screenshot.success", Component.literal("01-hud.png")));

        assertTrue(outcome.saved());
        assertEquals("01-hud.png", outcome.detail());
    }

    @Test
    void readsAFailedCaptureAndKeepsItsReason() {
        TestContext.ScreenshotOutcome outcome = GameTestRunner.outcomeOf("01-hud.png",
                Component.translatable("screenshot.failure", "disk full"));

        assertFalse(outcome.saved());
        assertTrue(outcome.detail().contains("disk full"), outcome.detail());
    }

    @Test
    void treatsAnUnrecognizedMessageAsAFailure() {
        TestContext.ScreenshotOutcome outcome =
                GameTestRunner.outcomeOf("01-hud.png", Component.literal("something else entirely"));

        assertFalse(outcome.saved());
    }
}

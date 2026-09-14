package fr.lacaleche.jcef;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FrameMailboxTest {

    @Test
    void skippedFramesAccumulateDamageAndPreserveBorrowedPixels() {
        try (FrameMailbox mailbox = new FrameMailbox()) {
            ByteBuffer original = pixels(0, 0, 0, 0);
            mailbox.capture(null, original, 4, 1);
            FrameMailbox.Transfer first = mailbox.take();
            mailbox.recycle(first);
            ByteBuffer second = pixels(0xFF112233, 0, 0, 0);
            mailbox.capture(new Rectangle[] { new Rectangle(0, 0, 1, 1) }, second, 4, 1);
            second.putInt(0, 0);
            mailbox.capture(new Rectangle[] { new Rectangle(3, 0, 1, 1) }, pixels(0xFF112233, 0, 0, 0xFF445566), 4, 1);
            FrameMailbox.Transfer merged = mailbox.take();
            assertNotNull(merged);
            assertEquals(new FrameMailbox.Region(0, 0, 4, 1), merged.region());
            assertEquals(0xFF112233, merged.pixels().order(ByteOrder.LITTLE_ENDIAN).getInt(0));
            assertEquals(0xFF445566, merged.pixels().getInt(12));
            assertEquals(3, merged.sequence());
            assertNull(mailbox.take());
        }
    }

    @Test
    void partialRowsArePackedAndResizeForcesACompleteImage() {
        try (FrameMailbox mailbox = new FrameMailbox()) {
            mailbox.capture(null, pixels(1, 2, 3, 4, 5, 6), 3, 2);
            mailbox.recycle(mailbox.take());
            mailbox.capture(new Rectangle[] { new Rectangle(1, 0, 1, 2) }, pixels(1, 20, 3, 4, 50, 6), 3, 2);
            FrameMailbox.Transfer partial = mailbox.take();
            assertEquals(8, partial.pixels().remaining());
            assertEquals(20, partial.pixels().order(ByteOrder.LITTLE_ENDIAN).getInt(0));
            assertEquals(50, partial.pixels().getInt(4));
            mailbox.recycle(partial);
            mailbox.capture(new Rectangle[] { new Rectangle(0, 0, 1, 1) }, pixels(7, 8), 2, 1);
            assertEquals(new FrameMailbox.Region(0, 0, 2, 1), mailbox.take().region());
        }
    }

    @Test
    void closedMailboxRejectsLateCallbacksWithoutAllocating() {
        FrameMailbox mailbox = new FrameMailbox();
        mailbox.close();
        mailbox.capture(null, pixels(10), 1, 1);
        assertEquals(0, mailbox.capturedFrames());
        assertNull(mailbox.take());
    }

    private static ByteBuffer pixels(int... values) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : values) pixels.putInt(value);
        return pixels.flip();
    }
}

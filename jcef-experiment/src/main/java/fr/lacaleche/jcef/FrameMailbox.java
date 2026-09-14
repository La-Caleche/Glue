package fr.lacaleche.jcef;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

/**
 * Owns the latest complete CEF image and accumulates damage until the renderer consumes it.
 * CEF's borrowed callback memory never escapes capture. GPU work runs outside this monitor.
 */
final class FrameMailbox implements AutoCloseable {

    private final ArrayDeque<ByteBuffer> transfers = new ArrayDeque<>();
    private ByteBuffer image;
    private Region pending;
    private int width;
    private int height;
    private long sequence;
    private long capturedAt;
    private long captureNanos;
    private long copiedBytes;
    private boolean closed;

    synchronized void capture(Rectangle[] rectangles, ByteBuffer source, int width, int height) {
        if (this.closed || width <= 0 || height <= 0) return;
        long bytes = (long) width * height * 4;
        if (bytes > 4096L * 4096 * 4 || source.capacity() < bytes) {
            throw new IllegalArgumentException("Invalid or oversized CEF paint surface");
        }
        long started = System.nanoTime();
        boolean resized = this.image == null || this.width != width || this.height != height;
        if (resized) {
            this.image = ByteBuffer.allocateDirect((int) bytes).order(ByteOrder.nativeOrder());
            this.width = width;
            this.height = height;
            this.pending = Region.full(width, height);
            this.transfers.clear();
        }
        Region changed = resized || rectangles == null || rectangles.length == 0
                ? Region.full(width, height) : damage(rectangles, width, height);
        if (changed == null) return;
        // One bounded rectangle also merges overlapping CEF rectangles without copying pixels twice.
        copyRegion(source, width, changed, this.image, width, changed.x, changed.y);
        this.pending = this.pending == null ? changed : this.pending.union(changed);
        this.sequence++;
        this.capturedAt = System.nanoTime();
        this.captureNanos = this.capturedAt - started;
        this.copiedBytes += changed.bytes();
    }

    synchronized Transfer take() {
        if (this.closed || this.pending == null) return null;
        Region region = this.pending;
        int bytes = region.bytes();
        ByteBuffer buffer = this.transfers.pollFirst();
        if (buffer == null || buffer.capacity() < bytes) buffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        buffer.clear().limit(bytes);
        long started = System.nanoTime();
        copyRegion(this.image, this.width, region, buffer, region.width, 0, 0);
        this.pending = null;
        return new Transfer(buffer, region, this.width, this.height, this.sequence,
                this.capturedAt, this.captureNanos, System.nanoTime() - started);
    }

    synchronized void recycle(Transfer transfer) {
        if (!this.closed && this.transfers.size() < 2) this.transfers.addLast(transfer.pixels);
    }

    synchronized void invalidate() {
        if (!this.closed && this.image != null) this.pending = Region.full(this.width, this.height);
    }

    synchronized long capturedFrames() { return this.sequence; }

    synchronized long copiedBytes() { return this.copiedBytes; }

    synchronized BufferedImage screenshot() {
        if (this.image == null) throw new IllegalStateException("No CEF paint received");
        BufferedImage result = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB_PRE);
        this.image.duplicate().clear().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
                .get(((DataBufferInt) result.getRaster().getDataBuffer()).getData());
        return result;
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        this.image = null;
        this.pending = null;
        this.transfers.clear();
    }

    private static Region damage(Rectangle[] rectangles, int width, int height) {
        Region union = null;
        for (Rectangle rectangle : rectangles) {
            if (rectangle == null || rectangle.width <= 0 || rectangle.height <= 0) continue;
            int x = Math.max(0, rectangle.x);
            int y = Math.max(0, rectangle.y);
            int right = (int) Math.min(width, (long) rectangle.x + rectangle.width);
            int bottom = (int) Math.min(height, (long) rectangle.y + rectangle.height);
            if (right <= x || bottom <= y) continue;
            Region next = new Region(x, y, right - x, bottom - y);
            union = union == null ? next : union.union(next);
        }
        return union;
    }

    private static void copyRegion(ByteBuffer source, int sourceWidth, Region region,
                                   ByteBuffer target, int targetWidth, int targetX, int targetY) {
        ByteBuffer input = source.duplicate();
        ByteBuffer output = target.duplicate();
        input.clear();
        output.clear();
        if (region.x == 0 && region.width == sourceWidth && targetWidth == sourceWidth && targetX == 0) {
            int start = region.y * sourceWidth * 4;
            input.position(start).limit(start + region.bytes());
            output.position(targetY * targetWidth * 4);
            output.put(input);
            return;
        }
        for (int row = 0; row < region.height; row++) {
            int start = ((region.y + row) * sourceWidth + region.x) * 4;
            input.limit(input.capacity()).position(start).limit(start + region.width * 4);
            output.position(((targetY + row) * targetWidth + targetX) * 4);
            output.put(input);
        }
    }

    record Transfer(ByteBuffer pixels, Region region, int width, int height, long sequence,
                    long capturedAt, long captureNanos, long stagingNanos) {
    }

    record Region(int x, int y, int width, int height) {
        static Region full(int width, int height) { return new Region(0, 0, width, height); }
        int bytes() { return this.width * this.height * 4; }
        Region union(Region other) {
            int left = Math.min(this.x, other.x);
            int top = Math.min(this.y, other.y);
            return new Region(left, top, Math.max(this.x + this.width, other.x + other.width) - left,
                    Math.max(this.y + this.height, other.y + other.height) - top);
        }
    }
}

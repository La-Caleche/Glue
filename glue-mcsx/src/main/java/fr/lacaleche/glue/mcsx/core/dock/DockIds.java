package fr.lacaleche.glue.mcsx.core.dock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Mints node and float ids for one dock workspace. Ids only need to be unique within the layout
 * they live in, so each workspace owns its own generator; persistence never stores ids — a loaded
 * layout is re-minted through the owner's generator, which is what keeps them collision-free.
 */
public final class DockIds {

    private final AtomicLong counter = new AtomicLong();

    public String next(String prefix) {
        return prefix + counter.incrementAndGet();
    }
}

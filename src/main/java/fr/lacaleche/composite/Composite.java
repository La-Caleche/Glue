package fr.lacaleche.composite;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composite cells, built on Glue's public API as a separate mod would be: Glue's own code does not
 * depend on this package.
 */
public final class Composite implements ModInitializer {

    static final Logger LOGGER = LoggerFactory.getLogger("composite");

    @Override
    public void onInitialize() {
        CompositeBlocks.register();
    }
}

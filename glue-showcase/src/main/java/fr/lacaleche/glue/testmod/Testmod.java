package fr.lacaleche.glue.testmod;

import fr.lacaleche.glue.lumos.server.PersistentLights;
import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import fr.lacaleche.glue.testmod.registries.TestBlocks;
import fr.lacaleche.glue.testmod.registries.TestDataComponents;
import fr.lacaleche.glue.testmod.registries.TestItemGroups;
import fr.lacaleche.glue.testmod.registries.TestItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The showcase's both-sides initializer. Registers everything that lives in synced registries
 * (blocks, items, data components, block entities, creative tab) so the demo content exists on
 * dedicated servers too, and opens the Lumos client request channel &mdash; closed by default
 * &mdash; to operators at permission level 4: that is what lets Glue Studio, opened through the F6
 * showcase controls, place, edit, and remove world lights. Every request is still validated server-side
 * (well-formed, near the player, dimension cap).
 */
public class Testmod implements ModInitializer {

    public static final String MOD_ID = "glue-test";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        // The registerX() methods only force class-loading: each registry class performs its
        // registrations in static field initializers, so touching the class is the registration.
        TestBlocks.registerBlocks();
        TestItems.registerItems();
        TestDataComponents.registerDataComponents();
        TestBlockEntities.registerBlockEntities();
        TestItemGroups.registerItemGroups();

        PersistentLights.allowClientRequests(PersistentLights.OPERATORS);
    }
}

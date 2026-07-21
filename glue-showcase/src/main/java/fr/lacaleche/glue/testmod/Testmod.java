package fr.lacaleche.glue.testmod;

import fr.lacaleche.glue.lumos.server.PersistentLights;
import net.fabricmc.api.ModInitializer;

/**
 * The showcase's both-sides initializer. Its one job is the Lumos server-side opt-in: the light debug
 * HUD (F12) places, edits, and removes world lights from the client, which only works because this
 * opens the client request channel &mdash; closed by default &mdash; to operators at permission
 * level 4. Every request is still validated server-side (well-formed, near the player, dimension cap).
 */
public class Testmod implements ModInitializer {

    @Override
    public void onInitialize() {
        PersistentLights.allowClientRequests(PersistentLights.OPERATORS);
    }
}

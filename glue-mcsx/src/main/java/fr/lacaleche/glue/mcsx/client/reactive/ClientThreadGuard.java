package fr.lacaleche.glue.mcsx.client.reactive;

import icyllis.modernui.core.Core;
import net.minecraft.client.Minecraft;

/**
 * Game-side confinement, the counterpart of {@link UiThreadGuard}. With a running client the owning
 * thread is Minecraft's client thread, so every server, network and worker thread is rejected;
 * without one — as in unit tests — there is no client thread to compare against and only ModernUI's
 * UI thread can still be ruled out.
 */
final class ClientThreadGuard {

    private ClientThreadGuard() {
    }

    static boolean isOnClientThread() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) return minecraft.isSameThread();

        return !Core.isOnUiThread();
    }
}

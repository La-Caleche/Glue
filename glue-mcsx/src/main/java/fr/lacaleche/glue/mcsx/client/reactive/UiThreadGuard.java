package fr.lacaleche.glue.mcsx.client.reactive;

import icyllis.modernui.core.Core;

final class UiThreadGuard {

    private UiThreadGuard() {
    }

    static void check() {
        if (Core.getUiThread() != null) {
            Core.checkUiThread();
        }
    }
}

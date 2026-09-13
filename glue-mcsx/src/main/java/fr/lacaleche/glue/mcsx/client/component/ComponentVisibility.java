package fr.lacaleche.glue.mcsx.client.component;

import icyllis.modernui.view.View;

final class ComponentVisibility {

    private ComponentVisibility() {
    }

    static void apply(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}

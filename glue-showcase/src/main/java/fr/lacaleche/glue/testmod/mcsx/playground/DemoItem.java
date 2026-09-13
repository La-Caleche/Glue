package fr.lacaleche.glue.testmod.mcsx.playground;

import fr.lacaleche.glue.mcsx.client.reactive.Signal;

import java.util.Objects;

record DemoItem(int id, Signal<String> label) {

    DemoItem {
        Objects.requireNonNull(label, "label");
    }

    static DemoItem create(int id, String label) {
        return new DemoItem(id, Signal.of(Objects.requireNonNull(label, "label")));
    }
}

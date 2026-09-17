package fr.lacaleche.glue.testmod.web.hub;

import fr.lacaleche.glue.testmod.web.WebDemos;
import fr.lacaleche.glue.testmod.web.browser.BrowserScreen;
import fr.lacaleche.glue.web.bridge.WebAction;

/** Actions of the hub page: open a demo, toggle a layer, show a toast. */
public final class HubActions {

    @WebAction("demo.open")
    void open(Demo request) {
        switch (request.name()) {
            case "lab" -> WebDemos.openLab();
            case "waypoints" -> WebDemos.openWaypoints();
            case "browser" -> BrowserScreen.open(BrowserScreen.HOME);
            default -> throw new IllegalArgumentException("There is no demo named " + request.name());
        }
    }

    @WebAction("layers.set")
    Layers set(LayerChange change) {
        switch (change.layer()) {
            case "hud" -> WebDemos.vitals().setEnabled(change.enabled());
            case "minimap" -> WebDemos.minimap().setEnabled(change.enabled());
            case "toasts" -> WebDemos.toasts().overlay().setEnabled(change.enabled());
            default -> throw new IllegalArgumentException("There is no layer named " + change.layer());
        }
        return Layers.capture();
    }

    @WebAction("toast.sample")
    void sampleToast() {
        WebDemos.toast("Sent from the hub", "Overlays stay above every screen and menu.");
    }

    record Demo(String name) {
    }

    record LayerChange(String layer, boolean enabled) {
    }

    public record Layers(boolean hud, boolean minimap, boolean toasts) {

        public static Layers capture() {
            return new Layers(WebDemos.vitals().isEnabled(), WebDemos.minimap().isEnabled(),
                    WebDemos.toasts().overlay().isEnabled());
        }
    }
}

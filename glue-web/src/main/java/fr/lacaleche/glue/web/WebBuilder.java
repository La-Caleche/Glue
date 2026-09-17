package fr.lacaleche.glue.web;

import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.bridge.WebAction;
import fr.lacaleche.glue.web.bridge.WebSlotRenderer;
import fr.lacaleche.glue.web.host.WebHud;
import fr.lacaleche.glue.web.host.WebOverlay;
import fr.lacaleche.glue.web.host.WebScreen;
import fr.lacaleche.glue.web.host.WebWidget;
import fr.lacaleche.glue.web.internal.bridge.ActionTable;
import fr.lacaleche.glue.web.internal.bridge.BridgeSettings;
import fr.lacaleche.glue.web.internal.bridge.SlotBinding;
import fr.lacaleche.glue.web.internal.bridge.WebOrigin;
import fr.lacaleche.glue.web.internal.browser.SurfaceOptions;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Options shared by every way of hosting a page: the raw {@link WebSurface}, {@link WebScreen},
 * {@link WebHud}, {@link WebOverlay} and {@link WebWidget}.
 *
 * <p>The page bridge is available to pages that import {@code https://glue-web.glue/bridge.js} from a
 * trusted origin. Addresses returned by {@link WebApp} are trusted by default; any other address
 * needs {@link #trust(URI)}. Options are copied when the host is built, opened or registered.</p>
 */
public abstract class WebBuilder<B extends WebBuilder<B>> {

    private static final Pattern STATE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");

    private final URI address;
    private final List<Object> handlers = new ArrayList<>();
    private final Map<String, Supplier<?>> states = new LinkedHashMap<>();
    private final Map<String, SlotBinding> slots = new LinkedHashMap<>();
    private WebOrigin trusted;
    private boolean bridgeDisabled;
    private int frameRate = 60;
    private boolean transparent = true;

    protected WebBuilder(URI address) {
        this.address = SurfaceOptions.address(address);
        this.trusted = WebOrigin.defaultFor(this.address);
    }

    /** Browser frame cap in 1..120; defaults to 60. */
    public B frameRate(int fps) {
        SurfaceOptions.validateFrameRate(fps);
        this.frameRate = fps;
        return this.self();
    }

    /** Transparent pages reveal what is drawn beneath them; defaults to true. */
    public B transparent(boolean transparent) {
        this.transparent = transparent;
        return this.self();
    }

    /** Grants the bridge to documents from this HTTP(S) origin only, replacing the default. */
    public B trust(URI origin) {
        this.trusted = WebOrigin.from(origin);
        this.bridgeDisabled = false;
        return this.self();
    }

    /**
     * Refuses the bridge to every document, including Glue app pages. Hosts that let users open
     * arbitrary addresses use it; it cannot be combined with actions, state or slots.
     */
    public B withoutBridge() {
        this.trusted = null;
        this.bridgeDisabled = true;
        return this.self();
    }

    /** Exposes every {@link WebAction} method of the handler. */
    public B bind(Object handler) {
        Objects.requireNonNull(handler, "handler");
        List<Object> candidates = new ArrayList<>(this.handlers);
        candidates.add(handler);
        ActionTable.compile(candidates);
        this.handlers.add(handler);
        return this.self();
    }

    /**
     * Publishes the supplier's value, encoded as JSON, under a state key. It is sampled on the client
     * thread after every client tick while a page is connected and sent only when it changes.
     */
    public B state(String key, Supplier<?> value) {
        if (key == null || !STATE_KEY.matcher(key).matches()) throw new IllegalArgumentException("Invalid state key: " + key);
        Objects.requireNonNull(value, "value");
        if (this.states.putIfAbsent(key, value) != null) throw new IllegalArgumentException("State is declared twice: " + key);
        return this.self();
    }

    /** Draws native content above the page in every element marked {@code data-glue-slot="name[:argument]"}. */
    public B slot(String name, WebSlotRenderer renderer) {
        return this.addSlot(name, new SlotBinding(SlotBinding.Layer.ABOVE, renderer));
    }

    /** Like {@link #slot}, but draws behind the page, which must leave the element transparent. */
    public B slotBehind(String name, WebSlotRenderer renderer) {
        return this.addSlot(name, new SlotBinding(SlotBinding.Layer.BEHIND, renderer));
    }

    protected abstract B self();

    /** Copies common options into a private surface builder owned by the resulting host. */
    protected final WebSurface.Builder surfaceBuilder() {
        this.validateBridge();
        WebSurface.Builder result = WebSurface.builder(this.address);
        WebBuilder<?> copy = result;
        copy.handlers.addAll(this.handlers);
        copy.states.putAll(this.states);
        copy.slots.putAll(this.slots);
        copy.trusted = this.trusted;
        copy.bridgeDisabled = this.bridgeDisabled;
        copy.frameRate = this.frameRate;
        copy.transparent = this.transparent;
        return result;
    }

    protected final boolean declaresBridge() {
        return !this.handlers.isEmpty() || !this.states.isEmpty() || !this.slots.isEmpty();
    }

    final SurfaceOptions options(int width, int height, double scale) {
        this.validateBridge();
        return new SurfaceOptions(this.address, width, height, scale, this.frameRate, this.transparent,
                new BridgeSettings(this.trusted, ActionTable.compile(this.handlers), this.states, this.slots));
    }

    private void validateBridge() {
        if (this.bridgeDisabled && (!this.handlers.isEmpty() || !this.states.isEmpty() || !this.slots.isEmpty())) {
            throw new IllegalStateException("A host without a bridge cannot declare actions, state or slots");
        }
    }

    private B addSlot(String name, SlotBinding binding) {
        SlotBinding.validateName(name);
        if (this.slots.putIfAbsent(name, binding) != null) throw new IllegalArgumentException("Slot is declared twice: " + name);
        return this.self();
    }
}

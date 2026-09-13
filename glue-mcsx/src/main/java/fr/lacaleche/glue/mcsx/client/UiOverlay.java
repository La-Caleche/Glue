package fr.lacaleche.glue.mcsx.client;

import fr.lacaleche.glue.mcsx.client.internal.OverlayHost;
import fr.lacaleche.mui.OverlayHandle;
import icyllis.modernui.fragment.Fragment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Owns one native MCSX overlay mounted beside the running game. The single overlay slot hosts either
 * an interactive workspace ({@link #mount}) or a non-interactive HUD ({@link #hud}); MCSX composites
 * whichever is mounted after the HUD and before an open screen, so consumers never register a render
 * hook.
 */
@Environment(EnvType.CLIENT)
public final class UiOverlay {

    private UiOverlay() {
    }

    /**
     * Mounts a fragment in the exclusive overlay slot. Client thread only.
     *
     * @param onUnmount invoked on the client thread during teardown, before cursor restoration
     */
    public static Mount mount(Fragment fragment, Runnable onUnmount) {
        return new Mount(OverlayHost.mount(
                Objects.requireNonNull(fragment, "fragment"),
                Objects.requireNonNull(onUnmount, "onUnmount")
        ));
    }

    /**
     * Creates a managed host for one non-interactive HUD overlay. Nothing mounts until
     * {@link Hud#mount()} is called; each mount builds a fresh fragment from the factory.
     */
    public static <F extends Fragment> Hud<F> hud(Supplier<? extends F> fragmentFactory) {
        return new Hud<>(
                Objects.requireNonNull(fragmentFactory, "fragmentFactory"),
                OverlayHost::mountHud
        );
    }

    /**
     * Whether the exclusive overlay slot is taken — by a workspace {@link #mount}, by a managed
     * {@link Hud}, or by a fragment an application mounted straight through {@code MuiApi}. It is
     * exactly the condition a further mount is rejected on, so a consumer that can be asked to open
     * twice — a keybind, a command — checks this instead of catching the rejection.
     */
    public static boolean isOccupied() {
        return OverlayHost.isOccupied();
    }

    /** Exclusive ownership of one overlay mount; {@link #close} is idempotent from any thread. */
    public static final class Mount {

        private final OverlayHost.Mount delegate;

        private Mount(OverlayHost.Mount delegate) {
            this.delegate = delegate;
        }

        /**
         * Registers the handler that runs on ModernUI's UI thread before an idle Escape returns to
         * Minecraft. It returns whether it consumed Escape.
         */
        public void setEscapeHandler(BooleanSupplier handler) {
            this.delegate.setEscapeHandler(Objects.requireNonNull(handler, "handler"));
        }

        public boolean acceptsWork() {
            return this.delegate.acceptsWork();
        }

        /** Closes this mount from any thread. Repeated calls are no-ops. */
        public void close() {
            this.delegate.close();
        }
    }

    /**
     * A managed host for one non-interactive HUD overlay: {@link #mount()} builds a fresh fragment
     * and takes the exclusive overlay slot, {@link #unmount()} releases it, and remounting after an
     * unmount builds another fragment. Client thread only. The mounted fragment receives no input
     * and renders automatically after the HUD.
     */
    public static final class Hud<F extends Fragment> {

        private final Supplier<? extends F> fragmentFactory;
        private final Function<Fragment, OverlayHandle> mounter;
        private OverlayHandle handle;
        private F fragment;

        Hud(Supplier<? extends F> fragmentFactory, Function<Fragment, OverlayHandle> mounter) {
            this.fragmentFactory = fragmentFactory;
            this.mounter = mounter;
        }

        /**
         * Mounts a fresh fragment from the factory. A no-op while this host is already mounted.
         *
         * @throws IllegalStateException when another overlay — a workspace or another host — holds
         *                               the single overlay slot
         */
        public void mount() {
            if (this.isMounted()) return;

            F next = Objects.requireNonNull(this.fragmentFactory.get(), "fragmentFactory result");
            this.handle = Objects.requireNonNull(this.mounter.apply(next), "overlay handle");
            this.fragment = next;
        }

        /** Unmounts this host's overlay. A no-op while nothing is mounted. */
        public void unmount() {
            OverlayHandle active = this.handle;
            if (active == null) return;

            active.close();
            this.handle = null;
        }

        public boolean isMounted() {
            return this.handle != null && !this.handle.isClosed();
        }

        /**
         * The fragment built for the most recent {@link #mount()}, retained through the following
         * unmount for inspection.
         *
         * @throws IllegalStateException when this host has never mounted
         */
        public F fragment() {
            F current = this.fragment;
            if (current == null) throw new IllegalStateException("The overlay has not been mounted");
            return current;
        }
    }
}

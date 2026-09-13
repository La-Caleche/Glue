package fr.lacaleche.glue.mcsx.client;

import fr.lacaleche.mui.MuiApi;
import fr.lacaleche.mui.ScreenCallback;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Base for single-use MCSX screens composed through {@link Ui}. Subclasses may use the ordinary
 * Fragment lifecycle and only need to implement {@link #create(Ui)}.
 */
@Environment(EnvType.CLIENT)
public abstract class UiScreen extends Fragment implements ScreenCallback {

    private final BiConsumer<UiScreen, Screen> opener;
    private boolean opened;
    private boolean destroyed;

    protected UiScreen() {
        this(MuiApi::openScreen);
    }

    UiScreen(BiConsumer<UiScreen, Screen> opener) {
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    @Override
    public final View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        return this.createView(this.requireContext());
    }

    @Override
    public void onDestroy() {
        this.destroyed = true;
        super.onDestroy();
    }

    @Override
    public final boolean shouldClose() {
        return this.canClose();
    }

    @Override
    public final boolean isPauseScreen() {
        return this.pausesGame();
    }

    @Override
    public final boolean hasDefaultBackground() {
        return this.drawsDefaultBackground();
    }

    public final void open() {
        this.open(null);
    }

    public final void open(Screen previousScreen) {
        if (this.opened) throw new IllegalStateException("UiScreen has already been opened");
        if (this.destroyed) throw new IllegalStateException("UiScreen is closed");

        this.opened = true;
        this.opener.accept(this, previousScreen);
    }

    /** Creates this screen's root View. The returned View must not be null. */
    protected abstract View create(Ui ui);

    protected boolean pausesGame() {
        return false;
    }

    protected boolean drawsDefaultBackground() {
        return true;
    }

    protected boolean canClose() {
        return true;
    }

    final View createView(Context context) {
        View view = this.create(Ui.with(context));
        return Objects.requireNonNull(view, "UiScreen create returned null");
    }
}

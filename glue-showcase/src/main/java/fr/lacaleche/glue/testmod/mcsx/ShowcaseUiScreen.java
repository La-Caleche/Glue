package fr.lacaleche.glue.testmod.mcsx;

import fr.lacaleche.glue.mcsx.client.UiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** UiScreen with showcase-local host tracking for race-safe Back actions. */
public abstract class ShowcaseUiScreen extends UiScreen {

    private volatile Screen host;
    private Screen previousScreen;
    private volatile boolean closeRequested;

    public final void show() {
        this.show(null);
    }

    public final void show(Screen previousScreen) {
        this.open(previousScreen);
        this.previousScreen = previousScreen;
        this.host = Minecraft.getInstance().screen;
        if (this.host == null) throw new IllegalStateException("MCSX did not install a screen host");
    }

    protected final void back() {
        if (this.closeRequested) return;

        this.closeRequested = true;
        Minecraft minecraft = Minecraft.getInstance();
        Screen expected = this.host;
        minecraft.schedule(() -> {
            if (minecraft.screen != expected) {
                this.closeRequested = false;
                return;
            }
            if (!this.shouldClose()) {
                this.closeRequested = false;
                return;
            }
            minecraft.setScreen(this.previousScreen);
        });
    }
}

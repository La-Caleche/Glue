package fr.lacaleche.glue.client.ui;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** A button that runs an action, showing its text. */
public final class UiButton extends FlatButton {

    private final Runnable action;

    public UiButton(Component text, Runnable action) {
        super(text);
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override
    public void onPress() {
        this.action.run();
    }

    @Override
    @Nullable Component value() {
        return null;
    }
}

package fr.lacaleche.glue.mcsx.client.component;

import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

/**
 * Drives the real attach and detach overrides in tests, which cannot construct the ModernUI
 * AttachInfo that {@code dispatchAttachedToWindow} requires.
 *
 * <p>Dispatch order mirrors ViewGroup: attach parent first, detach children first. Views outside
 * the MCSX component types are traversed but have no lifecycle of their own.
 */
final class Lifecycles {

    private Lifecycles() {
    }

    static void attach(View view) {
        switch (view) {
            case TaffyLayout layout -> layout.onAttachedToWindow();
            case Button button -> button.onAttachedToWindow();
            case Checkbox checkbox -> checkbox.onAttachedToWindow();
            case Text text -> text.onAttachedToWindow();
            case TextField field -> field.onAttachedToWindow();
            default -> {
            }
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                attach(group.getChildAt(index));
            }
        }
    }

    static void detach(View view) {
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                detach(group.getChildAt(index));
            }
        }
        switch (view) {
            case TaffyLayout layout -> layout.onDetachedFromWindow();
            case Button button -> button.onDetachedFromWindow();
            case Checkbox checkbox -> checkbox.onDetachedFromWindow();
            case Text text -> text.onDetachedFromWindow();
            case TextField field -> field.onDetachedFromWindow();
            default -> {
            }
        }
    }
}

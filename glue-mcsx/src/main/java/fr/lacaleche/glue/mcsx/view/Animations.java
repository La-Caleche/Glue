package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.view.View;

/**
 * The looping animations {@code animate-*} declares, plus the entrance an overlay plays when it
 * opens. Kept in one place because every animator here must be cancelled when its view goes away —
 * a {@code ValueAnimator} holds a strong reference to its listener, and an {@code <if>} that
 * rebuilds its subtree would otherwise leave one spinning forever against a detached view.
 */
public final class Animations {

    private static final long SPIN_MS = 900;
    private static final long PULSE_MS = 1400;
    /** Dialogs and popovers settle in this long — DESIGN.md's {@code dur-base}. */
    private static final long ENTER_MS = 180;

    private Animations() {
    }

    /**
     * Starts the looping animation a spec declares, or does nothing. The returned animator must be
     * cancelled by the caller when the view is discarded; null when the spec animates nothing.
     */
    public static ValueAnimator start(View view, StyleSpec spec) {
        if (spec.animation() == null || spec.animation() == StyleSpec.Animation.NONE) {
            return null;
        }
        ValueAnimator animator = switch (spec.animation()) {
            case SPIN -> spin(view);
            case PULSE -> pulse(view);
            case NONE -> throw new IllegalStateException("unreachable");
        };
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.start();
        return animator;
    }

    private static ValueAnimator spin(View view) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(SPIN_MS);
        animator.setInterpolator(TimeInterpolator.LINEAR);
        animator.addUpdateListener(a -> view.setRotation((Float) a.getAnimatedValue()));
        return animator;
    }

    private static ValueAnimator pulse(View view) {
        ValueAnimator animator = ValueAnimator.ofFloat(1f, 0.45f, 1f);
        animator.setDuration(PULSE_MS);
        animator.setInterpolator(TimeInterpolator.ACCELERATE_DECELERATE);
        animator.addUpdateListener(a -> view.setAlpha((Float) a.getAnimatedValue()));
        return animator;
    }

    /** A dialog or popover fades and scales into place rather than snapping. */
    public static void playEnter(View view) {
        view.setAlpha(0f);
        view.setScaleX(0.97f);
        view.setScaleY(0.97f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ENTER_MS);
        animator.setInterpolator(TimeInterpolator.DECELERATE);
        animator.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            view.setAlpha(t);
            float scale = 0.97f + 0.03f * t;
            view.setScaleX(scale);
            view.setScaleY(scale);
        });
        animator.start();
    }
}

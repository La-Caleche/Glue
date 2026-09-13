package fr.lacaleche.glue.mcsx.mui.mixin;

import fr.lacaleche.mui.internal.UIManager;
import icyllis.modernui.fragment.FragmentContainerView;
import icyllis.modernui.view.View;
import icyllis.modernui.view.WindowGroup;
import icyllis.modernui.view.WindowManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets pointer input reach a mounted overlay at all.
 *
 * <p>The decor is a {@code WindowGroup}, and a window group routes every touch, hover, scroll and
 * pointer-icon event exclusively to its focused child window while that child is <em>touch-modal</em>
 * — which the default {@code WindowManager.LayoutParams} is. The host adds the screen container after
 * the overlay container, so the screen container holds window focus, and with no ModernUI screen open
 * it is an empty view that consumes nothing: every mouse event dies there and the overlay underneath
 * is unreachable. Screens never noticed because their content lives in the focused container.</p>
 *
 * <p>Marking both fragment containers not-touch-modal restores ordinary front-to-back child dispatch
 * — the empty container passes events through to the overlay, and a mounted ModernUI screen still
 * wins by sitting above it. Sub-windows such as popups and context menus add their own layout params
 * and keep their modal exclusivity.</p>
 */
@Mixin(value = UIManager.class, remap = false)
public class UiManagerOverlayInputMixin {

    @Inject(method = "initializeViews", at = @At("TAIL"))
    private void mcsx$reachableOverlay(CallbackInfo callback) {
        WindowGroup decor = ((UIManager) (Object) this).getDecorView();
        for (int index = 0; index < decor.getChildCount(); index++) {
            View child = decor.getChildAt(index);
            if (child instanceof FragmentContainerView) {
                ((WindowManager.LayoutParams) child.getLayoutParams()).flags
                        |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            }
        }
    }
}

package fr.lacaleche.glue.mcsx.mui.mixin;

import fr.lacaleche.glue.mcsx.client.internal.CursorRegistry;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.TextView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {View.class, ViewGroup.class, TextView.class}, remap = false)
abstract class ViewCursorMixin {

    @Inject(method = "onResolvePointerIcon", at = @At("HEAD"), cancellable = true)
    private void resolveAssignedCursor(MotionEvent event, CallbackInfoReturnable<PointerIcon> callback) {
        PointerIcon cursor = CursorRegistry.cursor((View) (Object) this);
        if (cursor != null) {
            callback.setReturnValue(cursor);
            return;
        }

        View view = (View) (Object) this;
        if (view.isEnabled() && view.isClickable() && !(view instanceof EditText)) {
            callback.setReturnValue(PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND));
        }
    }
}

package fr.lacaleche.glue.mcsx.mui.mixin;

import fr.lacaleche.glue.mcsx.client.internal.CursorRegistry;
import icyllis.modernui.view.PointerIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "fr.lacaleche.mui.internal.UIManager$HostViewRoot", remap = false)
abstract class HostViewRootCursorMixin {

    @Redirect(
            method = "applyPointerIcon",
            at = @At(
                    value = "INVOKE",
                    target = "Licyllis/modernui/view/PointerIcon;getSystemIcon(I)Licyllis/modernui/view/PointerIcon;"
            )
    )
    private PointerIcon resolveMcsxCursor(int pointerType) {
        return CursorRegistry.resolve(pointerType);
    }
}

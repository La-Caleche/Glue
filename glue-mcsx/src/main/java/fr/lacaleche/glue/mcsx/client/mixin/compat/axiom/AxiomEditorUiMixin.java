package fr.lacaleche.glue.mcsx.client.mixin.compat.axiom;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.lacaleche.glue.mcsx.client.internal.OverlayHost;
import fr.lacaleche.glue.mcsx.client.internal.compat.AxiomCompatManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Gives Axiom's full editor exclusive ownership of the window when it opens. */
@Pseudo
@Mixin(targets = "com.moulberry.axiom.editor.EditorUI", remap = false)
public abstract class AxiomEditorUiMixin {

    @WrapMethod(method = "enable")
    private static void mcsx$closeOverlay(Operation<Void> original) {
        original.call();
        if (AxiomCompatManager.isEditorUiEnabled()) OverlayHost.closeActive();
    }
}

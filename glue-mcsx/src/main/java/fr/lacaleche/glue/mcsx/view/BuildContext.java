package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.bind.Scope;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxContent;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import fr.lacaleche.glue.mcsx.core.reactive.Effect;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The immutable lexical and lifecycle context used while binding one subtree.
 *
 * <p>{@code inherited} is a <em>supplier</em>, not a value: a container's text classes or
 * {@code font=} binding can be reactive, and the only reader of the inherited defaults is a descendant
 * {@code <text>}/{@code <icon>} restyle effect. Reading the chain from inside that effect is what
 * subscribes it to the ancestors' class bindings; a baked value would freeze the inherited colour at
 * build time — the structural gates rebuild untracked, so nothing else re-bakes it.
 */
record BuildContext(Scope scope, Map<String, String> imports, SlotContent slot,
                    List<Effect> effects, Supplier<InheritedText> inherited) {

    BuildContext withEffects(List<Effect> newEffects) {
        return new BuildContext(scope, imports, slot, newEffects, inherited);
    }

    BuildContext withScope(Scope newScope) {
        return new BuildContext(newScope, imports, slot, effects, inherited);
    }

    record SlotContent(List<McsxContent> children, Scope callerScope,
                       Map<String, String> callerImports, McsxElement callSite) {
    }
}

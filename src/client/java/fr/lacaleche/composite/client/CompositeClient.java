package fr.lacaleche.composite.client;

import fr.lacaleche.composite.CompositeBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws composite cells: every state of the composite block takes the {@link CompositeBlockModel},
 * and the block parts' block entities and the item parts are drawn by the
 * {@link CompositeBlockEntityRenderer}.
 */
public final class CompositeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModelLoadingPlugin.register(context -> context.registerBlockStateResolver(CompositeBlocks.COMPOSITE, resolver -> {
            CompositeBlockModel.Unbaked model = new CompositeBlockModel.Unbaked();
            for (BlockState state : resolver.block().getStateDefinition().getPossibleStates()) resolver.setModel(state, model);
        }));
        BlockEntityRenderers.register(CompositeBlocks.COMPOSITE_ENTITY, CompositeBlockEntityRenderer::new);
    }
}

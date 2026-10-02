package fr.lacaleche.glue.testmod.datagen;

import fr.lacaleche.glue.shaper.BlockShapeProvider;
import fr.lacaleche.glue.testmod.registries.TestBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;

/** The showcase blocks whose shapes come from their models. */
public class ShowcaseBlockShapes extends BlockShapeProvider {

    public ShowcaseBlockShapes(FabricDataOutput output) {
        super(output);
    }

    @Override
    protected void generate() {
        block(TestBlocks.TEST_SHAPE_BLOCK);
    }
}

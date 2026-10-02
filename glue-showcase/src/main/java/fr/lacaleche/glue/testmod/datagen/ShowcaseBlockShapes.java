package fr.lacaleche.glue.testmod.datagen;

import fr.lacaleche.glue.shaper.BlockShapeProvider;
import fr.lacaleche.glue.testmod.Testmod;
import fr.lacaleche.glue.testmod.blocks.demo.TestChairBlock;
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
        block(TestBlocks.TEST_CHAIR_BLOCK).rotation16(TestChairBlock.ROTATION);
        block(TestBlocks.TEST_STOVE_BLOCK).collision(Testmod.id("block/template_stove_shape"));
    }
}

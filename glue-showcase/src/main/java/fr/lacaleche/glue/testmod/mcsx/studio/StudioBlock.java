package fr.lacaleche.glue.testmod.mcsx.studio;

import fr.lacaleche.glue.testmod.registries.TestBlocks;
import fr.lacaleche.glue.testmod.registries.TestItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * One demo block in the Palette pane, paired with the Glue feature it demonstrates. The label comes
 * from the block rather than its item: {@code ItemsRegistry} registers block items without
 * {@code useBlockDescriptionPrefix()}, so their description id has no translation.
 */
record StudioBlock(Block block, Item item, String detailKey) {

    static List<StudioBlock> all() {
        return List.of(
                new StudioBlock(TestBlocks.TEST_OUTLINE_BLOCK, TestItems.TEST_OUTLINE_BLOCK,
                        "mcsx.studio.block.outline"),
                new StudioBlock(TestBlocks.TEST_SPINNING_BLOCK, TestItems.TEST_SPINNING_BLOCK,
                        "mcsx.studio.block.spinning"),
                new StudioBlock(TestBlocks.TEST_SHADER_BLOCK, TestItems.TEST_SHADER_BLOCK,
                        "mcsx.studio.block.shader"),
                new StudioBlock(TestBlocks.TEST_ADDITIVE_SPRITE_BLOCK,
                        TestItems.TEST_ADDITIVE_SPRITE_BLOCK, "mcsx.studio.block.additive"),
                new StudioBlock(TestBlocks.TEST_SHAPE_BLOCK, TestItems.TEST_SHAPE_BLOCK,
                        "mcsx.studio.block.shape")
        );
    }
}

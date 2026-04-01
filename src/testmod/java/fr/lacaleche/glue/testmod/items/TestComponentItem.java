package fr.lacaleche.glue.testmod.items;

import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.testmod.registries.TestDataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public class TestComponentItem extends Item {

    public TestComponentItem(Properties properties) {
        super(properties.component(TestDataComponents.TEST_TRANSFORM_COMPONENT, TransformationComponent.DEFAULT));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide()) {
            TransformationComponent comp = stack.get(TestDataComponents.TEST_TRANSFORM_COMPONENT);
            if (comp != null) {
                TransformationComponent newComp = new TransformationComponent(
                        comp.translation().add(new Vector3f(0f, 1f, 0f)),
                        comp.leftRotation(),
                        comp.scale(),
                        comp.rightRotation());
                stack.set(TestDataComponents.TEST_TRANSFORM_COMPONENT, newComp);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}

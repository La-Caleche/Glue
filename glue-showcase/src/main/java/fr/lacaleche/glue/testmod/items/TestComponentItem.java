package fr.lacaleche.glue.testmod.items;

import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.testmod.registries.TestDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

/**
 * Demonstrates Glue's {@link TransformationComponent} data component as a small transform preset
 * tool. The selected preset is persisted on the stack, synchronized to clients, and exposed through
 * action-bar and tooltip feedback.
 */
public class TestComponentItem extends Item {

    private static final List<TransformationComponent> PRESETS = List.of(
            TransformationComponent.DEFAULT,
            new TransformationComponent(new Vector3f(0.0f, 1.0f, 0.0f), new Quaternionf(),
                    new Vector3f(1.0f), new Quaternionf()),
            new TransformationComponent(new Vector3f(),
                    new Quaternionf().rotateY((float) Math.toRadians(90.0)),
                    new Vector3f(1.0f), new Quaternionf()),
            new TransformationComponent(new Vector3f(), new Quaternionf(),
                    new Vector3f(0.5f, 1.5f, 0.5f), new Quaternionf()),
            new TransformationComponent(new Vector3f(0.5f, 0.25f, 0.0f),
                    new Quaternionf().rotateY((float) Math.toRadians(45.0)),
                    new Vector3f(1.25f, 0.75f, 1.25f), new Quaternionf())
    );

    public TestComponentItem(Properties properties) {
        super(properties
                .component(TestDataComponents.TEST_TRANSFORM_COMPONENT, TransformationComponent.DEFAULT)
                .component(DataComponents.LORE, lore(TransformationComponent.DEFAULT)));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide()) {
            TransformationComponent current = transform(stack);
            int nextPreset = player.isShiftKeyDown() ? 0 : (presetIndex(current) + 1) % PRESETS.size();
            TransformationComponent next = copy(PRESETS.get(nextPreset));
            stack.set(TestDataComponents.TEST_TRANSFORM_COMPONENT, next);
            stack.set(DataComponents.LORE, lore(next));
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "item.glue-test.test_component.selected",
                            net.minecraft.network.chat.Component.translatable(presetTranslation(nextPreset))),
                    true);
        }
        return InteractionResult.SUCCESS;
    }

    private static ItemLore lore(TransformationComponent transform) {
        int preset = presetIndex(transform);
        net.minecraft.network.chat.Component presetName = preset >= 0
                ? net.minecraft.network.chat.Component.translatable(presetTranslation(preset))
                : net.minecraft.network.chat.Component.translatable("item.glue-test.test_component.preset.custom");
        return new ItemLore(List.of(
                net.minecraft.network.chat.Component.translatable(
                        "item.glue-test.test_component.tooltip.preset", presetName)
                        .withStyle(ChatFormatting.AQUA),
                net.minecraft.network.chat.Component.translatable(
                        "item.glue-test.test_component.tooltip.translation", vectorText(transform.translation()))
                        .withStyle(ChatFormatting.GRAY),
                net.minecraft.network.chat.Component.translatable(
                        "item.glue-test.test_component.tooltip.scale", vectorText(transform.scale()))
                        .withStyle(ChatFormatting.GRAY),
                net.minecraft.network.chat.Component.translatable(
                        "item.glue-test.test_component.tooltip.use").withStyle(ChatFormatting.DARK_GRAY)
        ));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !TransformationComponent.DEFAULT.equals(transform(stack));
    }

    private static TransformationComponent transform(ItemStack stack) {
        return stack.getOrDefault(TestDataComponents.TEST_TRANSFORM_COMPONENT, TransformationComponent.DEFAULT);
    }

    private static int presetIndex(TransformationComponent transform) {
        return PRESETS.indexOf(transform);
    }

    private static String presetTranslation(int preset) {
        return "item.glue-test.test_component.preset." + preset;
    }

    private static String vectorText(Vector3f vector) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vector.x, vector.y, vector.z);
    }

    private static TransformationComponent copy(TransformationComponent transform) {
        return new TransformationComponent(
                new Vector3f(transform.translation()),
                new Quaternionf(transform.leftRotation()),
                new Vector3f(transform.scale()),
                new Quaternionf(transform.rightRotation()));
    }
}

package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.registries.DataComponentTypesRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import net.minecraft.core.component.DataComponentType;

public class TestDataComponents {

    public static final DataComponentTypesRegistry REGISTRY = new DataComponentTypesRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static final DataComponentType<TransformationComponent> TEST_TRANSFORM_COMPONENT = REGISTRY.register(
            "test_transform_component",
            TransformationComponent.CODEC,
            TransformationComponent.PACKET_CODEC);

    public static void registerDataComponents() {
        TestmodClient.LOGGER.info("Registering data components");
    }
}

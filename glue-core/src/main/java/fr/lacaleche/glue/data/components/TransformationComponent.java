package fr.lacaleche.glue.data.components;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record TransformationComponent(
        Vector3f translation, Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation) {

    public static final TransformationComponent DEFAULT = new TransformationComponent(
            new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f), new Quaternionf());

    public static final Codec<TransformationComponent> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            ExtraCodecs.VECTOR3F.fieldOf("translation").forGetter(TransformationComponent::translation),
                            ExtraCodecs.QUATERNIONF.fieldOf("left_rotation")
                                    .forGetter(TransformationComponent::leftRotation),
                            ExtraCodecs.VECTOR3F.fieldOf("scale").forGetter(TransformationComponent::scale),
                            ExtraCodecs.QUATERNIONF.fieldOf("right_rotation")
                                    .forGetter(TransformationComponent::rightRotation))
                    .apply(instance, TransformationComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransformationComponent> PACKET_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VECTOR3F, TransformationComponent::translation,
                    ByteBufCodecs.QUATERNIONF, TransformationComponent::leftRotation,
                    ByteBufCodecs.VECTOR3F, TransformationComponent::scale,
                    ByteBufCodecs.QUATERNIONF, TransformationComponent::rightRotation,
                    TransformationComponent::new);

    public Transformation toTransformation() {
        Transformation leftTransform = new Transformation(translation, leftRotation, new Vector3f(1f, 1f, 1f), null);
        Transformation scaleTransform = new Transformation(new Vector3f(), new Quaternionf(), scale, null);
        Transformation rightTransform = new Transformation(new Vector3f(), rightRotation, new Vector3f(1f, 1f, 1f), null);
        return leftTransform.compose(scaleTransform).compose(rightTransform);
    }
}
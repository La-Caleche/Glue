package fr.lacaleche.glue.client.transform.flywheel;

public interface TransformStack<Self extends TransformStack<Self>> extends Transform<Self> {
    Self pushPose();

    Self popPose();
}

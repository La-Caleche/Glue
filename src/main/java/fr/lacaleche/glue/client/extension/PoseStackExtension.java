package fr.lacaleche.glue.client.extension;

import fr.lacaleche.glue.client.transform.PoseStackTransform;

public interface PoseStackExtension {

    /**
     * @return The {@link PoseStackTransform} wrapper for this {@link com.mojang.blaze3d.vertex.PoseStack}.
     */
    PoseStackTransform glue$transformStack();

}

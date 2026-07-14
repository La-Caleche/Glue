package fr.lacaleche.glue.client.render.internal.material;

import fr.lacaleche.glue.client.render.pipeline.MaterialFrame;

import java.util.Optional;

interface TerrainMaterialCapture {

    void beginFrame(long frameSequence);

    void cancelFrame();

    Optional<MaterialFrame> currentFrame(long frameSequence);

    void cleanup();
}

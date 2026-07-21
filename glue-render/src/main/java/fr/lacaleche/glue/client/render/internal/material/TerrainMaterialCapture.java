package fr.lacaleche.glue.client.render.internal.material;

interface TerrainMaterialCapture {

    void beginFrame(long frameSequence);

    void cancelFrame();

    void cleanup();
}

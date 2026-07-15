package fr.lacaleche.glue.client.render.internal.material;

interface TerrainMaterialCapture {

    void beginFrame(long frameSequence);

    void cancelFrame();

    /** GL texture id of the captured material color for the current frame, or -1 if none. */
    int colorTextureId();

    /** GL depth texture id matching {@link #colorTextureId()}, or -1 if none. */
    int depthTextureId();

    void cleanup();
}

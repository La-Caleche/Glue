package fr.lacaleche.glue.compat;

import net.irisshaders.iris.api.v0.IrisApi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

class IrisProxy {

    private IrisProxy() {}

    private static boolean reflectionInitialized = false;
    private static boolean reflectionReady = false;

    private static Method getPipelineManagerMethod;
    private static Method getCurrentDimensionMethod;
    private static Field pipelineField;
    private static Field pipelinesPerDimensionField;
    private static Object cachedVanillaPipeline;

    static boolean isIrisShaderEnabled() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    static boolean initReflection() {
        if (reflectionInitialized) return reflectionReady;
        reflectionInitialized = true;

        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            getPipelineManagerMethod = irisClass.getMethod("getPipelineManager");
            getCurrentDimensionMethod = irisClass.getMethod("getCurrentDimension");

            Class<?> pipelineManagerClass = Class.forName("net.irisshaders.iris.pipeline.PipelineManager");
            pipelineField = pipelineManagerClass.getDeclaredField("pipeline");
            pipelineField.setAccessible(true);
            pipelinesPerDimensionField = pipelineManagerClass.getDeclaredField("pipelinesPerDimension");
            pipelinesPerDimensionField.setAccessible(true);

            Class<?> vanillaPipelineClass = Class.forName("net.irisshaders.iris.pipeline.VanillaRenderingPipeline");
            cachedVanillaPipeline = vanillaPipelineClass.getDeclaredConstructor().newInstance();

            reflectionReady = true;
        } catch (Exception e) {
            reflectionReady = false;
            throw new RuntimeException("Iris reflection setup failed", e);
        }
        return reflectionReady;
    }

    static boolean isReflectionReady() {
        return reflectionReady;
    }

    @SuppressWarnings("unchecked")
    static RenderCompat.IrisSavedState bypassPipeline() throws Exception {
        Object pm = getPipelineManagerMethod.invoke(null);
        Object prevPipeline = pipelineField.get(pm);

        if (cachedVanillaPipeline.getClass().isInstance(prevPipeline)) {
            return null;
        }

        Object dimensionKey = getCurrentDimensionMethod.invoke(null);
        Map<Object, Object> map = (Map<Object, Object>) pipelinesPerDimensionField.get(pm);

        RenderCompat.IrisSavedState state = new RenderCompat.IrisSavedState();
        state.pipelineManager = pm;
        state.previousPipeline = prevPipeline;
        state.dimensionKey = dimensionKey;
        state.hadMapEntry = map.containsKey(dimensionKey);
        state.previousMapEntry = state.hadMapEntry ? map.get(dimensionKey) : null;

        pipelineField.set(pm, cachedVanillaPipeline);
        map.put(dimensionKey, cachedVanillaPipeline);

        return state;
    }

    @SuppressWarnings("unchecked")
    static void restorePipeline(RenderCompat.IrisSavedState state) throws Exception {
        pipelineField.set(state.pipelineManager, state.previousPipeline);

        Map<Object, Object> map = (Map<Object, Object>) pipelinesPerDimensionField.get(state.pipelineManager);
        if (state.hadMapEntry) {
            map.put(state.dimensionKey, state.previousMapEntry);
        } else {
            map.remove(state.dimensionKey);
        }
    }
}

package fr.lacaleche.glue.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.vertices.ImmediateState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

class IrisProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-iris");

    private IrisProxy() {}

    static boolean isIrisShaderEnabled() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    static boolean isRenderingShadowPass() {
        return IrisApi.getInstance().isRenderingShadowPass();
    }

    static void assignPipeline(RenderPipeline pipeline, String programName) {
        IrisApi.getInstance().assignPipeline(pipeline, IrisProgram.valueOf(programName));
    }

    static boolean isBypassing() {
        return ImmediateState.bypass;
    }

    static void withBypass(Runnable action) {
        boolean wasBypassed = ImmediateState.bypass;
        ImmediateState.bypass = true;
        try {
            action.run();
        } finally {
            ImmediateState.bypass = wasBypassed;
        }
    }

    static void withFullBypass(Runnable action) {
        boolean wasBypassed = ImmediateState.bypass;
        boolean wasSafe = ImmediateState.safeToMultiply;
        ImmediateState.bypass = true;
        ImmediateState.safeToMultiply = true;
        try {
            action.run();
        } finally {
            ImmediateState.bypass = wasBypassed;
            ImmediateState.safeToMultiply = wasSafe;
        }
    }

    static int getMainDepthGlId() {
        try {
            Object pipeline = getIrisPipeline();
            if (pipeline == null) return -1;

            Class<?> pipeClass = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline");
            if (!pipeClass.isInstance(pipeline)) return -1;

            Object rts = getRenderTargets(pipeline, pipeClass);
            if (rts == null) return -1;

            Class<?> rtsClass = Class.forName("net.irisshaders.iris.targets.RenderTargets");
            Object depthTex = rtsClass.getMethod("getDepthTexture").invoke(rts);
            if (depthTex == null) return -1;

            Class<?> glTexClass = Class.forName("com.mojang.blaze3d.opengl.GlTexture");
            if (glTexClass.isInstance(depthTex)) {
                return (int) glTexClass.getMethod("glId").invoke(depthTex);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    static int getSceneDepthGlId() {
        try {
            Object pipeline = getIrisPipeline();
            if (pipeline == null) return -1;

            Class<?> pipeClass = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline");
            if (!pipeClass.isInstance(pipeline)) return -1;

            Object rts = getRenderTargets(pipeline, pipeClass);
            if (rts == null) return -1;

            Class<?> rtsClass = Class.forName("net.irisshaders.iris.targets.RenderTargets");
            for (String fieldName : new String[]{"noHand", "noTranslucents"}) {
                try {
                    Field f = rtsClass.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object depthTex = f.get(rts);
                    if (depthTex instanceof Integer id && id > 0) return id;
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception ignored) {}
        return -1;
    }

    static Object[] getIrisRenderTargetArray() {
        try {
            Object pipeline = getIrisPipeline();
            if (pipeline == null) return null;

            Class<?> pipeClass = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline");
            if (!pipeClass.isInstance(pipeline)) return null;

            Object rts = getRenderTargets(pipeline, pipeClass);
            if (rts == null) return null;

            Class<?> rtsClass = Class.forName("net.irisshaders.iris.targets.RenderTargets");
            Field arrField = rtsClass.getDeclaredField("targets");
            arrField.setAccessible(true);
            return (Object[]) arrField.get(rts);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            LOGGER.warn("[Glue] Failed to get Iris render targets", e);
            return null;
        }
    }

    static int[] getIrisTargetTextures(Object target, String name) {
        try {
            Class<?> rtClass = Class.forName("net.irisshaders.iris.targets.RenderTarget");
            Field mf = rtClass.getDeclaredField("mainTexture"); mf.setAccessible(true);
            Field af = rtClass.getDeclaredField("altTexture");  af.setAccessible(true);
            Field wf = rtClass.getDeclaredField("width");       wf.setAccessible(true);
            Field hf = rtClass.getDeclaredField("height");      hf.setAccessible(true);

            int mainId = (int) mf.get(target);
            int altId = (int) af.get(target);
            int w = (int) wf.get(target);
            int h = (int) hf.get(target);
            return new int[]{mainId, altId, w, h};
        } catch (Exception e) {
            LOGGER.warn("[Glue] Failed to read Iris target: {}", name, e);
            return null;
        }
    }

    private static Object getIrisPipeline() throws Exception {
        Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
        Object mgr = irisClass.getMethod("getPipelineManager").invoke(null);
        return mgr.getClass().getMethod("getPipelineNullable").invoke(mgr);
    }

    private static Object getRenderTargets(Object pipeline, Class<?> pipeClass) throws Exception {
        Field rtsField = pipeClass.getDeclaredField("renderTargets");
        rtsField.setAccessible(true);
        return rtsField.get(pipeline);
    }
}

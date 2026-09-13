package fr.lacaleche.glue.testmod.mcsx.studio;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightType;
import fr.lacaleche.glue.mcsx.client.Cursors;
import fr.lacaleche.glue.mcsx.client.component.ReactiveView;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.BlendMode;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Top-down plot of every demo light around the player, drawn from the same reactive values the docked
 * panes read and the same {@link Theme} the workspace installs &mdash; so a resource reload restyles
 * the radar with the rest of the workspace.
 *
 * <p>Each light is an additive stack of discs sized by its real range and coloured by its real RGB,
 * which is why overlapping emitters bloom the way they do in the world. Clicking picks the nearest
 * light.</p>
 */
final class LightRadarView extends ReactiveView {

    private static final int GLOW_STEPS = 7;
    private static final float PICK_RADIUS = 16.0f;
    private static final float CORE_RADIUS = 3.5f;
    private static final float VIEW_CONE = 70.0f;

    private final Paint paint = new Paint();
    private final Value<Theme> theme;
    private final Value<List<StudioLight>> lights;
    private final Value<Light> selected;
    private final Value<StudioSession.Pose> pose;
    private final Value<Integer> span;
    private final Consumer<Light> onSelect;

    LightRadarView(
            Context context,
            Value<Theme> theme,
            Value<List<StudioLight>> lights,
            Value<Light> selected,
            Value<StudioSession.Pose> pose,
            Value<Integer> span,
            Consumer<Light> onSelect
    ) {
        super(context);
        this.theme = theme;
        this.lights = lights;
        this.selected = selected;
        this.pose = pose;
        this.span = span;
        this.onSelect = onSelect;
        this.invalidateOn(theme, lights, selected, pose, span);
        Cursors.set(this, Cursors.crosshair());
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (event.getAction() != MotionEvent.ACTION_UP) return false;

        this.onSelect.accept(this.pick(event.getX(), event.getY()));
        return true;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Theme active = this.theme.get();
        float width = this.getWidth();
        float height = this.getHeight();
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
        float scale = this.scale();

        this.paint.setAntiAlias(true);
        this.paint.setStyle(Paint.FILL);
        this.paint.setColor(active.get(ThemeTokens.SURFACE_WELL));
        canvas.drawRect(0, 0, width, height, this.paint);

        this.drawRangeLayers(canvas, centerX, centerY, scale, active.get(ThemeTokens.SURFACE_HEADER));
        this.drawLights(canvas, centerX, centerY, scale, active.get(ThemeTokens.CONTROL_ON));
        this.drawPlayer(canvas, centerX, centerY, active.get(ThemeTokens.CONTROL_ON));
    }

    private void drawRangeLayers(Canvas canvas, float centerX, float centerY, float scale, int layer) {
        this.paint.setStyle(Paint.FILL);
        int outer = this.span.get() / 16 * 16;
        for (int blocks = outer; blocks >= 16; blocks -= 16) {
            this.paint.setColor(ThemeTokens.withAlpha(layer, blocks % 32 == 0 ? 48 : 28));
            canvas.drawCircle(centerX, centerY, blocks * scale, this.paint);
        }
    }

    private void drawLights(Canvas canvas, float centerX, float centerY, float scale, int onColor) {
        StudioSession.Pose origin = this.pose.get();
        Light picked = this.selected.get();
        for (StudioLight entry : this.lights.get()) {
            Light light = entry.light();
            float x = centerX + (float) (light.x - origin.x()) * scale;
            float y = centerY + (float) (light.z - origin.z()) * scale;
            int color = entry.color();

            this.paint.setStyle(Paint.FILL);
            this.paint.setBlendMode(BlendMode.PLUS);
            float reach = Math.max(light.range * scale, CORE_RADIUS);
            int peak = Math.clamp(Math.round(26f + light.intensity * 9f), 18, 70);
            for (int step = GLOW_STEPS; step >= 1; step--) {
                float factor = step / (float) GLOW_STEPS;
                this.paint.setColor(ThemeTokens.withAlpha(color, Math.round(peak * (1.0f - factor) + 6)));
                canvas.drawCircle(x, y, reach * factor, this.paint);
            }
            if (light.type != LightType.POINT) {
                this.drawCone(canvas, light, x, y, reach, color);
            }
            this.paint.setBlendMode(null);

            this.paint.setColor(color);
            canvas.drawCircle(x, y, CORE_RADIUS, this.paint);
            if (light != picked) continue;

            this.paint.setStyle(Paint.STROKE);
            this.paint.setStrokeWidth(1.5f);
            this.paint.setColor(onColor);
            canvas.drawCircle(x, y, CORE_RADIUS + 6.0f, this.paint);
        }
    }

    private void drawCone(Canvas canvas, Light light, float x, float y, float reach, int color) {
        float horizontal = (float) Math.hypot(light.directionX, light.directionZ);
        if (horizontal < 0.1f) return;

        float facing = (float) Math.toDegrees(Math.atan2(light.directionZ, light.directionX));
        float half = StudioLight.coneDegrees(light.cosOuter);
        this.paint.setColor(ThemeTokens.withAlpha(color, 46));
        canvas.drawPie(x, y, reach, facing - half, half * 2.0f, this.paint);
    }

    private void drawPlayer(Canvas canvas, float centerX, float centerY, int onColor) {
        double yaw = Math.toRadians(this.pose.get().yaw());
        float facing = (float) Math.toDegrees(Math.atan2(Math.cos(yaw), -Math.sin(yaw)));

        this.paint.setStyle(Paint.FILL);
        this.paint.setColor(ThemeTokens.withAlpha(onColor, 64));
        canvas.drawPie(centerX, centerY, 24.0f, facing - VIEW_CONE / 2.0f, VIEW_CONE, this.paint);

        this.paint.setColor(onColor);
        canvas.drawCircle(centerX, centerY, 4.5f, this.paint);
        this.paint.setColor(0xffffffff);
        canvas.drawCircle(centerX, centerY, 1.8f, this.paint);
    }

    private Light pick(float x, float y) {
        StudioSession.Pose origin = this.pose.get();
        float scale = this.scale();
        float centerX = this.getWidth() / 2.0f;
        float centerY = this.getHeight() / 2.0f;
        Light nearest = null;
        float nearestDistance = PICK_RADIUS;
        for (StudioLight entry : this.lights.get()) {
            Light light = entry.light();
            float dx = centerX + (float) (light.x - origin.x()) * scale - x;
            float dy = centerY + (float) (light.z - origin.z()) * scale - y;
            float distance = (float) Math.hypot(dx, dy);
            if (distance > nearestDistance) continue;

            nearestDistance = distance;
            nearest = light;
        }
        return nearest;
    }

    private float scale() {
        return Math.min(this.getWidth(), this.getHeight()) / (float) Math.max(1, this.span.get());
    }

}

package fr.lacaleche.glue.testmod.mcsx.studio;

import fr.lacaleche.glue.mcsx.client.component.ReactiveView;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.ViewGroup;

/** A colour chip that reads its colour reactively, used for light rows, presets and the preview. */
final class SwatchView extends ReactiveView {

    private final Paint paint = new Paint();
    private final Value<Integer> color;

    SwatchView(Context context, Value<Integer> color, int size) {
        super(context);
        this.color = color;
        this.setMinimumWidth(size);
        this.setMinimumHeight(size);
        this.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        this.invalidateOn(color);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float width = this.getWidth();
        float height = this.getHeight();
        float radius = Math.min(3.0f, Math.min(width, height) / 2.0f);
        this.paint.setAntiAlias(true);
        this.paint.setStyle(Paint.FILL);
        this.paint.setColor(this.color.get());
        canvas.drawRoundRect(0, 0, width, height, radius, this.paint);

        this.paint.setColor(0x24ffffff);
        canvas.drawRoundRect(1, 1, width - 1, 2, 1, this.paint);
        this.paint.setColor(0x46000000);
        canvas.drawRoundRect(1, height - 3, width - 1, height - 1, 1, this.paint);
    }
}

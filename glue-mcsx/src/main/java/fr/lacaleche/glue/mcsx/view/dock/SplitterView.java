package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockGeometry;
import fr.lacaleche.glue.mcsx.core.dock.DockSplit.Dir;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import fr.lacaleche.glue.mcsx.cursor.Cursors;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;

/**
 * The draggable gap between two split children: an 8px hit area drawing a 1px hairline, accent
 * on hover. Which split/index it adjusts is (re)assigned every geometry pass — splitters are
 * positional, not identities.
 */
final class SplitterView extends View {

    private final DockHostView host;
    private DockGeometry.Splitter splitter;
    private boolean hover;

    SplitterView(Context context, DockHostView host) {
        super(context);
        this.host = host;
        // colours are read in onDraw; the binding only re-triggers it on theme change
        Themed.onTheme(this, theme -> {
        });
    }

    void setSplitter(DockGeometry.Splitter value) {
        this.splitter = value;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (splitter == null) {
            return;
        }
        Paint paint = Paint.obtain();
        paint.setColor(Themes.active().color(hover ? Tokens.ACCENT : Tokens.BORDER));
        if (splitter.dir() == Dir.ROW) {
            float x = getWidth() / 2f;
            canvas.drawRect(x - 0.5f, 0f, x + 0.5f, getHeight(), paint);
        } else {
            float y = getHeight() / 2f;
            canvas.drawRect(0f, y - 0.5f, getWidth(), y + 0.5f, paint);
        }
        paint.recycle();
    }

    @Override
    public boolean onHoverEvent(@NonNull MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_HOVER_ENTER -> {
                hover = true;
                invalidate();
            }
            case MotionEvent.ACTION_HOVER_EXIT -> {
                hover = false;
                invalidate();
            }
            default -> {
            }
        }
        return super.onHoverEvent(event);
    }

    @Override
    public PointerIcon onResolvePointerIcon(@NonNull MotionEvent event) {
        if (splitter == null) {
            return null;
        }
        return splitter.dir() == Dir.ROW ? Cursors.resizeEW() : Cursors.resizeNS();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && splitter != null) {
            host.splitterPressed(splitter);
            return true;
        }
        return super.onTouchEvent(event);
    }
}

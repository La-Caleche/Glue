package fr.lacaleche.glue.mcsx.view.debug;

import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * An in-game view inspector, in the spirit of a browser's element picker: hover to highlight the
 * deepest view under the pointer, click to pin it, and read its box model and container config off
 * the panel. Its purpose is comparing two subtrees that should lay out alike and don't.
 *
 * <p>Overlays the tree it inspects rather than living inside it, so it never perturbs the layout it
 * is measuring. The facts come from {@link ViewProbe}, which is headless-testable — what you read
 * here is exactly what a test can assert.
 */
public final class InspectorView extends View {

    /** Box-model colours, fixed rather than themed so the overlay reads against any UI. */
    private static final int FILL = 0x4059A9FF;
    private static final int BORDER = 0xFF59A9FF;
    private static final int PADDING_FILL = 0x403DDC84;

    private final View target;
    private final TextView panel;

    private View hovered;
    private View pinned;
    private final Paint paint = new Paint();

    /**
     * @param target the root whose tree is inspected; the inspector must be a sibling above it, not
     *     a child, or it would appear in its own hit tests
     * @param panel  where the selected view's report is written
     */
    public InspectorView(Context context, View target, TextView panel) {
        super(context);
        this.target = target;
        this.panel = panel;
        setClickable(true);
        setFocusable(false);
    }

    /** The view the panel is describing: the pinned one, else whatever the pointer is over. */
    public View selection() {
        return pinned != null ? pinned : hovered;
    }

    public void clearPin() {
        pinned = null;
        report();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                View under = ViewProbe.deepestAt(target, Math.round(event.getX()), Math.round(event.getY()));
                if (under != hovered) {
                    hovered = under;
                    report();
                    invalidate();
                }
            }
            case MotionEvent.ACTION_DOWN -> {
                pinned = ViewProbe.deepestAt(target, Math.round(event.getX()), Math.round(event.getY()));
                report();
                invalidate();
            }
            default -> {
            }
        }
        return true;
    }

    private void report() {
        View selected = selection();
        if (selected == null) {
            panel.setText("Inspector — move the pointer over the UI. Click pins a view.");
            return;
        }
        StringBuilder text = new StringBuilder();
        for (View ancestor : ancestry(selected)) {
            text.append(ancestor == selected ? "► " : "  ")
                    .append(ancestor.getClass().getSimpleName()).append('\n');
        }
        text.append('\n').append(ViewProbe.dump(ViewProbe.probe(selected)));
        panel.setText(text.toString());
    }

    /** The chain from the inspected root down to {@code view}, so a node has visible context. */
    private List<View> ancestry(View view) {
        List<View> chain = new ArrayList<>();
        for (View current = view; current != null; current = parentWithin(current)) {
            chain.add(0, current);
            if (current == target) {
                break;
            }
        }
        return chain;
    }

    private View parentWithin(View view) {
        return view != target && view.getParent() instanceof View parent ? parent : null;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        View selected = selection();
        if (selected == null) {
            return;
        }
        int[] box = boundsOf(selected);
        paint.setColor(FILL);
        canvas.drawRect(box[0], box[1], box[0] + box[2], box[1] + box[3], paint);

        // the content box inside the padding, so a mis-set padding is visible as a thick green ring
        paint.setColor(PADDING_FILL);
        canvas.drawRect(box[0] + selected.getPaddingLeft(), box[1] + selected.getPaddingTop(),
                box[0] + box[2] - selected.getPaddingRight(),
                box[1] + box[3] - selected.getPaddingBottom(), paint);

        paint.setColor(BORDER);
        canvas.drawRect(box[0], box[1], box[0] + box[2], box[1] + 1, paint);
        canvas.drawRect(box[0], box[1] + box[3] - 1, box[0] + box[2], box[1] + box[3], paint);
        canvas.drawRect(box[0], box[1], box[0] + 1, box[1] + box[3], paint);
        canvas.drawRect(box[0] + box[2] - 1, box[1], box[0] + box[2], box[1] + box[3], paint);
    }

    private int[] boundsOf(View view) {
        return ViewProbe.boundsIn(target, view);
    }

    /** Paints the panel against the active theme so it stays legible in either. */
    public void stylePanel() {
        panel.setTextSize(16f);
        panel.setTextColor(Themes.active().color(Tokens.TEXT_PRIMARY));
        panel.setPadding(8, 8, 8, 8);
    }
}

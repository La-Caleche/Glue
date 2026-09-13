package fr.lacaleche.glue.mcsx.client.dock.internal.view;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Full-screen shell with optional fixed header and footer. */
@Environment(EnvType.CLIENT)
public final class DockRootView extends ViewGroup {

    private final View header;
    private final View host;
    private final View footer;

    public DockRootView(Context context, View header, View host, View footer) {
        super(context);
        this.header = header;
        this.host = host;
        this.footer = footer;
        if (header != null) this.addView(header);
        this.addView(host);
        if (footer != null) this.addView(footer);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int headerHeight = measureChrome(this.header, width, height);
        int footerHeight = measureChrome(this.footer, width, height);
        this.host.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, height - headerHeight - footerHeight), MeasureSpec.EXACTLY)
        );
        this.setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int y = 0;
        if (this.header != null) {
            this.header.layout(0, y, this.getWidth(), y + this.header.getMeasuredHeight());
            y += this.header.getMeasuredHeight();
        }
        this.host.layout(0, y, this.getWidth(), y + this.host.getMeasuredHeight());
        y += this.host.getMeasuredHeight();
        if (this.footer != null) {
            this.footer.layout(0, y, this.getWidth(), y + this.footer.getMeasuredHeight());
        }
    }

    private static int measureChrome(View chrome, int width, int height) {
        if (chrome == null) return 0;

        chrome.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        );
        return chrome.getMeasuredHeight();
    }
}

package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;

/**
 * The extension seam: a Java ModernUI {@code View} registered under a custom tag. A native component
 * reads its attributes/children through the {@code element} and resolves bindings / registers effects
 * / builds child Views through the {@code binder} — this is how a consumer such as a VFX editor adds
 * {@code <slider>}, {@code <viewport>}, or {@code <curve>} without MCSX importing them.
 */
@FunctionalInterface
public interface NativeComponent {

    View create(Context context, McsxElement element, ViewBinder binder);
}

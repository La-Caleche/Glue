package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxDocument;
import fr.lacaleche.glue.mcsx.view.debug.Inspector;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

/**
 * Hosts a bound {@code .mcsx} screen as a ModernUI {@code Fragment}: binds the document on
 * {@code onCreateView} and disposes every effect on {@code onDestroyView}.
 */
public final class McsxFragment extends Fragment {

    private final McsxDocument document;
    private final ScreenController controller;
    private final ComponentRegistry registry;
    private final ViewBinder.DocumentResolver resolver;
    private final boolean inspect;

    private ViewInstance instance;

    public McsxFragment(McsxDocument document, ScreenController controller,
                        ComponentRegistry registry, ViewBinder.DocumentResolver resolver) {
        this(document, controller, registry, resolver, false);
    }

    /** @param inspect mounts the {@link Inspector} over the screen for picking views apart */
    public McsxFragment(McsxDocument document, ScreenController controller,
                        ComponentRegistry registry, ViewBinder.DocumentResolver resolver,
                        boolean inspect) {
        this.document = document;
        this.controller = controller;
        this.registry = registry;
        this.resolver = resolver;
        this.inspect = inspect;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        instance = ViewBinder.bind(document, controller, requireContext(), registry, resolver);
        return inspect ? Inspector.wrap(requireContext(), instance.root()) : instance.root();
    }

    @Override
    public void onDestroyView() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
        super.onDestroyView();
    }
}

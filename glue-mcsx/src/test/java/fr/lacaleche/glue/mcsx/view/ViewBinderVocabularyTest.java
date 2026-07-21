package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.mcsx.Tags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The binder and the linter must agree on the tag vocabulary. They used to keep separate copies,
 * which drifted: the linter accepted {@code <option>}, a tag no builder has ever handled.
 */
class ViewBinderVocabularyTest {

    @Test
    void everyBuiltInTagHasABuilderAndEveryBuilderHasABuiltInTag() {
        assertEquals(Tags.BUILT_IN, ViewBinder.BUILT_INS.keySet());
    }

    /** A config tag is consumed by its parent, so the binder must not have a builder for it. */
    @Test
    void configTagsHaveNoBuilder() {
        for (String tag : Tags.CONFIG) {
            assertEquals(null, ViewBinder.BUILT_INS.get(tag), tag + " must not be built directly");
        }
    }
}

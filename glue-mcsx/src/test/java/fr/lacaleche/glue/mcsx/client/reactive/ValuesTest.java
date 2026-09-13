package fr.lacaleche.glue.mcsx.client.reactive;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ValuesTest {

    @Test
    void constantKeepsItsValueAndNeverNotifies() {
        Value<String> constant = Values.constant("fixed");
        List<String> values = new ArrayList<>();

        Subscription subscription = constant.subscribe(values::add);
        subscription.close();
        subscription.close();

        assertEquals("fixed", constant.get());
        assertEquals(List.of(), values);
        assertThrows(NullPointerException.class, () -> constant.subscribe(null));
    }

    @Test
    void notTracksItsSourceAndOnlyEmitsOnChange() {
        Signal<Boolean> source = Signal.of(false);
        Value<Boolean> negated = Values.not(source);
        List<Boolean> values = new ArrayList<>();
        negated.subscribe(values::add);

        assertTrue(negated.get());
        source.set(true);
        source.set(true);

        assertFalse(negated.get());
        assertEquals(List.of(false), values);
    }

    @Test
    void allAndAnyFoldEverySourceAndSuppressUnchangedResults() {
        Signal<Boolean> first = Signal.of(true);
        Signal<Boolean> second = Signal.of(true);
        Signal<Boolean> third = Signal.of(true);
        Value<Boolean> all = Values.all(first, second, third);
        Value<Boolean> any = Values.any(first, second, third);
        List<Boolean> allValues = new ArrayList<>();
        all.subscribe(allValues::add);

        assertTrue(all.get());
        assertTrue(any.get());

        second.set(false);
        third.set(false);

        assertFalse(all.get());
        assertTrue(any.get());
        assertEquals(List.of(false), allValues);

        first.set(false);
        assertFalse(any.get());
    }

    @Test
    void emptyFoldsAreConstantIdentitiesAndSingleSourcesPassThrough() {
        Signal<Boolean> only = Signal.of(true);

        assertTrue(Values.all().get());
        assertFalse(Values.any().get());
        assertSame(only, Values.all(only));
        assertSame(only, Values.any(only));
        assertThrows(NullPointerException.class, () -> Values.all(only, null));
    }
}

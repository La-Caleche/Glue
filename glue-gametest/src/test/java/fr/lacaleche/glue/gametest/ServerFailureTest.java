package fr.lacaleche.glue.gametest;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a step reports when a server action fails. The executor hands back a wrapper; the report is
 * only useful if it names the throwable the action actually raised.
 */
class ServerFailureTest {

    @Test
    void anExceptionIsReportedAsItself() {
        IllegalStateException thrown = new IllegalStateException("no player");

        Exception reported = assertThrows(IllegalStateException.class,
                () -> GameTest.rethrowServerFailure(new CompletionException(thrown)));

        assertSame(thrown, reported);
    }

    @Test
    void anErrorIsReportedAsItself() {
        AssertionError thrown = new AssertionError("the block did not change");

        Error reported = assertThrows(AssertionError.class,
                () -> GameTest.rethrowServerFailure(new CompletionException(thrown)));

        assertSame(thrown, reported);
    }

    @Test
    void aWrapperWithoutACauseIsKept() {
        CompletionException wrapper = new CompletionException((Throwable) null);

        assertSame(wrapper, assertThrows(CompletionException.class,
                () -> GameTest.rethrowServerFailure(wrapper)));
    }
}

package fr.lacaleche.glue.gametest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameTestsTest {

    @Test
    void factoryRegistrationBuildsAFreshGraphPerRun() {
        GameTests.register("gametests-test:factory-fresh",
                () -> GameTest.create("gametests-test:factory-fresh").waitTicks(1));

        GameTest first = GameTests.build("gametests-test:factory-fresh");
        GameTest second = GameTests.build("gametests-test:factory-fresh");

        assertNotSame(first, second);
        assertEquals(1, first.steps().size());
        assertEquals(1, second.steps().size());
    }

    @Test
    void instanceRegistrationReturnsTheSameGraphEachRun() {
        GameTest test = GameTest.create("gametests-test:instance-shared").waitTicks(1);
        GameTests.register(test);

        assertSame(test, GameTests.build("gametests-test:instance-shared"));
        assertSame(test, GameTests.build("gametests-test:instance-shared"));
    }

    @Test
    void factoryProductMustCarryTheRegisteredName() {
        GameTests.register("gametests-test:mismatch",
                () -> GameTest.create("gametests-test:other"));

        assertThrows(IllegalStateException.class,
                () -> GameTests.build("gametests-test:mismatch"));
    }

    @Test
    void factoryBuildingNullFailsLoudly() {
        GameTests.register("gametests-test:null-product", () -> null);

        assertThrows(IllegalStateException.class,
                () -> GameTests.build("gametests-test:null-product"));
    }

    @Test
    void unknownNameBuildsNull() {
        assertNull(GameTests.build("gametests-test:never-registered"));
    }
}

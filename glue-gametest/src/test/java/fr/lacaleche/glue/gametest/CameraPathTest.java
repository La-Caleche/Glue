package fr.lacaleche.glue.gametest;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CameraPathTest {

    private static final double EPSILON = 1e-4;

    @Test
    void looksSouthAlongPositiveZAndDownWithPositivePitch() {
        CameraPose south = CameraPose.lookingAt(Vec3.ZERO, new Vec3(0, 0, 5), 70);
        CameraPose west = CameraPose.lookingAt(Vec3.ZERO, new Vec3(-5, 0, 0), 70);
        CameraPose down = CameraPose.lookingAt(Vec3.ZERO, new Vec3(0, -5, 5), 70);

        assertEquals(0, south.yaw(), EPSILON);
        assertEquals(90, west.yaw(), EPSILON);
        assertEquals(45, down.pitch(), EPSILON);
    }

    @Test
    void turnsTheShortWayRoundWhenYawWraps() {
        CameraPose from = new CameraPose(Vec3.ZERO, 170, 0, 70);
        CameraPose to = new CameraPose(Vec3.ZERO, -170, 0, 70);

        assertEquals(180, from.lerp(to, 0.5).yaw(), EPSILON);
    }

    @Test
    void easesBetweenPosesAndHoldsTheEnds() {
        CameraPose from = new CameraPose(Vec3.ZERO, 0, 0, 60);
        CameraPose to = new CameraPose(new Vec3(10, 0, 0), 0, 0, 80);
        CameraPath path = CameraPath.move(from, to, 2);

        assertEquals(0, path.at(-1).eye().x, EPSILON);
        assertEquals(5, path.at(1).eye().x, EPSILON);
        assertEquals(70, path.at(1).fov(), EPSILON);
        assertEquals(10, path.at(3).eye().x, EPSILON);
    }

    @Test
    void orbitsAroundTheCentreLookingAtIt() {
        Vec3 center = new Vec3(100, 64, 100);
        CameraPath orbit = CameraPath.orbit(center, 10, 0, 0, 90, 70, 4);

        CameraPose start = orbit.at(0);
        CameraPose end = orbit.at(4);
        assertEquals(110, start.eye().z, EPSILON);
        assertEquals(180, Math.abs(start.yaw()), EPSILON);
        assertEquals(90, end.eye().x, EPSILON);
        assertEquals(-90, end.yaw(), EPSILON);
    }

    @Test
    void chainsPathsEndToEnd() {
        CameraPose first = new CameraPose(Vec3.ZERO, 0, 0, 70);
        CameraPose second = new CameraPose(new Vec3(1, 2, 3), 0, 0, 70);
        CameraPath film = CameraPath.hold(first, 1).then(CameraPath.hold(second, 2));

        assertEquals(3, film.seconds(), EPSILON);
        assertSame(first, film.at(0.5));
        assertSame(second, film.at(1.5));
    }

    @Test
    void rejectsAnEmptyPathAndAPoseLookingAtItself() {
        CameraPose pose = new CameraPose(Vec3.ZERO, 0, 0, 70);

        assertThrows(IllegalArgumentException.class, () -> CameraPath.hold(pose, 0));
        assertThrows(IllegalArgumentException.class, () -> CameraPose.lookingAt(Vec3.ZERO, Vec3.ZERO, 70));
        assertThrows(IllegalArgumentException.class, () -> new CameraPose(Vec3.ZERO, 0, 95, 70));
    }
}

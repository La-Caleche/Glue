package fr.lacaleche.glue.shaper;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VecHelperTest {

    private static final double EPS = 1e-5;

    @Test
    public void lerp_pIsZero_returnsFrom() {
        Vec3 from = new Vec3(1, 2, 3);
        Vec3 to = new Vec3(4, 5, 6);
        Vec3 result = VecHelper.lerp(0f, from, to);
        assertEquals(from.x, result.x, EPS);
        assertEquals(from.y, result.y, EPS);
        assertEquals(from.z, result.z, EPS);
    }

    @Test
    public void lerp_pIsOne_returnsTo() {
        Vec3 from = new Vec3(1, 2, 3);
        Vec3 to = new Vec3(4, 5, 6);
        Vec3 result = VecHelper.lerp(1f, from, to);
        assertEquals(to.x, result.x, EPS);
        assertEquals(to.y, result.y, EPS);
        assertEquals(to.z, result.z, EPS);
    }

    @Test
    public void lerp_pIsHalf_returnsMidpoint() {
        Vec3 from = new Vec3(0, 0, 0);
        Vec3 to = new Vec3(2, 4, 6);
        Vec3 mid = VecHelper.lerp(0.5f, from, to);
        assertEquals(1.0, mid.x, EPS);
        assertEquals(2.0, mid.y, EPS);
        assertEquals(3.0, mid.z, EPS);
    }

    @Test
    public void lerp_pBeyondOne_extrapolates() {
        Vec3 from = new Vec3(0, 0, 0);
        Vec3 to = new Vec3(1, 0, 0);
        Vec3 result = VecHelper.lerp(2f, from, to);
        assertEquals(2.0, result.x, EPS);
    }

    @Test
    public void slerp_pIsZero_returnsFrom() {
        Vec3 from = new Vec3(1, 0, 0);
        Vec3 to = new Vec3(0, 1, 0);
        Vec3 result = VecHelper.slerp(0f, from, to);
        assertEquals(from.x, result.x, 1e-4);
        assertEquals(from.y, result.y, 1e-4);
        assertEquals(from.z, result.z, 1e-4);
    }

    @Test
    public void slerp_pIsOne_returnsTo() {
        Vec3 from = new Vec3(1, 0, 0);
        Vec3 to = new Vec3(0, 1, 0);
        Vec3 result = VecHelper.slerp(1f, from, to);
        assertEquals(to.x, result.x, 1e-4);
        assertEquals(to.y, result.y, 1e-4);
        assertEquals(to.z, result.z, 1e-4);
    }

    @Test
    public void slerp_parallelVectors_doesNotProduceNaN() {
        Vec3 v = new Vec3(1, 0, 0).normalize();
        Vec3 result = VecHelper.slerp(0.5f, v, v);
        assertFalse(Double.isNaN(result.x), "slerp of parallel vectors must not produce NaN");
        assertFalse(Double.isNaN(result.y), "slerp of parallel vectors must not produce NaN");
        assertFalse(Double.isNaN(result.z), "slerp of parallel vectors must not produce NaN");
    }

    @Test
    public void slerp_perpendicularVectors_atHalfReturnsDiagonal() {
        Vec3 from = new Vec3(1, 0, 0);
        Vec3 to = new Vec3(0, 1, 0);
        Vec3 mid = VecHelper.slerp(0.5f, from, to);
        assertEquals(Math.sqrt(2) / 2, mid.x, 1e-4);
        assertEquals(Math.sqrt(2) / 2, mid.y, 1e-4);
        assertEquals(0.0, mid.z, EPS);
    }

    @Test
    public void rotate_zeroDegrees_returnsSameReference() {
        Vec3 v = new Vec3(3, 1, 4);
        assertSame(v, VecHelper.rotate(v, 0, Axis.X), "rotate(0) should short-circuit");
    }

    @Test
    public void rotate_90degreesAroundY_mapsXToNegZ() {
        Vec3 r = VecHelper.rotate(new Vec3(1, 0, 0), 90, Axis.Y);
        assertEquals(0.0, r.x, EPS);
        assertEquals(0.0, r.y, EPS);
        assertEquals(-1.0, r.z, EPS);
    }

    @Test
    public void rotate_90degreesAroundX_mapsYToPosZ() {
        Vec3 r = VecHelper.rotate(new Vec3(0, 1, 0), 90, Axis.X);
        assertEquals(0.0, r.x, EPS);
        assertEquals(0.0, r.y, EPS);
        assertEquals(1.0, r.z, EPS);
    }

    @Test
    public void rotate_90degreesAroundZ_mapsXToPosY() {
        Vec3 r = VecHelper.rotate(new Vec3(1, 0, 0), 90, Axis.Z);
        assertEquals(0.0, r.x, EPS);
        assertEquals(1.0, r.y, EPS);
        assertEquals(0.0, r.z, EPS);
    }

    @Test
    public void rotate_360degrees_returnsOriginalVector() {
        Vec3 v = new Vec3(1, 2, 3);
        Vec3 r = VecHelper.rotate(v, 360, Axis.Y);
        assertEquals(v.x, r.x, EPS);
        assertEquals(v.y, r.y, EPS);
        assertEquals(v.z, r.z, EPS);
    }

    @Test
    public void axisAlignedPlaneOf_xAxis_returnsYZPlane() {
        Vec3 plane = VecHelper.axisAlignedPlaneOf(new Vec3(1, 0, 0));
        assertEquals(0.0, plane.x, EPS, "X component should be 0 for X-facing plane");
        assertEquals(1.0, plane.y, EPS);
        assertEquals(1.0, plane.z, EPS);
    }

    @Test
    public void axisAlignedPlaneOf_yAxis_returnsXZPlane() {
        Vec3 plane = VecHelper.axisAlignedPlaneOf(new Vec3(0, 1, 0));
        assertEquals(1.0, plane.x, EPS);
        assertEquals(0.0, plane.y, EPS, "Y component should be 0 for Y-facing plane");
        assertEquals(1.0, plane.z, EPS);
    }

    @Test
    public void axisAlignedPlaneOf_zAxis_returnsXYPlane() {
        Vec3 plane = VecHelper.axisAlignedPlaneOf(new Vec3(0, 0, 1));
        assertEquals(1.0, plane.x, EPS);
        assertEquals(1.0, plane.y, EPS);
        assertEquals(0.0, plane.z, EPS, "Z component should be 0 for Z-facing plane");
    }

    @Test
    public void axisAlignedPlaneOf_directionOverload_matchesVec3Overload() {
        Vec3 fromDir = VecHelper.axisAlignedPlaneOf(Direction.EAST);
        Vec3 fromVec = VecHelper.axisAlignedPlaneOf(new Vec3(1, 0, 0));
        assertEquals(fromDir.x, fromVec.x, EPS);
        assertEquals(fromDir.y, fromVec.y, EPS);
        assertEquals(fromDir.z, fromVec.z, EPS);
    }

    @Test
    public void intersectSphere_rayAlignedWithSphere_returnsNearHitPoint() {
        Vec3 hit = VecHelper.intersectSphere(
                new Vec3(-5, 0, 0), new Vec3(1, 0, 0),
                new Vec3(0, 0, 0), 1.0);
        assertNotNull(hit);
        assertEquals(-1.0, hit.x, EPS, "Ray from outside should hit the near face at x=-1");
        assertEquals(0.0, hit.y, EPS);
        assertEquals(0.0, hit.z, EPS);
    }

    @Test
    public void intersectSphere_originInsideSphere_returnsForwardExitPoint() {
        Vec3 hit = VecHelper.intersectSphere(
                new Vec3(-0.5, 0, 0), new Vec3(1, 0, 0),
                new Vec3(0, 0, 0), 1.0);
        assertNotNull(hit);
        assertEquals(1.0, hit.x, EPS, "The only forward hit from inside is the exit at x=+1");
        assertEquals(0.0, hit.y, EPS);
        assertEquals(0.0, hit.z, EPS);
    }

    @Test
    public void intersectSphere_sphereBehindOrigin_returnsNull() {
        Vec3 hit = VecHelper.intersectSphere(
                new Vec3(5, 0, 0), new Vec3(1, 0, 0),
                new Vec3(0, 0, 0), 1.0);
        assertNull(hit, "A sphere entirely behind the ray should not intersect");
    }

    @Test
    public void intersectSphere_rayOffsetBeyondRadius_returnsNull() {
        Vec3 hit = VecHelper.intersectSphere(
                new Vec3(-5, 2, 0), new Vec3(1, 0, 0),
                new Vec3(0, 0, 0), 1.0);
        assertNull(hit, "Ray offset outside radius should not intersect");
    }

    @Test
    public void intersectSphere_zeroDirection_returnsNull() {
        Vec3 hit = VecHelper.intersectSphere(
                new Vec3(0, 0, 0), Vec3.ZERO,
                new Vec3(0, 0, 0), 1.0);
        assertNull(hit, "Zero-length direction should return null");
    }

    @Test
    public void project_ontoZeroVector_returnsZero() {
        Vec3 result = VecHelper.project(new Vec3(1, 2, 3), Vec3.ZERO);
        assertEquals(Vec3.ZERO, result);
    }

    @Test
    public void project_parallelVectors_returnsOriginalMagnitude() {
        Vec3 v = new Vec3(3, 0, 0);
        Vec3 result = VecHelper.project(v, new Vec3(1, 0, 0));
        assertEquals(v.x, result.x, EPS);
        assertEquals(v.y, result.y, EPS);
        assertEquals(v.z, result.z, EPS);
    }

    @Test
    public void project_perpendicularVectors_returnsZero() {
        Vec3 result = VecHelper.project(new Vec3(0, 1, 0), new Vec3(1, 0, 0));
        assertEquals(0.0, result.x, EPS);
        assertEquals(0.0, result.y, EPS);
        assertEquals(0.0, result.z, EPS);
    }
}

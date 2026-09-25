package com.example.risingworldstarter.tests;

import com.example.risingworldstarter.wallfit.WallFitGeometry;
import com.example.risingworldstarter.wallfit.WallFitGeometry.Wall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WallFitGeometryTest {
    private static Wall wall(double x, double z, double length) {
        return new Wall(x, z, 1, 0, length, 0.2, 0, 3);
    }

    @Test void fillsGapAndAlignsSidewaysWithoutChangingHeightOrThickness() {
        var result = WallFitGeometry.fit(wall(0.1, 0.15, 1.8),
                List.of(wall(-2, 0, 2), wall(2, 0, 2)));
        assertTrue(result.fitted(), result.reason());
        assertEquals(wall(0, 0, 2), result.wall());
    }

    @Test void extendsEitherSideWithoutAFoundation() {
        for (double side : new double[]{-1, 1}) {
            var result = WallFitGeometry.fit(wall(side * 0.2, 0.1, 2), List.of(wall(side * 2, 0, 2)));
            assertTrue(result.fitted(), result.reason());
            assertEquals(wall(0, 0, 2), result.wall());
        }
    }

    @Test void followsAngledNeighborAndHandlesReversedAxis() {
        double angle = Math.toRadians(8), dx = Math.cos(angle), dz = Math.sin(angle);
        Wall neighbor = new Wall(-2 * dx, -2 * dz, -dx, -dz, 2, 0.2, 0, 3);
        var result = WallFitGeometry.fit(wall(0, 0.1, 2), List.of(neighbor));
        assertTrue(result.fitted(), result.reason());
        assertEquals(dx, result.wall().dx(), 1e-8);
        assertEquals(dz, result.wall().dz(), 1e-8);
        assertEquals(0, result.wall().x(), 1e-8);
        assertEquals(0, result.wall().z(), 1e-8);
    }

    @Test void leavesUnmatchedOrDistantPlacementsAlone() {
        assertFalse(WallFitGeometry.fit(wall(0, 0, 2), List.of()).fitted());
        assertFalse(WallFitGeometry.fit(wall(0, 0, 2), List.of(wall(-5, 0, 2))).fitted());
        assertFalse(WallFitGeometry.fit(wall(0, 0, 2), List.of(wall(-2, 1, 2))).fitted());
        assertFalse(WallFitGeometry.fit(wall(0, 0, 2), List.of(
                new Wall(-2, 0, 1, 0, 2, 0.2, 3, 3))).fitted());
    }

    @Test void rejectsConflictingLinesRegardlessOfSearchOrder() {
        Wall left = wall(-2, -0.1, 2), right = wall(2, 0.1, 2);
        assertFalse(WallFitGeometry.fit(wall(0, 0, 2), List.of(left, right)).fitted());
        assertFalse(WallFitGeometry.fit(wall(0, 0, 2), List.of(right, left)).fitted());
    }

    @Test void rejectsOversizedGapsAndExcessiveMovement() {
        assertFalse(WallFitGeometry.fit(wall(0, 0, 2),
                List.of(wall(-2.3, 0, 2), wall(2.3, 0, 2))).fitted());
        assertFalse(WallFitGeometry.fit(wall(0, 0.3, 2), List.of(wall(-2.75, 0, 2))).fitted());
    }

    @Test void doesNotSnapIntoPerpendicularWalls() {
        Wall crossing = new Wall(0.8, 0, 0, 1, 2, 0.2, 0, 3);
        var result = WallFitGeometry.fit(wall(0, 0.1, 2), List.of(wall(-2, 0, 2), crossing));
        assertFalse(result.fitted());
        assertEquals("fitted wall would overlap an existing wall", result.reason());
    }

    @Test void ignoresWallsOnOtherFloorsForCollision() {
        Wall upstairs = new Wall(0, 0, 0, 1, 2, 0.2, 3, 3);
        assertTrue(WallFitGeometry.fit(wall(0, 0.1, 2), List.of(wall(-2, 0, 2), upstairs)).fitted());
    }
}

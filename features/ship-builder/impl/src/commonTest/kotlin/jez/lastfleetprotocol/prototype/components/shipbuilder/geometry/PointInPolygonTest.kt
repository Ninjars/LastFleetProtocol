package jez.lastfleetprotocol.prototype.components.shipbuilder.geometry

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PointInPolygonTest {

    // Simple triangle
    private val triangle = listOf(
        Offset(0f, 0f),
        Offset(10f, 0f),
        Offset(5f, 10f),
    )

    @Test
    fun pointInsideTriangle_returnsTrue() {
        assertTrue(pointInPolygon(Offset(5f, 3f), triangle))
    }

    @Test
    fun pointOutsideTriangle_returnsFalse() {
        assertFalse(pointInPolygon(Offset(0f, 10f), triangle))
        assertFalse(pointInPolygon(Offset(-1f, 0f), triangle))
        assertFalse(pointInPolygon(Offset(20f, 5f), triangle))
    }

    @Test
    fun pointOnTriangleEdge_returnsTrue() {
        // On the bottom edge (0,0)-(10,0)
        assertTrue(pointInPolygon(Offset(5f, 0f), triangle))
        // On vertex
        assertTrue(pointInPolygon(Offset(0f, 0f), triangle))
    }

    // Convex pentagon matching the player hull shape
    private val playerHullPentagon = listOf(
        Offset(56f, 0f),
        Offset(-30f, 37f),
        Offset(-56f, 20f),
        Offset(-56f, -20f),
        Offset(-30f, -37f),
    )

    @Test
    fun pointInsideConvexPentagon_returnsTrue() {
        // Center area
        assertTrue(pointInPolygon(Offset(0f, 0f), playerHullPentagon))
        // Near the nose
        assertTrue(pointInPolygon(Offset(40f, 0f), playerHullPentagon))
        // Near the rear
        assertTrue(pointInPolygon(Offset(-50f, 0f), playerHullPentagon))
    }

    @Test
    fun pointOutsideConvexPentagon_returnsFalse() {
        // Beyond the nose
        assertFalse(pointInPolygon(Offset(60f, 0f), playerHullPentagon))
        // Beyond the wing
        assertFalse(pointInPolygon(Offset(0f, 50f), playerHullPentagon))
        // Behind the ship
        assertFalse(pointInPolygon(Offset(-60f, 0f), playerHullPentagon))
    }

    @Test
    fun pointInsideRotatedPolygon_callerPreTransforms() {
        // Simulate a hull rotated by 90 degrees (PI/2).
        // To test: transform the world-space test point into hull local space,
        // then call pointInPolygon with the original (unrotated) hull vertices.
        val hullRotation = (kotlin.math.PI / 2).toFloat()
        val hullPosition = Offset(100f, 100f)

        // A point that is at (100, 140) in world space.
        // In hull local space (after un-rotating by 90 degrees):
        // local = unrotate(worldPoint - hullPos, -rotation)
        val worldPoint = Offset(100f, 140f)
        val localX = worldPoint.x - hullPosition.x
        val localY = worldPoint.y - hullPosition.y
        val cosR = cos(-hullRotation)
        val sinR = sin(-hullRotation)
        val testX = localX * cosR - localY * sinR
        val testY = localX * sinR + localY * cosR

        val localPoint = Offset(testX, testY)

        // The point (100, 140) is 40 units in the +Y direction from hull center.
        // After un-rotating by -90 degrees, this becomes approximately (40, 0) in local space,
        // which is inside the player hull pentagon (nose is at x=56).
        assertTrue(pointInPolygon(localPoint, playerHullPentagon))
    }

    @Test
    fun pointOutsideRotatedPolygon_callerPreTransforms() {
        val hullRotation = (kotlin.math.PI / 2).toFloat()
        val hullPosition = Offset(100f, 100f)

        // A point far from the hull
        val worldPoint = Offset(200f, 200f)
        val localX = worldPoint.x - hullPosition.x
        val localY = worldPoint.y - hullPosition.y
        val cosR = cos(-hullRotation)
        val sinR = sin(-hullRotation)
        val testX = localX * cosR - localY * sinR
        val testY = localX * sinR + localY * cosR

        val localPoint = Offset(testX, testY)

        assertFalse(pointInPolygon(localPoint, playerHullPentagon))
    }
}

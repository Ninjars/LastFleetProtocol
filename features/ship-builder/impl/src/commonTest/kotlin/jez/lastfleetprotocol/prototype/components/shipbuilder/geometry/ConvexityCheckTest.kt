package jez.lastfleetprotocol.prototype.components.shipbuilder.geometry

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConvexityCheckTest {

    @Test
    fun square_isConvex() {
        // Clockwise square
        val square = listOf(
            Offset(0f, 0f),
            Offset(10f, 0f),
            Offset(10f, 10f),
            Offset(0f, 10f),
        )
        assertTrue(isConvex(square))
    }

    @Test
    fun triangle_isConvex() {
        val triangle = listOf(
            Offset(0f, 0f),
            Offset(10f, 0f),
            Offset(5f, 10f),
        )
        assertTrue(isConvex(triangle))
    }

    @Test
    fun lShape_isConcave() {
        // L-shape: concave polygon
        val lShape = listOf(
            Offset(0f, 0f),
            Offset(10f, 0f),
            Offset(10f, 5f),
            Offset(5f, 5f),
            Offset(5f, 10f),
            Offset(0f, 10f),
        )
        assertFalse(isConvex(lShape))
    }

    @Test
    fun fewerThan3Vertices_isConvex() {
        assertTrue(isConvex(emptyList()))
        assertTrue(isConvex(listOf(Offset(0f, 0f))))
        assertTrue(isConvex(listOf(Offset(0f, 0f), Offset(5f, 5f))))
    }

    @Test
    fun collinearVertices_isConvex() {
        // Three collinear points — all cross products are zero
        val collinear = listOf(
            Offset(0f, 0f),
            Offset(5f, 0f),
            Offset(10f, 0f),
        )
        assertTrue(isConvex(collinear))
    }

    @Test
    fun counterClockwiseSquare_isConvex() {
        // Counter-clockwise square
        val ccwSquare = listOf(
            Offset(0f, 0f),
            Offset(0f, 10f),
            Offset(10f, 10f),
            Offset(10f, 0f),
        )
        assertTrue(isConvex(ccwSquare))
    }
}

package jez.lastfleetprotocol.prototype.components.shipbuilder.geometry

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class PolygonAreaTest {

    @Test
    fun triangle_area() {
        val triangle = listOf(
            Offset(0f, 0f),
            Offset(10f, 0f),
            Offset(0f, 10f),
        )
        assertEquals(50f, calculatePolygonArea(triangle), 0.001f)
    }

    @Test
    fun square_area() {
        val square = listOf(
            Offset(0f, 0f),
            Offset(10f, 0f),
            Offset(10f, 10f),
            Offset(0f, 10f),
        )
        assertEquals(100f, calculatePolygonArea(square), 0.001f)
    }

    @Test
    fun fewerThan3Vertices_returnsZero() {
        assertEquals(0f, calculatePolygonArea(emptyList()))
        assertEquals(0f, calculatePolygonArea(listOf(Offset(0f, 0f))))
        assertEquals(0f, calculatePolygonArea(listOf(Offset(0f, 0f), Offset(5f, 5f))))
    }

    @Test
    fun degenerateCollinear_returnsZero() {
        val collinear = listOf(
            Offset(0f, 0f),
            Offset(5f, 0f),
            Offset(10f, 0f),
        )
        assertEquals(0f, calculatePolygonArea(collinear), 0.001f)
    }
}

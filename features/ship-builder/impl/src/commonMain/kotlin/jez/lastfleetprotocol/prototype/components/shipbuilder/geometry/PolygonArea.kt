package jez.lastfleetprotocol.prototype.components.shipbuilder.geometry

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * Calculate the area of a polygon using the shoelace formula.
 * Returns the absolute area regardless of winding order.
 * Returns 0f for fewer than 3 vertices.
 */
fun calculatePolygonArea(vertices: List<Offset>): Float {
    if (vertices.size < 3) return 0f

    var sum = 0f
    for (i in vertices.indices) {
        val current = vertices[i]
        val next = vertices[(i + 1) % vertices.size]
        sum += current.x * next.y - next.x * current.y
    }
    return abs(sum) / 2f
}

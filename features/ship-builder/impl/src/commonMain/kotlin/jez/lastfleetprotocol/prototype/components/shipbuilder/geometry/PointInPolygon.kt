package jez.lastfleetprotocol.prototype.components.shipbuilder.geometry

import androidx.compose.ui.geometry.Offset

/**
 * Determines whether a [point] is inside the polygon defined by [vertices]
 * using the ray-casting algorithm.
 *
 * The boundary is treated as inclusive: points on edges are considered inside.
 * Vertices and point are in the same coordinate space (world/screen coordinates,
 * not SceneOffset). For a placed hull piece with rotation, the caller should
 * transform the test point into the hull's local space first.
 */
fun pointInPolygon(point: Offset, vertices: List<Offset>): Boolean {
    if (vertices.size < 3) return false

    // First check if point is on any edge (inclusive boundary)
    if (isPointOnEdge(point, vertices)) return true

    // Ray casting: count intersections of a horizontal ray from point to +X infinity
    var inside = false
    var j = vertices.size - 1
    for (i in vertices.indices) {
        val xi = vertices[i].x
        val yi = vertices[i].y
        val xj = vertices[j].x
        val yj = vertices[j].y

        val intersect = ((yi > point.y) != (yj > point.y)) &&
            (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)

        if (intersect) inside = !inside
        j = i
    }

    return inside
}

/**
 * Check if a point lies on any edge of the polygon within a small tolerance.
 */
private fun isPointOnEdge(point: Offset, vertices: List<Offset>): Boolean {
    val epsilon = 1e-4f
    var j = vertices.size - 1
    for (i in vertices.indices) {
        val a = vertices[j]
        val b = vertices[i]

        // Check if point is within the bounding box of the edge (with tolerance)
        val minX = minOf(a.x, b.x) - epsilon
        val maxX = maxOf(a.x, b.x) + epsilon
        val minY = minOf(a.y, b.y) - epsilon
        val maxY = maxOf(a.y, b.y) + epsilon

        if (point.x in minX..maxX && point.y in minY..maxY) {
            // Check if point lies on the line segment using cross product
            val cross = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
            if (kotlin.math.abs(cross) < epsilon * maxOf(
                    kotlin.math.abs(b.x - a.x),
                    kotlin.math.abs(b.y - a.y),
                    1f,
                )
            ) {
                return true
            }
        }

        j = i
    }
    return false
}

package jez.lastfleetprotocol.prototype.components.gamecore.geometry

import com.pandulapeter.kubriko.types.SceneOffset
import kotlin.math.abs

/**
 * Calculate the area of a polygon using the shoelace formula.
 * Operates on [SceneOffset] (Kubriko value type) unlike the builder's version
 * which uses Compose `Offset`.
 *
 * Returns the absolute area regardless of winding order.
 * Returns 0f for fewer than 3 vertices.
 */
fun calculatePolygonArea(vertices: List<SceneOffset>): Float {
    if (vertices.size < 3) return 0f

    var sum = 0f
    for (i in vertices.indices) {
        val current = vertices[i]
        val next = vertices[(i + 1) % vertices.size]
        sum += current.x.raw * next.y.raw - next.x.raw * current.y.raw
    }
    return abs(sum) / 2f
}

/**
 * Compute the axis-aligned bounding box extents of a set of vertices.
 * Returns (width, height) as a Pair. Width = X extent (forward/reverse axis),
 * height = Y extent (lateral axis).
 */
fun computeBoundingBoxExtents(vertices: List<SceneOffset>): Pair<Float, Float> {
    if (vertices.isEmpty()) return 0f to 0f

    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE

    for (v in vertices) {
        val x = v.x.raw
        val y = v.y.raw
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }

    return (maxX - minX) to (maxY - minY)
}

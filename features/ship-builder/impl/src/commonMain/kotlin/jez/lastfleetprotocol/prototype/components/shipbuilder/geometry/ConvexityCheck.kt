package jez.lastfleetprotocol.prototype.components.shipbuilder.geometry

import androidx.compose.ui.geometry.Offset

/**
 * Determines whether a polygon defined by [vertices] is convex.
 *
 * Algorithm: for each consecutive triple of vertices (i, i+1, i+2 mod n),
 * compute the cross product of the two edge vectors. If all non-zero cross
 * products have the same sign, the polygon is convex.
 *
 * - Fewer than 3 vertices: returns true (not yet invalid).
 * - Collinear vertices (cross product = 0): skipped — treated as convex.
 * - If any non-zero cross products disagree in sign: returns false (concave).
 */
fun isConvex(vertices: List<Offset>): Boolean {
    if (vertices.size < 3) return true

    val n = vertices.size
    var positiveFound = false
    var negativeFound = false

    for (i in 0 until n) {
        val a = vertices[i]
        val b = vertices[(i + 1) % n]
        val c = vertices[(i + 2) % n]

        // Edge vectors: ab and bc
        val abx = b.x - a.x
        val aby = b.y - a.y
        val bcx = c.x - b.x
        val bcy = c.y - b.y

        // Cross product of ab x bc
        val cross = abx * bcy - aby * bcx

        if (cross > 0f) positiveFound = true
        if (cross < 0f) negativeFound = true

        if (positiveFound && negativeFound) return false
    }

    return true
}

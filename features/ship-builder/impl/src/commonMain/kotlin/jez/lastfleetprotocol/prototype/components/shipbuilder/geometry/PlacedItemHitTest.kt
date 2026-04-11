package jez.lastfleetprotocol.prototype.components.shipbuilder.geometry

import androidx.compose.ui.geometry.Offset
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedItem
import kotlin.math.cos
import kotlin.math.sin

/**
 * Test whether [worldPos] is inside a placed item's polygon, accounting for
 * the item's position, rotation, and mirror transforms.
 *
 * Returns false if the item has fewer than 3 vertices.
 */
fun pointInPlacedItem(
    worldPos: Offset,
    placed: PlacedItem,
    vertices: List<SceneOffset>,
): Boolean {
    if (vertices.size < 3) return false
    val rotation = placed.rotation.normalized
    val pos = Offset(placed.position.x.raw, placed.position.y.raw)
    val localX = worldPos.x - pos.x
    val localY = worldPos.y - pos.y
    val cosR = cos(-rotation)
    val sinR = sin(-rotation)
    var testX = localX * cosR - localY * sinR
    var testY = localX * sinR + localY * cosR
    if (placed.mirrorY) testX = -testX
    if (placed.mirrorX) testY = -testY
    return pointInPolygon(
        Offset(testX, testY),
        vertices.map { Offset(it.x.raw, it.y.raw) },
    )
}

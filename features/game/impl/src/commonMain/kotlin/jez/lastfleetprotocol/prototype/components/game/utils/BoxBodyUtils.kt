package jez.lastfleetprotocol.prototype.components.game.utils

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.cos
import com.pandulapeter.kubriko.helpers.extensions.sin
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset

fun BoxBody.getRelativePoint(point: SceneOffset): SceneOffset {
    val scaled = (point - pivot) * scale
    val rotated = if (rotation == AngleRadians.Zero) scaled + pivot else SceneOffset(
        x = (scaled.x + pivot.x) * rotation.cos - (scaled.y + pivot.y) * rotation.sin,
        y = (scaled.x + pivot.x) * rotation.sin + (scaled.y + pivot.y) * rotation.cos,
    )
    return rotated + position
}
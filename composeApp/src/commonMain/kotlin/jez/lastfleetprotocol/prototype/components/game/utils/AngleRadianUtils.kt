package jez.lastfleetprotocol.prototype.components.game.utils

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.types.AngleRadians

fun AngleRadians.rotateTowards(target: AngleRadians, maxChange: AngleRadians): AngleRadians {
    val delta = target.normalized.rad - this.normalized.rad
    val rotateNegative = (delta.normalized - 0.0001f).rad > delta
    return if (rotateNegative) {
        if (-maxChange > delta) {
            this - maxChange
        } else {
            this + delta
        }
    } else {
        if (maxChange < delta) {
            this + maxChange
        } else {
            this + delta
        }
    }
}

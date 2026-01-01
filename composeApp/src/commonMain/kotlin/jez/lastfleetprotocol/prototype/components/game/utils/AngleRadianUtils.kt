package jez.lastfleetprotocol.prototype.components.game.utils

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.types.AngleRadians

fun AngleRadians.rotateTowards(target: AngleRadians, maxChange: AngleRadians): AngleRadians {
    val delta = target - this
    val rotateNegative = delta.normalized.rad > delta

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

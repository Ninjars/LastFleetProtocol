package jez.lastfleetprotocol.prototype.components.game.utils

import com.pandulapeter.kubriko.helpers.extensions.cos
import com.pandulapeter.kubriko.helpers.extensions.sin
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset

fun SceneOffset.rotate(angle: AngleRadians) =
    SceneOffset(
        x = (this.x * angle.cos - this.y * angle.sin),
        y = this.x * angle.sin + this.y * angle.cos,
    )
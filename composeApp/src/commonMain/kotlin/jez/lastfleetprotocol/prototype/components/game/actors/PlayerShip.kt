package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.graphics.Color
import com.pandulapeter.kubriko.types.SceneOffset

class PlayerShip(
    initialPosition: SceneOffset,
) : Ship(
    factionColor = Color.Blue,
    initialPosition = initialPosition,
) {

}

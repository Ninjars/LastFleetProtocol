package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.graphics.Color
import com.pandulapeter.kubriko.types.SceneOffset

class EnemyShip(
    initialPosition: SceneOffset,
) : Ship(
    factionColor = Color.Red,
    initialPosition = initialPosition,
) {

}

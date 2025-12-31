package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.types.SceneOffset
import lastfleetprotocol.composeapp.generated.resources.Res
import lastfleetprotocol.composeapp.generated.resources.ship_player_1

class PlayerShip(
    initialPosition: SceneOffset,
) : Ship(
    drawable = Res.drawable.ship_player_1,
    initialPosition = initialPosition,
) {

}

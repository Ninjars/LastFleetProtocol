package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import lastfleetprotocol.composeapp.generated.resources.Res
import lastfleetprotocol.composeapp.generated.resources.ship_enemy_1

class EnemyShip(
    initialPosition: SceneOffset,
) : Ship(
    drawable = Res.drawable.ship_enemy_1,
    initialPosition = initialPosition,
) {

    override val drawingOrder: Float = DrawOrder.ENEMY_SHIP

}

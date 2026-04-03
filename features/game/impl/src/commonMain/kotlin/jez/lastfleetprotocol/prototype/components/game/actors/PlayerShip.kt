package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import lastfleetprotocol.components.design.generated.resources.Res
import lastfleetprotocol.components.design.generated.resources.ship_player_1

class PlayerShip(
    spec: ShipSpec,
    initialPosition: SceneOffset,
    turrets: List<Turret>,
    shipSystems: ShipSystems = ShipSystems(emptyList()),
) : Ship(
    spec = spec,
    drawable = Res.drawable.ship_player_1,
    initialPosition = initialPosition,
    turrets = turrets,
    shipSystems = shipSystems,
) {
    override val drawingOrder: Float = DrawOrder.PLAYER_SHIP
}

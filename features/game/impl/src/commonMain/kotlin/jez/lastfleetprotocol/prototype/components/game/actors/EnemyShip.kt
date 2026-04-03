package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.ai.EnemyAI
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import lastfleetprotocol.components.design.generated.resources.Res
import lastfleetprotocol.components.design.generated.resources.ship_enemy_1

class EnemyShip(
    initialPosition: SceneOffset,
    spec: ShipSpec,
    shipSystems: ShipSystems = ShipSystems(emptyList()),
    turrets: List<Turret> = emptyList(),
) : Ship(
    spec = spec,
    drawable = Res.drawable.ship_enemy_1,
    initialPosition = initialPosition,
    shipSystems = shipSystems,
    turrets = turrets,
) {

    override val drawingOrder: Float = DrawOrder.ENEMY_SHIP

    private val ai = EnemyAI()
    private val playerShips = mutableListOf<PlayerShip>()

    fun registerPlayerShips(ships: List<PlayerShip>) {
        playerShips.clear()
        playerShips.addAll(ships)
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        super.update(deltaTimeInMilliseconds)
        ai.update(this, playerShips, deltaTimeInMilliseconds)
    }
}

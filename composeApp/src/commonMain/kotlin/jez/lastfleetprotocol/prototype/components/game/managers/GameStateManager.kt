package jez.lastfleetprotocol.prototype.components.game.managers

import androidx.compose.ui.geometry.Offset
import com.pandulapeter.kubriko.actor.traits.Unique
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.EnemyShip
import jez.lastfleetprotocol.prototype.components.game.actors.PlayerShip
import jez.lastfleetprotocol.prototype.components.game.actors.Turret
import jez.lastfleetprotocol.prototype.components.game.data.GunData
import me.tatarka.inject.annotations.Inject

@Inject
class GameStateManager(
    private val stateManager: StateManager,
    private val actorManager: ActorManager,
    private val viewportManager: ViewportManager,
) : Manager(), Unique {
    fun setPaused(paused: Boolean) {
        if (paused == !stateManager.isRunning.value) return

        stateManager.updateIsRunning(paused)
    }

    fun startDemoScene() {
        val topLeft = viewportManager.topLeft.value
        val bottomRight = viewportManager.bottomRight.value

        val playerShipTurrets = mutableListOf<Turret>()
        val playerShip = PlayerShip(
            SceneOffset(
                x = -100f.sceneUnit,
                y = 0f.sceneUnit,
            ),
            turrets = playerShipTurrets,
        )
        Turret(
            parent = playerShip,
            offsetFromParentPivot = SceneOffset(Offset(0f, -45f)),
            pivot = SceneOffset(Offset(32f, 32f)),
            gunData = GunData(
                magazineCapacity = 12,
                reloadMilliseconds = 5000,
                cycleMilliseconds = 700,
                shotsPerBurst = 3,
                burstCycleMilliseconds = 100,
            ),
        ).apply {
            playerShipTurrets.add(this)
        }
        Turret(
            parent = playerShip,
            offsetFromParentPivot = SceneOffset(Offset(0f, 45f)),
            pivot = SceneOffset(Offset(32f, 32f)),
            gunData = GunData(
                magazineCapacity = 12,
                reloadMilliseconds = 5000,
                cycleMilliseconds = 700,
                shotsPerBurst = 3,
                burstCycleMilliseconds = 100,
            ),
        ).apply {
            playerShipTurrets.add(this)
        }
        actorManager.add(
            playerShip
        )

        val enemyShip = EnemyShip(
            SceneOffset(
                x = 100f.sceneUnit,
                y = -100f.sceneUnit,
            )
        )
        actorManager.add(
            enemyShip
        )

        playerShip.setTarget(enemyShip)
    }
}
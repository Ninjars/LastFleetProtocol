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
import jez.lastfleetprotocol.prototype.components.game.actors.Ship
import jez.lastfleetprotocol.prototype.components.game.actors.ShipSpec
import jez.lastfleetprotocol.prototype.components.game.actors.Turret
import jez.lastfleetprotocol.prototype.components.game.data.DemoScenarioConfig
import jez.lastfleetprotocol.prototype.components.game.debug.DebugVisualiser
import jez.lastfleetprotocol.prototype.components.game.data.ShipConfig
import jez.lastfleetprotocol.prototype.components.game.input.InputController
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.tatarka.inject.annotations.Inject

@Inject
class GameStateManager(
    private val stateManager: StateManager,
    private val actorManager: ActorManager,
    private val viewportManager: ViewportManager,
) : Manager(), Unique {

    enum class GameResult { VICTORY, DEFEAT }

    private val playerShips = mutableListOf<PlayerShip>()
    private val enemyShips = mutableListOf<EnemyShip>()

    val activePlayerShips: List<PlayerShip> get() = playerShips
    val activeEnemyShips: List<EnemyShip> get() = enemyShips

    private val _gameResult = MutableStateFlow<GameResult?>(null)
    val gameResult: StateFlow<GameResult?> = _gameResult

    var onGameResult: ((GameResult) -> Unit)? = null

    fun setPaused(paused: Boolean) {
        if (paused == !stateManager.isRunning.value) return

        stateManager.updateIsRunning(paused)
    }

    fun startDemoScene() {
        stateManager.updateIsRunning(true)

        // Create input controller
        val inputController = InputController()
        actorManager.add(inputController)

        // Create 2 player ships
        val player1 = createPlayerShip(
            DemoScenarioConfig.playerShipConfig,
            SceneOffset((-200f).sceneUnit, (-50f).sceneUnit),
        )
        val player2 = createPlayerShip(
            DemoScenarioConfig.playerShipConfig,
            SceneOffset((-200f).sceneUnit, 50f.sceneUnit),
        )

        // Create 3 enemy ships: light, medium, heavy
        val enemyLight = createEnemyShip(
            DemoScenarioConfig.enemyShipLightConfig,
            SceneOffset(200f.sceneUnit, (-120f).sceneUnit),
        )
        val enemyMedium = createEnemyShip(
            DemoScenarioConfig.enemyShipMediumConfig,
            SceneOffset(250f.sceneUnit, 0f.sceneUnit),
        )
        val enemyHeavy = createEnemyShip(
            DemoScenarioConfig.enemyShipHeavyConfig,
            SceneOffset(200f.sceneUnit, 120f.sceneUnit),
        )

        // Wire input controller with player ships
        inputController.registerPlayerShip(player1)
        inputController.registerPlayerShip(player2)

        // Wire enemy AI with player ship references
        for (enemy in enemyShips) {
            enemy.registerPlayerShips(playerShips.toList())
        }

        // Add debug visualiser for all ships
        val debugVisualiser = DebugVisualiser()
        for (ship in playerShips + enemyShips) {
            debugVisualiser.registerShip(ship)
        }
        actorManager.add(debugVisualiser)
    }

    private fun createPlayerShip(config: ShipConfig, position: SceneOffset): PlayerShip {
        val spec = ShipSpec.fromConfig(config)
        val systems = ShipSystems(config.internalSystems)
        val turretList = mutableListOf<Turret>()

        val ship = PlayerShip(
            spec = spec,
            initialPosition = position,
            turrets = turretList,
            shipSystems = systems,
        )

        for (tc in config.turretConfigs) {
            Turret(
                parent = ship,
                offsetFromParentPivot = SceneOffset(Offset(tc.offsetX, tc.offsetY)),
                pivot = SceneOffset(Offset(tc.pivotX, tc.pivotY)),
                gunData = tc.gunData,
            ).also { turretList.add(it) }
        }

        ship.onDestroyedCallback = ::onShipDestroyed
        playerShips.add(ship)
        actorManager.add(ship)
        return ship
    }

    private fun createEnemyShip(config: ShipConfig, position: SceneOffset): EnemyShip {
        val spec = ShipSpec.fromConfig(config)
        val systems = ShipSystems(config.internalSystems)
        val turretList = mutableListOf<Turret>()

        val ship = EnemyShip(
            spec = spec,
            initialPosition = position,
            shipSystems = systems,
            turrets = turretList,
        )

        for (tc in config.turretConfigs) {
            Turret(
                parent = ship,
                offsetFromParentPivot = SceneOffset(Offset(tc.offsetX, tc.offsetY)),
                pivot = SceneOffset(Offset(tc.pivotX, tc.pivotY)),
                gunData = tc.gunData,
            ).also { turretList.add(it) }
        }

        ship.onDestroyedCallback = ::onShipDestroyed
        enemyShips.add(ship)
        actorManager.add(ship)
        return ship
    }

    private fun onShipDestroyed(ship: Ship) {
        when (ship) {
            is PlayerShip -> playerShips.remove(ship)
            is EnemyShip -> enemyShips.remove(ship)
        }
        // Check defeat first — if simultaneous destruction, defeat takes priority
        val result = when {
            playerShips.isEmpty() -> GameResult.DEFEAT
            enemyShips.isEmpty() -> GameResult.VICTORY
            else -> null
        }
        if (result != null) {
            _gameResult.value = result
            stateManager.updateIsRunning(false)
            onGameResult?.invoke(result)
        }
    }

    fun restartScene() {
        _gameResult.value = null
        clearScene()
        startDemoScene()
    }

    fun clearScene() {
        actorManager.removeAll()
        playerShips.clear()
        enemyShips.clear()
    }
}
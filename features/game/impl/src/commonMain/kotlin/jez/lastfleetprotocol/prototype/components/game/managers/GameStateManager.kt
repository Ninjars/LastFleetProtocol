package jez.lastfleetprotocol.prototype.components.game.managers

import androidx.compose.ui.geometry.Offset
import com.pandulapeter.kubriko.actor.traits.Unique
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.Ship
import jez.lastfleetprotocol.prototype.components.game.actors.ShipSpec
import jez.lastfleetprotocol.prototype.components.game.actors.Turret
import jez.lastfleetprotocol.prototype.components.game.ai.AIModule
import jez.lastfleetprotocol.prototype.components.game.ai.BasicAI
import jez.lastfleetprotocol.prototype.components.game.data.DemoScenarioConfig
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import jez.lastfleetprotocol.prototype.components.game.data.ShipConfig
import jez.lastfleetprotocol.prototype.components.game.debug.DebugVisualiser
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

    private val playerShips = mutableListOf<Ship>()
    private val enemyShips = mutableListOf<Ship>()

    val activePlayerShips: List<Ship> get() = playerShips
    val activeEnemyShips: List<Ship> get() = enemyShips

    private val _gameResult = MutableStateFlow<GameResult?>(null)
    val gameResult: StateFlow<GameResult?> = _gameResult

    var onGameResult: ((GameResult) -> Unit)? = null

    fun setPaused(paused: Boolean) {
        if (paused == !stateManager.isRunning.value) return
        stateManager.updateIsRunning(paused)
    }

    fun startDemoScene() {
        stateManager.updateIsRunning(true)

        // Create input controller for player ships
        val inputController = InputController(selectableTeamId = Ship.TEAM_PLAYER)
        actorManager.add(inputController)

        // Create 2 player ships (no AI modules — player-controlled)
        val player1 = createShip(
            config = DemoScenarioConfig.playerShipConfig,
            position = SceneOffset((-300f).sceneUnit, (-50f).sceneUnit),
            teamId = Ship.TEAM_PLAYER,
            targetProvider = { enemyShips },
            aiModules = emptyList(),
            drawOrder = DrawOrder.PLAYER_SHIP,
        )
        val player2 = createShip(
            config = DemoScenarioConfig.playerShipConfig,
            position = SceneOffset((-300f).sceneUnit, 50f.sceneUnit),
            teamId = Ship.TEAM_PLAYER,
            targetProvider = { enemyShips },
            aiModules = emptyList(),
            drawOrder = DrawOrder.PLAYER_SHIP,
        )

        // Create 3 enemy ships with BasicAI
        createShip(
            config = DemoScenarioConfig.enemyShipLightConfig,
            position = SceneOffset(300f.sceneUnit, (-120f).sceneUnit),
            teamId = Ship.TEAM_ENEMY,
            targetProvider = { playerShips },
            aiModules = listOf(BasicAI()),
            drawOrder = DrawOrder.ENEMY_SHIP,
        )
        createShip(
            config = DemoScenarioConfig.enemyShipMediumConfig,
            position = SceneOffset(350f.sceneUnit, 0f.sceneUnit),
            teamId = Ship.TEAM_ENEMY,
            targetProvider = { playerShips },
            aiModules = listOf(BasicAI()),
            drawOrder = DrawOrder.ENEMY_SHIP,
        )
        createShip(
            config = DemoScenarioConfig.enemyShipHeavyConfig,
            position = SceneOffset(300f.sceneUnit, 120f.sceneUnit),
            teamId = Ship.TEAM_ENEMY,
            targetProvider = { playerShips },
            aiModules = listOf(BasicAI()),
            drawOrder = DrawOrder.ENEMY_SHIP,
        )

        // Wire input controller with player ships
        for (ship in playerShips) {
            inputController.registerShip(ship)
        }

        // Add debug visualiser for all ships
        val debugVisualiser = DebugVisualiser()
        for (ship in playerShips + enemyShips) {
            debugVisualiser.registerShip(ship)
        }
        actorManager.add(debugVisualiser)
    }

    private fun createShip(
        config: ShipConfig,
        position: SceneOffset,
        teamId: String,
        targetProvider: () -> List<Ship>,
        aiModules: List<AIModule>,
        drawOrder: Float,
    ): Ship {
        val spec = ShipSpec.fromConfig(config)
        val systems = ShipSystems(config.internalSystems)
        val turretList = mutableListOf<Turret>()

        val ship = Ship(
            spec = spec,
            drawable = config.drawable,
            initialPosition = position,
            teamId = teamId,
            targetProvider = targetProvider,
            aiModules = aiModules,
            turrets = turretList,
            shipSystems = systems,
            drawOrder = drawOrder,
        )

        for (tc in config.turretConfigs) {
            Turret(
                parent = ship,
                offsetFromParentPivot = SceneOffset(Offset(tc.offsetX, tc.offsetY)),
                pivot = SceneOffset(Offset(tc.pivotX, tc.pivotY)),
                gunData = tc.gunData,
                teamId = teamId,
            ).also { turretList.add(it) }
        }

        ship.onDestroyedCallback = ::onShipDestroyed

        when (teamId) {
            Ship.TEAM_PLAYER -> playerShips.add(ship)
            Ship.TEAM_ENEMY -> enemyShips.add(ship)
        }

        actorManager.add(ship)
        return ship
    }

    private fun onShipDestroyed(ship: Ship) {
        when (ship.teamId) {
            Ship.TEAM_PLAYER -> playerShips.remove(ship)
            Ship.TEAM_ENEMY -> enemyShips.remove(ship)
        }
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

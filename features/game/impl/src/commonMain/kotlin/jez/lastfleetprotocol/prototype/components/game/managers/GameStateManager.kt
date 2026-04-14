package jez.lastfleetprotocol.prototype.components.game.managers

import com.pandulapeter.kubriko.actor.traits.Unique
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.Ship
import jez.lastfleetprotocol.prototype.components.game.actors.ShipSpec
import jez.lastfleetprotocol.prototype.components.game.ai.AIModule
import jez.lastfleetprotocol.prototype.components.game.ai.BasicAI
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import jez.lastfleetprotocol.prototype.components.game.debug.DebugVisualiser
import jez.lastfleetprotocol.prototype.components.game.input.InputController
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import jez.lastfleetprotocol.prototype.components.gamecore.data.ShipConfig
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.DefaultShipDesignLoader
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.TurretGunLoader
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.convertShipDesign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.tatarka.inject.annotations.Inject

@Inject
class GameStateManager(
    private val stateManager: StateManager,
    private val actorManager: ActorManager,
    private val viewportManager: ViewportManager,
    private val shipDesignLoader: DefaultShipDesignLoader,
    private val turretGunLoader: TurretGunLoader,
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
        stateManager.updateIsRunning(!paused)
    }

    /**
     * Load default ship designs from bundled resources, convert them to ShipConfig,
     * and spawn all ships for the demo combat scenario.
     *
     * Spawn-slot mapping is filename-indexed:
     * - player_ship → 2 player ships (no AI)
     * - enemy_light → 1 enemy
     * - enemy_medium → 1 enemy
     * - enemy_heavy → 1 enemy
     */
    suspend fun startDemoScene() {
        stateManager.updateIsRunning(true)

        // Load designs and turret guns (cached after first load)
        val designs = shipDesignLoader.loadAll()
        val turretGuns = turretGunLoader.load()

        // Convert designs to runtime configs — fatal on failure
        val playerConfig = convertShipDesign(
            designs["player_ship"] ?: error("Missing bundled design: player_ship"),
            turretGuns,
        ).getOrThrow()
        val enemyLightConfig = convertShipDesign(
            designs["enemy_light"] ?: error("Missing bundled design: enemy_light"),
            turretGuns,
        ).getOrThrow()
        val enemyMediumConfig = convertShipDesign(
            designs["enemy_medium"] ?: error("Missing bundled design: enemy_medium"),
            turretGuns,
        ).getOrThrow()
        val enemyHeavyConfig = convertShipDesign(
            designs["enemy_heavy"] ?: error("Missing bundled design: enemy_heavy"),
            turretGuns,
        ).getOrThrow()

        // Create input controller for player ships
        val inputController = InputController(selectableTeamId = Ship.TEAM_PLAYER)
        actorManager.add(inputController)

        // Create 2 player ships (no AI modules — player-controlled)
        createShip(
            config = playerConfig,
            position = SceneOffset((-300f).sceneUnit, (-50f).sceneUnit),
            teamId = Ship.TEAM_PLAYER,
            targetProvider = { enemyShips },
            aiModules = emptyList(),
            drawOrder = DrawOrder.PLAYER_SHIP,
        )
        createShip(
            config = playerConfig,
            position = SceneOffset((-300f).sceneUnit, 50f.sceneUnit),
            teamId = Ship.TEAM_PLAYER,
            targetProvider = { enemyShips },
            aiModules = emptyList(),
            drawOrder = DrawOrder.PLAYER_SHIP,
        )

        // Create 3 enemy ships with BasicAI
        createShip(
            config = enemyLightConfig,
            position = SceneOffset(300f.sceneUnit, (-120f).sceneUnit),
            teamId = Ship.TEAM_ENEMY,
            targetProvider = { playerShips },
            aiModules = listOf(BasicAI()),
            drawOrder = DrawOrder.ENEMY_SHIP,
        )
        createShip(
            config = enemyMediumConfig,
            position = SceneOffset(350f.sceneUnit, 0f.sceneUnit),
            teamId = Ship.TEAM_ENEMY,
            targetProvider = { playerShips },
            aiModules = listOf(BasicAI()),
            drawOrder = DrawOrder.ENEMY_SHIP,
        )
        createShip(
            config = enemyHeavyConfig,
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

        val ship = Ship(
            spec = spec,
            initialPosition = position,
            teamId = teamId,
            targetProvider = targetProvider,
            aiModules = aiModules,
            turretsConfig = config.turretConfigs,
            shipSystems = systems,
            drawingOrder = drawOrder,
        )

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

    suspend fun restartScene() {
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

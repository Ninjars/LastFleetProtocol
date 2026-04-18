package jez.lastfleetprotocol.prototype.components.game.managers

import com.pandulapeter.kubriko.actor.traits.Unique
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.Ship
import jez.lastfleetprotocol.prototype.components.game.actors.ShipLifecycle
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
    /** A single spawn slot: which design to load, where, for which team. */
    private data class SpawnSlot(
        val designName: String,
        val position: SceneOffset,
        val teamId: String,
        val withAI: Boolean,
        val drawOrder: Float,
    )

    suspend fun startDemoScene() {
        stateManager.updateIsRunning(true)

        // Load designs and turret guns (cached after first load)
        val designs = shipDesignLoader.loadAll()
        val turretGuns = turretGunLoader.load()

        // Spawn-slot mapping: filename-indexed design + per-instance placement.
        val spawnSlots = listOf(
            SpawnSlot("player_ship", SceneOffset((-300f).sceneUnit, (-50f).sceneUnit), Ship.TEAM_PLAYER, withAI = false, drawOrder = DrawOrder.PLAYER_SHIP),
            SpawnSlot("player_ship", SceneOffset((-300f).sceneUnit, 50f.sceneUnit), Ship.TEAM_PLAYER, withAI = false, drawOrder = DrawOrder.PLAYER_SHIP),
            SpawnSlot("enemy_light", SceneOffset(300f.sceneUnit, (-120f).sceneUnit), Ship.TEAM_ENEMY, withAI = true, drawOrder = DrawOrder.ENEMY_SHIP),
            SpawnSlot("enemy_medium", SceneOffset(350f.sceneUnit, 0f.sceneUnit), Ship.TEAM_ENEMY, withAI = true, drawOrder = DrawOrder.ENEMY_SHIP),
            SpawnSlot("enemy_heavy", SceneOffset(300f.sceneUnit, 120f.sceneUnit), Ship.TEAM_ENEMY, withAI = true, drawOrder = DrawOrder.ENEMY_SHIP),
        )

        // Evaluate the spawn gate once per unique design (designs may be reused
        // across multiple slots, e.g., two player ships share player_ship).
        // Slice B Unit 4: replaces the Slice A `.getOrThrow()` calls that would
        // crash the whole scene on any bundled design failing conversion.
        val gateResults: Map<String, SpawnGateResult> = spawnSlots
            .map { it.designName }.distinct()
            .associateWith { name ->
                val design = designs[name]
                    ?: return@associateWith SpawnGateResult.ConversionFailed(
                        "missing bundled design '$name'",
                    )
                evaluateSpawnGate(design, turretGuns)
            }

        // Create input controller for player ships (pre-spawn so it's ready to
        // register each new player ship as they land).
        val inputController = InputController(selectableTeamId = Ship.TEAM_PLAYER)
        actorManager.add(inputController)

        // Spawn loop — one slot at a time. Unready gates log and skip, letting
        // every other slot proceed independently. No single failure aborts the
        // scene setup.
        for (slot in spawnSlots) {
            when (val gate = gateResults.getValue(slot.designName)) {
                is SpawnGateResult.Ready -> createShip(
                    config = gate.config,
                    position = slot.position,
                    teamId = slot.teamId,
                    targetProvider = if (slot.teamId == Ship.TEAM_PLAYER) {
                        { enemyShips }
                    } else {
                        { playerShips }
                    },
                    aiModules = if (slot.withAI) listOf(BasicAI()) else emptyList(),
                    drawOrder = slot.drawOrder,
                )

                is SpawnGateResult.ConversionFailed -> println(
                    "[combat] skipped slot '${slot.designName}': conversion failed — ${gate.reason}",
                )

                is SpawnGateResult.Unflightworthy -> println(
                    "[combat] skipped slot '${slot.designName}': unflightworthy — " +
                        "mass=${gate.totalMass}, lift=${gate.totalLift}",
                )
            }
        }

        // Wire input controller with whatever player ships successfully spawned.
        for (ship in playerShips) {
            inputController.registerShip(ship)
        }

        // Add debug visualiser for all ships
        val debugVisualiser = DebugVisualiser()
        actorManager.add(debugVisualiser)

        // Post-loop fallback: if either team ended up with zero spawned ships
        // (every slot on that team failed a gate), resolve the match immediately
        // so the game doesn't hang waiting for destruction events that can't fire.
        val startupResult = when {
            playerShips.isEmpty() && enemyShips.isEmpty() -> GameResult.DEFEAT // no-one to play
            playerShips.isEmpty() -> GameResult.DEFEAT
            enemyShips.isEmpty() -> GameResult.VICTORY
            else -> null
        }
        if (startupResult != null) {
            println("[combat] match resolved at startup: $startupResult (no opponents left to spawn)")
            _gameResult.value = startupResult
            stateManager.updateIsRunning(false)
            onGameResult?.invoke(startupResult)
        }
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

        ship.onLifecycleTransition = ::onShipLifecycleTransition

        when (teamId) {
            Ship.TEAM_PLAYER -> playerShips.add(ship)
            Ship.TEAM_ENEMY -> enemyShips.add(ship)
        }

        actorManager.add(ship)
        return ship
    }

    /**
     * Single hook for all ship lifecycle transitions. Branches on [newState] to
     * separate observer concerns (match-tally fires on any transition, via the
     * `none { is Active }` filter) from terminal concerns (cause-tagged logging
     * and backing-list cleanup fire only on [ShipLifecycle.Destroyed]).
     *
     * Victory fires the instant a Keel is destroyed (entry into
     * [ShipLifecycle.LiftFailed]), even though the ship's actor remains in the
     * scene for the drift window. The ship removes itself from the actor manager
     * when it transitions to `Destroyed` — [GameStateManager] only mutates its
     * own backing team lists here.
     *
     * Intentionally does **not** pause the Kubriko loop when the result fires:
     * doing so freezes any in-flight drift animations mid-countdown, breaking the
     * brainstorm's "visibly drift before despawning" success criterion for the
     * last-ship-on-team case. The UI overlays sit on top of the running scene,
     * and ships self-remove on their terminal Destroyed transition.
     */
    internal fun onShipLifecycleTransition(ship: Ship, newState: ShipLifecycle) {
        // Match-tally on every transition. Gate on _gameResult so we don't re-fire
        // after the match has already been resolved (e.g., the terminal
        // LiftFailed → Destroyed(LIFT_FAILURE) transition on the last enemy ship).
        if (_gameResult.value == null) {
            val result = when {
                playerShips.none { it.lifecycle is ShipLifecycle.Active } -> GameResult.DEFEAT
                enemyShips.none { it.lifecycle is ShipLifecycle.Active } -> GameResult.VICTORY
                else -> null
            }
            if (result != null) {
                _gameResult.value = result
                onGameResult?.invoke(result)
            }
        }

        // Terminal cleanup on Destroyed.
        if (newState is ShipLifecycle.Destroyed) {
            println("[combat] ${ship.teamId} ship destroyed: ${newState.cause}")
            when (ship.teamId) {
                Ship.TEAM_PLAYER -> playerShips.remove(ship)
                Ship.TEAM_ENEMY -> enemyShips.remove(ship)
            }
        }
    }

    /**
     * Register a pre-built [ship] directly into its team's backing list and wire
     * its lifecycle hook. Used by unit tests that drive the transition hook
     * without going through [createShip] — that path calls `actorManager.add`,
     * which requires a live Kubriko harness to satisfy child-actor `onAdded`.
     */
    internal fun registerShipForTest(ship: Ship) {
        ship.onLifecycleTransition = ::onShipLifecycleTransition
        when (ship.teamId) {
            Ship.TEAM_PLAYER -> playerShips.add(ship)
            Ship.TEAM_ENEMY -> enemyShips.add(ship)
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

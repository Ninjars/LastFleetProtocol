package jez.lastfleetprotocol.prototype.components.game.managers

import com.pandulapeter.kubriko.actor.traits.Unique
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
import jez.lastfleetprotocol.prototype.components.game.debug.DebugVisualiser
import jez.lastfleetprotocol.prototype.components.game.input.InputController
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import jez.lastfleetprotocol.prototype.components.gamecore.data.ShipConfig
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.DemoScenarioPreset
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.SpawnSlotConfig
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

    /**
     * Most recently launched slot list. Updated on every `startScene` call;
     * read by `restartScene` so the result-screen "Restart" button replays
     * whatever scene the dev launched (custom scenario or demo). Deliberately
     * NOT cleared by `clearScene` — between match end and restart, this is
     * the only record of "what was running."
     */
    internal var lastLaunched: List<SpawnSlotConfig>? = null
        private set

    fun setPaused(paused: Boolean) {
        if (paused == !stateManager.isRunning.value) return
        stateManager.updateIsRunning(!paused)
    }

    /**
     * Production demo entry point — preserves observable behaviour of the
     * pre-refactor `startDemoScene`. Reads the canonical layout from
     * [DemoScenarioPreset.SLOTS] (Unit 7 pins the shape).
     */
    suspend fun startDemoScene() = startScene(DemoScenarioPreset.SLOTS)

    /**
     * Generic scene entry point: load designs, evaluate spawn gates, then
     * iterate [slots] spawning ships team-by-team. Used by the demo path
     * (via [startDemoScene]) and by the scenario builder's launch path.
     *
     * Caches [slots] in [lastLaunched] so [restartScene] can replay the same
     * scene after `clearScene`. Empty slot lists fall through the post-loop
     * fallback to an immediate DEFEAT, matching the previous all-gates-failed
     * behaviour.
     */
    suspend fun startScene(slots: List<SpawnSlotConfig>) {
        lastLaunched = slots
        stateManager.updateIsRunning(true)

        // Load designs and turret guns (cached after first load)
        val designs = shipDesignLoader.loadAll()
        val turretGuns = turretGunLoader.load()

        // Evaluate the spawn gate once per unique design (designs may be reused
        // across multiple slots, e.g., two player ships share player_ship).
        // Slice B Unit 4: replaces the Slice A `.getOrThrow()` calls that would
        // crash the whole scene on any bundled design failing conversion.
        val gateResults: Map<String, SpawnGateResult> = slots
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
        for (slot in slots) {
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

    /**
     * Restart the most recently launched scene. Replays the cached
     * [lastLaunched] slot list (custom scenario or demo) — `clearScene` does
     * not clear that cache, so the result-screen "Restart" button always
     * re-runs whatever the dev was playing. Falls back to the demo if no
     * `startScene` has run yet (defensive — shouldn't happen in normal flow).
     */
    suspend fun restartScene() {
        _gameResult.value = null
        clearScene()
        startScene(lastLaunched ?: DemoScenarioPreset.SLOTS)
    }

    /**
     * Reset Kubriko-side actor state so a fresh scene can be set up. Does NOT
     * clear [lastLaunched] — that cache decouples restart from scene transitions
     * so a result → clear → restart cycle still replays the right slots.
     */
    fun clearScene() {
        actorManager.removeAll()
        playerShips.clear()
        enemyShips.clear()
    }
}

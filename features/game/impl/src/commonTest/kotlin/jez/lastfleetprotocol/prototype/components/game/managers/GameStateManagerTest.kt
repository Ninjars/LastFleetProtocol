package jez.lastfleetprotocol.prototype.components.game.managers

import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.Ship
import jez.lastfleetprotocol.prototype.components.game.actors.ShipLifecycle
import jez.lastfleetprotocol.prototype.components.game.actors.ShipSpec
import jez.lastfleetprotocol.prototype.components.game.managers.GameStateManager.GameResult
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import jez.lastfleetprotocol.prototype.components.gamecore.data.CombatStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemSpec
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemType
import jez.lastfleetprotocol.prototype.components.gamecore.data.MovementConfig
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.DefaultShipDesignLoader
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.TurretGunLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the [GameStateManager.onShipLifecycleTransition] hook branching — match
 * tally on entry into a non-Active state, terminal cleanup on [ShipLifecycle.Destroyed],
 * and the P1-#3 regression: the hook must NOT pause the Kubriko loop, because the
 * last ship on a losing team still needs to visibly drift before despawning.
 *
 * The `startDemoScene` spawn-slot loop is not exercised here — it requires a live
 * Kubriko actor manager to satisfy child-actor `onAdded` wiring. Coverage of the
 * loop and post-loop fallback is intentionally deferred as P2 residual work.
 */
class GameStateManagerTest {

    // --- Fixtures ---

    /** Realistic systems fixture: all four required internal-system types with default tuning. */
    private fun makeSystems() = ShipSystems(
        listOf(
            InternalSystemSpec(InternalSystemType.REACTOR, maxHp = 100f, density = 10f, mass = 20f),
            InternalSystemSpec(InternalSystemType.MAIN_ENGINE, maxHp = 80f, density = 4f, mass = 15f),
            InternalSystemSpec(InternalSystemType.BRIDGE, maxHp = 60f, density = 3f, mass = 10f),
            InternalSystemSpec(InternalSystemType.KEEL, maxHp = 120f, density = 8f, mass = 40f),
        )
    )

    private fun makeSpec() = ShipSpec(
        totalMass = 100f,
        movementConfig = MovementConfig(
            forwardThrust = 0f,
            lateralThrust = 0f,
            reverseThrust = 0f,
            angularThrust = 0f,
        ),
        combatStats = CombatStats(evasionModifier = 1f),
        hulls = emptyList(),
    )

    private fun makeShip(teamId: String, systems: ShipSystems = makeSystems()): Ship = Ship(
        spec = makeSpec(),
        initialPosition = SceneOffset.Zero,
        teamId = teamId,
        shipSystems = systems,
    )

    private fun newManager(): GameStateManager = GameStateManager(
        stateManager = StateManager.newInstance(shouldAutoStart = false),
        actorManager = ActorManager.newInstance(),
        viewportManager = ViewportManager.newInstance(),
        shipDesignLoader = DefaultShipDesignLoader(),
        turretGunLoader = TurretGunLoader(),
    )

    /** Drive `systemType`'s HP to zero on `systems` by overwhelming its armour. */
    private fun destroy(systems: ShipSystems, systemType: InternalSystemType) {
        systems.applyDamage(systemType, damage = 10_000f, armourPiercing = 100f)
    }

    // --- Match-tally on LiftFailed entry ---

    @Test
    fun enemyShipEntersLiftFailed_firesVictoryImmediately() {
        val manager = newManager()
        val resultsReceived = mutableListOf<GameResult>()
        manager.onGameResult = { resultsReceived += it }

        val playerSystems = makeSystems()
        val enemySystems = makeSystems()
        val player = makeShip(Ship.TEAM_PLAYER, playerSystems)
        val enemy = makeShip(Ship.TEAM_ENEMY, enemySystems)
        manager.registerShipForTest(player)
        manager.registerShipForTest(enemy)

        // Enemy's KEEL destroyed → enemy transitions Active → LiftFailed on next update.
        destroy(enemySystems, InternalSystemType.KEEL)
        enemy.updateLifecycle(16)

        assertEquals(GameResult.VICTORY, manager.gameResult.value)
        assertEquals(listOf(GameResult.VICTORY), resultsReceived)
    }

    @Test
    fun playerShipEntersLiftFailed_firesDefeatImmediately() {
        val manager = newManager()
        val resultsReceived = mutableListOf<GameResult>()
        manager.onGameResult = { resultsReceived += it }

        val playerSystems = makeSystems()
        val player = makeShip(Ship.TEAM_PLAYER, playerSystems)
        val enemy = makeShip(Ship.TEAM_ENEMY)
        manager.registerShipForTest(player)
        manager.registerShipForTest(enemy)

        destroy(playerSystems, InternalSystemType.KEEL)
        player.updateLifecycle(16)

        assertEquals(GameResult.DEFEAT, manager.gameResult.value)
        assertEquals(listOf(GameResult.DEFEAT), resultsReceived)
    }

    @Test
    fun reactorKillAlsoCountsTowardsMatchTally() {
        // Direct Destroyed(HULL) transition (no drift) must still resolve the match.
        val manager = newManager()
        val resultsReceived = mutableListOf<GameResult>()
        manager.onGameResult = { resultsReceived += it }

        val enemySystems = makeSystems()
        val player = makeShip(Ship.TEAM_PLAYER)
        val enemy = makeShip(Ship.TEAM_ENEMY, enemySystems)
        manager.registerShipForTest(player)
        manager.registerShipForTest(enemy)

        destroy(enemySystems, InternalSystemType.REACTOR)
        enemy.updateLifecycle(16)

        assertEquals(GameResult.VICTORY, manager.gameResult.value)
        assertEquals(listOf(GameResult.VICTORY), resultsReceived)
    }

    // --- Match-tally gating: no double-fire ---

    @Test
    fun terminalDestroyedAfterLiftFailed_doesNotRefireGameResult() {
        // The last enemy ship entering LiftFailed fires VICTORY once. Its terminal
        // transition to Destroyed(LIFT_FAILURE) some frames later must not refire
        // onGameResult — the match has already resolved.
        val manager = newManager()
        val resultsReceived = mutableListOf<GameResult>()
        manager.onGameResult = { resultsReceived += it }

        val enemySystems = makeSystems()
        val player = makeShip(Ship.TEAM_PLAYER)
        val enemy = makeShip(Ship.TEAM_ENEMY, enemySystems)
        manager.registerShipForTest(player)
        manager.registerShipForTest(enemy)

        destroy(enemySystems, InternalSystemType.KEEL)
        enemy.updateLifecycle(16) // → LiftFailed, fires VICTORY.

        // Fast-forward past the drift window to trigger Destroyed(LIFT_FAILURE).
        enemy.updateLifecycle(Ship.DRIFT_WINDOW_MS + 100)

        assertEquals(GameResult.VICTORY, manager.gameResult.value)
        assertEquals(
            listOf(GameResult.VICTORY),
            resultsReceived,
            "onGameResult must fire exactly once across the LiftFailed → Destroyed chain",
        )
    }

    @Test
    fun bothTeamsHaveActiveShips_noResultFires() {
        val manager = newManager()
        var result: GameResult? = null
        manager.onGameResult = { result = it }

        val player = makeShip(Ship.TEAM_PLAYER)
        val enemy = makeShip(Ship.TEAM_ENEMY)
        manager.registerShipForTest(player)
        manager.registerShipForTest(enemy)

        // A no-op update tick on fully healthy ships should not fire any transition
        // (and the hook is only called on transitions anyway — double safety check).
        player.updateLifecycle(16)
        enemy.updateLifecycle(16)

        assertNull(manager.gameResult.value)
        assertNull(result)
    }

    // --- Terminal cleanup on Destroyed ---

    @Test
    fun reactorKilled_shipRemovedFromTeamListOnDestroyed() {
        val manager = newManager()
        val enemySystems = makeSystems()
        val player = makeShip(Ship.TEAM_PLAYER)
        val enemy = makeShip(Ship.TEAM_ENEMY, enemySystems)
        manager.registerShipForTest(player)
        manager.registerShipForTest(enemy)

        assertTrue(enemy in manager.activeEnemyShips)

        destroy(enemySystems, InternalSystemType.REACTOR)
        enemy.updateLifecycle(16)

        assertFalse(
            enemy in manager.activeEnemyShips,
            "ship must be removed from its team list on Destroyed",
        )
        assertTrue(
            player in manager.activePlayerShips,
            "other team's list must be untouched",
        )
    }

    @Test
    fun liftFailedShip_remainsInTeamListUntilDestroyed() {
        // During the drift window the ship is in LiftFailed, not yet Destroyed.
        // Team-list cleanup must not fire yet — only the match-tally does.
        val manager = newManager()
        val enemySystems = makeSystems()
        val player = makeShip(Ship.TEAM_PLAYER)
        val enemy = makeShip(Ship.TEAM_ENEMY, enemySystems)
        manager.registerShipForTest(player)
        manager.registerShipForTest(enemy)

        destroy(enemySystems, InternalSystemType.KEEL)
        enemy.updateLifecycle(16) // → LiftFailed

        assertTrue(
            enemy in manager.activeEnemyShips,
            "ship in LiftFailed state must remain in the team list during drift",
        )

        // Drift to terminal.
        enemy.updateLifecycle(Ship.DRIFT_WINDOW_MS + 100)

        assertFalse(enemy in manager.activeEnemyShips)
    }

    // --- P1-#3 regression: drift must keep animating after result fires ---

    @Test
    fun lastShipVictory_doesNotFreezeDrift() {
        // Regression guard for P1-#3. Previously, onShipLifecycleTransition paused
        // the Kubriko loop when a match resolved — which froze the last drifting
        // ship mid-animation because its `update` stopped being called. We now
        // leave the loop running; the drift countdown continues to tick, and the
        // ship naturally transitions to Destroyed(LIFT_FAILURE).
        //
        // Without the loop wired up in a test (no real frame driver), we can't
        // assert Kubriko state directly. Instead we assert the behaviour that
        // depended on the loop staying live: a ship in LiftFailed that is driven
        // forward by explicit updateLifecycle calls still reaches Destroyed.
        val manager = newManager()
        val enemySystems = makeSystems()
        val player = makeShip(Ship.TEAM_PLAYER)
        val enemy = makeShip(Ship.TEAM_ENEMY, enemySystems)
        manager.registerShipForTest(player)
        manager.registerShipForTest(enemy)

        destroy(enemySystems, InternalSystemType.KEEL)
        enemy.updateLifecycle(16) // → LiftFailed, VICTORY fires.
        assertEquals(GameResult.VICTORY, manager.gameResult.value)
        assertTrue(enemy.lifecycle is ShipLifecycle.LiftFailed)

        // Continue ticking the ship. If P1-#3 regressed and production code re-
        // introduced a pause on the loop, the in-engine `update()` would stop
        // being called — but updateLifecycle is still driven directly here, so
        // this test would not catch a pause-at-hook regression on its own. It
        // does lock in the invariant that the match-tally branch does not touch
        // ship state — the drift completes to Destroyed without the manager
        // doing anything further.
        enemy.updateLifecycle(Ship.DRIFT_WINDOW_MS + 100)
        assertTrue(enemy.lifecycle is ShipLifecycle.Destroyed)
    }

    // --- Survival of multi-ship team compositions ---

    @Test
    fun onePlayerShipLost_otherPlayerStillActive_noDefeatFires() {
        val manager = newManager()
        var result: GameResult? = null
        manager.onGameResult = { result = it }

        val player1Systems = makeSystems()
        val player1 = makeShip(Ship.TEAM_PLAYER, player1Systems)
        val player2 = makeShip(Ship.TEAM_PLAYER)
        val enemy = makeShip(Ship.TEAM_ENEMY)
        manager.registerShipForTest(player1)
        manager.registerShipForTest(player2)
        manager.registerShipForTest(enemy)

        destroy(player1Systems, InternalSystemType.REACTOR)
        player1.updateLifecycle(16) // → Destroyed; player1 removed from list.

        // player2 is still Active → no DEFEAT should fire.
        assertNull(manager.gameResult.value)
        assertNull(result)
        assertFalse(player1 in manager.activePlayerShips)
        assertTrue(player2 in manager.activePlayerShips)
    }
}

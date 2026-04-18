package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import jez.lastfleetprotocol.prototype.components.gamecore.data.CombatStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemSpec
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemType
import jez.lastfleetprotocol.prototype.components.gamecore.data.MovementConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Slice B Unit 3 — verifies the [ShipLifecycle] state machine, the two-hook
 * architecture (`onLifecycleTransition` + `onDestroyedCallback`), and the drift
 * window. These tests drive [Ship.updateLifecycle] directly; side effects that
 * require a Kubriko harness (actor removal, draw scope) are not exercised here.
 */
class ShipLifecycleTest {

    // --- Test fixtures ---

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

    private fun makeShip(systems: ShipSystems = makeSystems()) = Ship(
        spec = makeSpec(),
        initialPosition = SceneOffset.Zero,
        teamId = Ship.TEAM_ENEMY,
        shipSystems = systems,
    )

    // --- Initial state ---

    @Test
    fun newShip_startsActiveAndIsValidTarget() {
        val ship = makeShip()
        assertIs<ShipLifecycle.Active>(ship.lifecycle)
        assertTrue(ship.isValidTarget())
    }

    @Test
    fun shipWithIntactSystems_staysActiveOnUpdate() {
        val ship = makeShip()
        ship.updateLifecycle(16)
        assertIs<ShipLifecycle.Active>(ship.lifecycle)
    }

    // --- Reactor destruction → Destroyed(HULL) ---

    @Test
    fun reactorDestroyed_transitionsToDestroyedHull_fireBothHooks() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        val transitions = mutableListOf<ShipLifecycle>()
        val destructions = mutableListOf<DestructionCause>()
        ship.onLifecycleTransition = { _, next -> transitions += next }
        ship.onDestroyedCallback = { _, cause -> destructions += cause }

        // Drive reactor HP to zero.
        systems.applyDamage(InternalSystemType.REACTOR, damage = 10_000f, armourPiercing = 20f)
        assertTrue(systems.isReactorDestroyed())

        ship.updateLifecycle(16)

        assertIs<ShipLifecycle.Destroyed>(ship.lifecycle).also {
            assertEquals(DestructionCause.HULL, it.cause)
        }
        assertEquals(1, transitions.size)
        assertIs<ShipLifecycle.Destroyed>(transitions.first())
        assertEquals(listOf(DestructionCause.HULL), destructions)
        assertFalse(ship.isValidTarget())
    }

    // --- Keel destruction → LiftFailed → drift → Destroyed(LIFT_FAILURE) ---

    @Test
    fun keelDestroyed_transitionsToLiftFailed_notYetDestroyed() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        val transitions = mutableListOf<ShipLifecycle>()
        val destructions = mutableListOf<DestructionCause>()
        ship.onLifecycleTransition = { _, next -> transitions += next }
        ship.onDestroyedCallback = { _, cause -> destructions += cause }

        systems.applyDamage(InternalSystemType.KEEL, damage = 10_000f, armourPiercing = 20f)
        assertTrue(systems.isKeelDestroyed())

        ship.updateLifecycle(16)

        val lifeFailed = assertIs<ShipLifecycle.LiftFailed>(ship.lifecycle)
        assertEquals(Ship.DRIFT_WINDOW_MS, lifeFailed.remainingMs)
        assertEquals(1, transitions.size)
        assertIs<ShipLifecycle.LiftFailed>(transitions.first())
        assertTrue(destructions.isEmpty(), "onDestroyedCallback must not fire at LiftFailed entry")
        assertFalse(ship.isValidTarget(), "LiftFailed ship is not a valid target")
    }

    @Test
    fun liftFailed_countdownDecrementsPerFrame() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        systems.applyDamage(InternalSystemType.KEEL, damage = 10_000f, armourPiercing = 20f)
        ship.updateLifecycle(16) // → LiftFailed(3000)
        assertEquals(Ship.DRIFT_WINDOW_MS, (ship.lifecycle as ShipLifecycle.LiftFailed).remainingMs)

        ship.updateLifecycle(1000)
        assertEquals(
            Ship.DRIFT_WINDOW_MS - 1000,
            (ship.lifecycle as ShipLifecycle.LiftFailed).remainingMs,
        )

        ship.updateLifecycle(500)
        assertEquals(
            Ship.DRIFT_WINDOW_MS - 1500,
            (ship.lifecycle as ShipLifecycle.LiftFailed).remainingMs,
        )
    }

    @Test
    fun liftFailed_countdownReachesZero_transitionsToDestroyedLiftFailure() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        val transitions = mutableListOf<ShipLifecycle>()
        val destructions = mutableListOf<DestructionCause>()
        ship.onLifecycleTransition = { _, next -> transitions += next }
        ship.onDestroyedCallback = { _, cause -> destructions += cause }

        systems.applyDamage(InternalSystemType.KEEL, damage = 10_000f, armourPiercing = 20f)
        ship.updateLifecycle(16) // Active → LiftFailed

        // Fast-forward past the drift window
        ship.updateLifecycle(Ship.DRIFT_WINDOW_MS + 100)

        val destroyed = assertIs<ShipLifecycle.Destroyed>(ship.lifecycle)
        assertEquals(DestructionCause.LIFT_FAILURE, destroyed.cause)
        // onLifecycleTransition fired twice: once for LiftFailed, once for Destroyed.
        assertEquals(2, transitions.size)
        assertIs<ShipLifecycle.LiftFailed>(transitions[0])
        assertIs<ShipLifecycle.Destroyed>(transitions[1])
        // onDestroyedCallback fired exactly once, at the terminal transition.
        assertEquals(listOf(DestructionCause.LIFT_FAILURE), destructions)
    }

    // --- Reactor-priority: reactor kill wins over same-frame keel kill ---

    @Test
    fun sameFrameReactorAndKeelDestruction_resolvesAsDestroyedHull() {
        // A single massive hit zeros both KEEL and REACTOR. updateLifecycle runs
        // after damage is applied; reactor check wins → Destroyed(HULL) directly,
        // no transient LiftFailed observed externally.
        val systems = makeSystems()
        val ship = makeShip(systems)
        val transitions = mutableListOf<ShipLifecycle>()
        val destructions = mutableListOf<DestructionCause>()
        ship.onLifecycleTransition = { _, next -> transitions += next }
        ship.onDestroyedCallback = { _, cause -> destructions += cause }

        systems.applyDamage(InternalSystemType.REACTOR, damage = 10_000f, armourPiercing = 20f)
        systems.applyDamage(InternalSystemType.KEEL, damage = 10_000f, armourPiercing = 20f)

        ship.updateLifecycle(16)

        val destroyed = assertIs<ShipLifecycle.Destroyed>(ship.lifecycle)
        assertEquals(DestructionCause.HULL, destroyed.cause)
        // Only one transition observed externally — no transient LiftFailed.
        assertEquals(1, transitions.size)
        assertIs<ShipLifecycle.Destroyed>(transitions.first())
        assertEquals(listOf(DestructionCause.HULL), destructions)
    }

    // --- Hook idempotence ---

    @Test
    fun alreadyDestroyedShip_updateLifecycleIsNoOp() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        val transitions = mutableListOf<ShipLifecycle>()
        val destructions = mutableListOf<DestructionCause>()
        ship.onLifecycleTransition = { _, next -> transitions += next }
        ship.onDestroyedCallback = { _, cause -> destructions += cause }

        systems.applyDamage(InternalSystemType.REACTOR, damage = 10_000f, armourPiercing = 20f)
        ship.updateLifecycle(16) // → Destroyed(HULL)

        // Further updates on a Destroyed ship should NOT re-fire hooks.
        ship.updateLifecycle(16)
        ship.updateLifecycle(16)

        assertEquals(1, transitions.size)
        assertEquals(1, destructions.size)
    }

    // --- Hook is never invoked when there is no transition ---

    @Test
    fun noTransition_hooksNotInvoked() {
        val ship = makeShip()
        var transitionCount = 0
        var destructionCount = 0
        ship.onLifecycleTransition = { _, _ -> transitionCount++ }
        ship.onDestroyedCallback = { _, _ -> destructionCount++ }

        repeat(10) { ship.updateLifecycle(16) }

        assertEquals(0, transitionCount)
        assertEquals(0, destructionCount)
    }

    // --- Missing hook subscribers don't crash ---

    @Test
    fun nullHooks_updateLifecycleSilentlySucceeds() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        // Hooks intentionally not assigned.
        systems.applyDamage(InternalSystemType.REACTOR, damage = 10_000f, armourPiercing = 20f)
        ship.updateLifecycle(16)
        assertIs<ShipLifecycle.Destroyed>(ship.lifecycle)
    }

    // --- New state value passed to onLifecycleTransition matches ship.lifecycle ---

    @Test
    fun onLifecycleTransition_receivesNewState() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        var observed: ShipLifecycle? = null
        ship.onLifecycleTransition = { s, next ->
            // The parameter matches the ship's live lifecycle.
            observed = next
            assertEquals(s.lifecycle, next)
        }
        systems.applyDamage(InternalSystemType.KEEL, damage = 10_000f, armourPiercing = 20f)
        ship.updateLifecycle(16)
        assertIs<ShipLifecycle.LiftFailed>(observed)
    }

    // --- Interaction with isValidTarget across states ---

    @Test
    fun isValidTarget_returnsFalseDuringLiftFailed() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        assertTrue(ship.isValidTarget())
        systems.applyDamage(InternalSystemType.KEEL, damage = 10_000f, armourPiercing = 20f)
        ship.updateLifecycle(16)
        assertFalse(ship.isValidTarget(), "LiftFailed ship is not a valid target")
    }

    @Test
    fun isValidTarget_returnsFalseAfterDestroyed() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        systems.applyDamage(InternalSystemType.REACTOR, damage = 10_000f, armourPiercing = 20f)
        ship.updateLifecycle(16)
        assertFalse(ship.isValidTarget())
    }

    // --- Smoke: Active transition returns null when reactor not destroyed, keel not destroyed ---

    @Test
    fun partialSystemDamage_doesNotTransition() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        systems.applyDamage(InternalSystemType.REACTOR, damage = 30f, armourPiercing = 20f)
        systems.applyDamage(InternalSystemType.KEEL, damage = 30f, armourPiercing = 20f)
        assertFalse(systems.isReactorDestroyed())
        assertFalse(systems.isKeelDestroyed())

        ship.updateLifecycle(16)
        assertIs<ShipLifecycle.Active>(ship.lifecycle)
    }
}

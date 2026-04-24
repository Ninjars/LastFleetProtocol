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
 * Slice B Unit 3 — verifies the [ShipLifecycle] state machine, the single
 * `onLifecycleTransition` hook (subscribers branch on the new state to separate
 * observer concerns from terminal cleanup), and the drift window. These tests
 * drive [Ship.updateLifecycle] directly; side effects that require a Kubriko
 * harness (actor removal, draw scope) are not exercised here.
 */
class ShipLifecycleTest {

    /**
     * Extracts `cause` only when the new state is `Destroyed` — the single-hook
     * subscription pattern that replaces the old separate `onDestroyedCallback`.
     */
    private fun capturingHooks(): Pair<MutableList<ShipLifecycle>, MutableList<DestructionCause>> {
        val transitions = mutableListOf<ShipLifecycle>()
        val destructions = mutableListOf<DestructionCause>()
        return transitions to destructions
    }

    private fun wireShip(
        ship: Ship,
        transitions: MutableList<ShipLifecycle>,
        destructions: MutableList<DestructionCause>,
    ) {
        ship.onLifecycleTransition = { _, next ->
            transitions += next
            if (next is ShipLifecycle.Destroyed) destructions += next.cause
        }
    }

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
    fun reactorDestroyed_transitionsToDestroyedHull() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        val (transitions, destructions) = capturingHooks()
        wireShip(ship, transitions, destructions)

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
        val (transitions, destructions) = capturingHooks()
        wireShip(ship, transitions, destructions)

        systems.applyDamage(InternalSystemType.KEEL, damage = 10_000f, armourPiercing = 20f)
        assertTrue(systems.isKeelDestroyed())

        ship.updateLifecycle(16)

        assertIs<ShipLifecycle.LiftFailed>(ship.lifecycle)
        assertEquals(Ship.DRIFT_WINDOW_MS, ship.driftRemainingMs)
        assertEquals(1, transitions.size)
        assertIs<ShipLifecycle.LiftFailed>(transitions.first())
        assertTrue(
            destructions.isEmpty(),
            "terminal cleanup must not fire at LiftFailed entry",
        )
        assertFalse(ship.isValidTarget(), "LiftFailed ship is not a valid target")
    }

    @Test
    fun liftFailed_countdownDecrementsPerFrame() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        systems.applyDamage(InternalSystemType.KEEL, damage = 10_000f, armourPiercing = 20f)
        ship.updateLifecycle(16) // Active → LiftFailed, drift countdown seeded.
        assertEquals(Ship.DRIFT_WINDOW_MS, ship.driftRemainingMs)

        ship.updateLifecycle(1000)
        assertEquals(Ship.DRIFT_WINDOW_MS - 1000, ship.driftRemainingMs)

        ship.updateLifecycle(500)
        assertEquals(Ship.DRIFT_WINDOW_MS - 1500, ship.driftRemainingMs)
    }

    @Test
    fun liftFailed_hookFiresOnceAcrossManyFrames() {
        // Regression: previously LiftFailed was a data class carrying remainingMs,
        // so `next == lifecycle` never held during the countdown and the transition
        // hook fired every frame. With LiftFailed as a data object, only the entry
        // transition and the terminal Destroyed transition should invoke the hook.
        val systems = makeSystems()
        val ship = makeShip(systems)
        val (transitions, _) = capturingHooks()
        val destructions = mutableListOf<DestructionCause>()
        wireShip(ship, transitions, destructions)

        systems.applyDamage(InternalSystemType.KEEL, damage = 10_000f, armourPiercing = 20f)
        ship.updateLifecycle(16) // → LiftFailed (1 transition)

        // Tick 100 frames inside the drift window. None should fire the hook.
        repeat(100) { ship.updateLifecycle(16) }

        assertEquals(
            1,
            transitions.size,
            "hook must not fire during the drift window — only on entry and exit",
        )
        assertIs<ShipLifecycle.LiftFailed>(ship.lifecycle)
    }

    @Test
    fun liftFailed_countdownReachesZero_transitionsToDestroyedLiftFailure() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        val (transitions, destructions) = capturingHooks()
        wireShip(ship, transitions, destructions)

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
        // Terminal-cleanup branch (cause capture) ran exactly once, at the Destroyed transition.
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
        val (transitions, destructions) = capturingHooks()
        wireShip(ship, transitions, destructions)

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
        val (transitions, destructions) = capturingHooks()
        wireShip(ship, transitions, destructions)

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
    fun noTransition_hookNotInvoked() {
        val ship = makeShip()
        var transitionCount = 0
        ship.onLifecycleTransition = { _, _ -> transitionCount++ }

        repeat(10) { ship.updateLifecycle(16) }

        assertEquals(0, transitionCount)
    }

    // --- Missing hook subscriber doesn't crash ---

    @Test
    fun nullHook_updateLifecycleSilentlySucceeds() {
        val systems = makeSystems()
        val ship = makeShip(systems)
        // Hook intentionally not assigned.
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

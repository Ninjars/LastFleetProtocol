package jez.lastfleetprotocol.prototype.components.game.systems

import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemSpec
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShipSystemsTest {

    private fun makeSpec(
        type: InternalSystemType,
        maxHp: Float = 100f,
        density: Float = 10f,
        mass: Float = 5f,
    ) = InternalSystemSpec(type = type, maxHp = maxHp, density = density, mass = mass)

    private fun defaultSpecs() = listOf(
        makeSpec(InternalSystemType.REACTOR),
        makeSpec(InternalSystemType.MAIN_ENGINE),
        makeSpec(InternalSystemType.BRIDGE),
        // Slice B: KEEL is part of every converted ship. Keep this fixture
        // representative of production so future tests don't accidentally
        // construct a Ship with a KEEL-less ShipSystems and crash updateLifecycle.
        makeSpec(InternalSystemType.KEEL),
    )

    // --- InternalSystem direct tests ---

    @Test
    fun system_takes30Damage_has70HpRemaining_notDisabled() {
        val system = InternalSystem(makeSpec(InternalSystemType.REACTOR))
        system.takeDamage(30f)

        assertEquals(70f, system.currentHp, "HP should be 70 after 30 damage")
        assertFalse(system.isDisabled, "Should not be disabled at 70 HP")
        assertFalse(system.isDestroyed, "Should not be destroyed at 70 HP")
    }

    @Test
    fun system_takes67Damage_becomesDisabled() {
        val system = InternalSystem(makeSpec(InternalSystemType.REACTOR))
        // disableThreshold = 100 * 2/3 = 66.67
        // disabled when currentHp <= maxHp - disableThreshold = 100 - 66.67 = 33.33
        system.takeDamage(67f)

        assertEquals(33f, system.currentHp, "HP should be 33 after 67 damage")
        assertTrue(system.isDisabled, "Should be disabled (33 HP <= 33.33 threshold)")
        assertFalse(system.isDestroyed, "Should not be destroyed at 33 HP")
    }

    @Test
    fun system_takes100Damage_becomesDestroyed() {
        val system = InternalSystem(makeSpec(InternalSystemType.REACTOR))
        system.takeDamage(100f)

        assertEquals(0f, system.currentHp, "HP should be 0 after 100 damage")
        assertTrue(system.isDisabled, "Destroyed system should also be disabled")
        assertTrue(system.isDestroyed, "Should be destroyed at 0 HP")
    }

    @Test
    fun destroyedSystem_ignoresFurtherDamage() {
        val system = InternalSystem(makeSpec(InternalSystemType.REACTOR))
        system.takeDamage(100f)
        val absorbed = system.takeDamage(50f)

        assertEquals(0f, absorbed, "Destroyed system should absorb 0 further damage")
        assertEquals(0f, system.currentHp, "HP should remain 0")
    }

    // --- ShipSystems capability queries ---

    @Test
    fun reactorDestroyed_isReactorDestroyedReturnsTrue() {
        val systems = ShipSystems(defaultSpecs())
        // Density 10, armourPiercing 10 => absorbs all damage
        systems.applyDamage(InternalSystemType.REACTOR, 100f, 10f)

        assertTrue(systems.isReactorDestroyed(), "Reactor should be destroyed")
    }

    @Test
    fun reactorDisabled_isPoweredFalse_canMoveFalse_hasFireControlFalse() {
        val systems = ShipSystems(defaultSpecs())
        // Disable reactor: need currentHp <= 33.33, so deal 67 damage
        systems.applyDamage(InternalSystemType.REACTOR, 67f, 10f)

        assertFalse(systems.isPowered(), "Should not be powered with disabled reactor")
        assertFalse(systems.canMove(), "Should not move without power")
        assertFalse(systems.hasFireControl(), "Should not have fire control without power")
        assertFalse(systems.canReceiveOrders(), "Should not receive orders without power")
    }

    @Test
    fun reactorIntact_engineDisabled_canMoveFalse_hasFireControlTrue() {
        val systems = ShipSystems(defaultSpecs())
        // Disable engine only
        systems.applyDamage(InternalSystemType.MAIN_ENGINE, 67f, 10f)

        assertTrue(systems.isPowered(), "Should be powered with intact reactor")
        assertFalse(systems.canMove(), "Should not move with disabled engine")
        assertTrue(systems.hasFireControl(), "Should have fire control with intact bridge")
        assertTrue(systems.canReceiveOrders(), "Should receive orders with intact bridge")
    }

    @Test
    fun bridgeDisabled_canReceiveOrdersFalse_hasFireControlFalse_canMoveTrue() {
        val systems = ShipSystems(defaultSpecs())
        // Disable bridge only
        systems.applyDamage(InternalSystemType.BRIDGE, 67f, 10f)

        assertTrue(systems.isPowered(), "Should be powered with intact reactor")
        assertTrue(systems.canMove(), "Should move with intact engine and power")
        assertFalse(systems.hasFireControl(), "Should not have fire control with disabled bridge")
        assertFalse(systems.canReceiveOrders(), "Should not receive orders with disabled bridge")
    }

    // --- Damage absorption based on density vs armourPiercing ---

    @Test
    fun applyDamage_densityEqualsAP_absorbsAllDamage() {
        val systems = ShipSystems(defaultSpecs())
        // density=10, armourPiercing=10 => absorbs all
        val absorbed = systems.applyDamage(InternalSystemType.REACTOR, 30f, 10f)

        assertEquals(30f, absorbed, "Should absorb all 30 damage when density >= AP")
        assertEquals(70f, systems.getSystem(InternalSystemType.REACTOR).currentHp)
    }

    @Test
    fun applyDamage_densityLessThanAP_absorbsPartialDamage() {
        // density=10, armourPiercing=20 => effective = 30 * (10/20) = 15
        val systems = ShipSystems(defaultSpecs())
        val absorbed = systems.applyDamage(InternalSystemType.REACTOR, 30f, 20f)

        assertEquals(15f, absorbed, "Should absorb 15 damage (30 * 10/20)")
        assertEquals(85f, systems.getSystem(InternalSystemType.REACTOR).currentHp)
    }

    @Test
    fun applyDamage_densityGreaterThanAP_absorbsAllDamage() {
        val systems = ShipSystems(defaultSpecs())
        // density=10, armourPiercing=5 => density >= AP, absorbs all
        val absorbed = systems.applyDamage(InternalSystemType.REACTOR, 30f, 5f)

        assertEquals(30f, absorbed, "Should absorb all 30 damage when density > AP")
        assertEquals(70f, systems.getSystem(InternalSystemType.REACTOR).currentHp)
    }

    @Test
    fun applyDamage_toDestroyedSystem_returnsZero() {
        val systems = ShipSystems(defaultSpecs())
        systems.applyDamage(InternalSystemType.REACTOR, 100f, 10f)
        val absorbed = systems.applyDamage(InternalSystemType.REACTOR, 50f, 10f)

        assertEquals(0f, absorbed, "Destroyed system should absorb no further damage")
    }
}

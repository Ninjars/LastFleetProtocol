package jez.lastfleetprotocol.prototype.components.game.data

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.ArmourStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.CombatStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.HullDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemSpec
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemType
import jez.lastfleetprotocol.prototype.components.gamecore.data.MovementConfig
import jez.lastfleetprotocol.prototype.components.gamecore.data.ShipConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShipConfigTest {

    private val triangleVertices = listOf(
        SceneOffset(10f.sceneUnit, 0f.sceneUnit),
        SceneOffset((-5f).sceneUnit, 5f.sceneUnit),
        SceneOffset((-5f).sceneUnit, (-5f).sceneUnit),
    )

    private fun makeConfig(
        hullMass: Float = 50f,
        armourDensity: Float = 2f,
        systems: List<InternalSystemSpec> = listOf(
            InternalSystemSpec(InternalSystemType.REACTOR, maxHp = 100f, density = 8f, mass = 20f),
        ),
    ) = ShipConfig(
        hulls = listOf(
            HullDefinition(
                vertices = triangleVertices,
                armour = ArmourStats(hardness = 5f, density = armourDensity),
                mass = hullMass,
            ),
        ),
        combatStats = CombatStats(evasionModifier = 0.1f),
        movementConfig = MovementConfig(
            forwardThrust = 1200f,
            lateralThrust = 500f,
            reverseThrust = 500f,
            angularThrust = 300f,
        ),
        internalSystems = systems,
        turretConfigs = emptyList(),
    )

    @Test
    fun totalMass_sumsAllHullsArmourAndSystems() {
        val config = makeConfig(hullMass = 50f, armourDensity = 2f)
        // hull mass = 50, armour contribution = 2 * 50 * 0.1 = 10, reactor = 20
        val expected = 50f + 10f + 20f
        assertEquals(expected, config.totalMass)
        assertTrue(config.totalMass > 0f)
    }

    @Test
    fun totalMass_multiHull_sumsAcrossHulls() {
        val config = ShipConfig(
            hulls = listOf(
                HullDefinition(triangleVertices, ArmourStats(5f, 2f), mass = 30f),
                HullDefinition(triangleVertices, ArmourStats(5f, 1f), mass = 20f),
            ),
            combatStats = CombatStats(evasionModifier = 0.1f),
            movementConfig = MovementConfig(1200f, 500f, 500f, 300f),
            internalSystems = emptyList(),
            turretConfigs = emptyList(),
        )
        // hull1: 30 + 2*30*0.1 = 36, hull2: 20 + 1*20*0.1 = 22
        assertEquals(58f, config.totalMass)
    }

    @Test
    fun zeroArmourDensity_stillProducesPositiveMass() {
        val config = makeConfig(hullMass = 20f, armourDensity = 0f)
        // hull mass = 20, armour = 0, reactor = 20
        assertEquals(40f, config.totalMass)
        assertTrue(config.totalMass > 0f)
    }

    @Test
    fun emptySystemsList_massFromHullOnly() {
        val config = makeConfig(hullMass = 50f, armourDensity = 0f, systems = emptyList())
        assertEquals(50f, config.totalMass)
    }
}

package jez.lastfleetprotocol.prototype.components.game.data

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.ArmourStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.HullDefinition
import kotlin.test.Test
import kotlin.test.assertTrue

class ShipConfigTest {

    @Test
    fun totalMass_sumsHullArmourAndSystems() {
        val config = DemoScenarioConfig.playerShipConfig
        val hullMass = config.hull.mass
        val armourMass = config.hull.armour.density * config.hull.mass * 0.1f
        val systemsMass = config.internalSystems.sumOf { it.mass.toDouble() }.toFloat()
        val expected = hullMass + armourMass + systemsMass

        assertTrue(
            config.totalMass == expected,
            "Total mass should be hull($hullMass) + armour($armourMass) + systems($systemsMass) = $expected, got ${config.totalMass}"
        )
        assertTrue(config.totalMass > 0f, "Total mass should be positive")
    }

    @Test
    fun demoConfigs_haveValidValues() {
        val configs = listOf(
            DemoScenarioConfig.playerShipConfig,
            DemoScenarioConfig.enemyShipLightConfig,
            DemoScenarioConfig.enemyShipMediumConfig,
            DemoScenarioConfig.enemyShipHeavyConfig,
        )

        for (config in configs) {
            assertTrue(config.totalMass > 0f, "Ship should have positive mass")
            assertTrue(config.movementConfig.forwardThrust > 0f, "Ship should have positive forward thrust")
            assertTrue(config.movementConfig.angularThrust > 0f, "Ship should have positive angular thrust")
            assertTrue(config.hull.vertices.size >= 3, "Hull should have at least 3 vertices")

            for (system in config.internalSystems) {
                assertTrue(system.maxHp > 0f, "${system.type} should have positive HP")
                assertTrue(system.density > 0f, "${system.type} should have positive density")
            }

            assertTrue(config.turretConfigs.isNotEmpty(), "Ship should have at least one turret")
        }
    }

    @Test
    fun demoConfigs_haveDistinctProfiles() {
        val light = DemoScenarioConfig.enemyShipLightConfig
        val heavy = DemoScenarioConfig.enemyShipHeavyConfig

        assertTrue(
            heavy.totalMass > light.totalMass,
            "Heavy ship should have more mass than light ship"
        )
        assertTrue(
            light.movementConfig.forwardThrust > heavy.movementConfig.forwardThrust,
            "Light ship should have more forward thrust than heavy ship"
        )
    }

    @Test
    fun zeroArmourDensity_stillProducesPositiveMass() {
        val hull = HullDefinition(
            vertices = listOf(
                SceneOffset(0f.sceneUnit, (-10f).sceneUnit),
                SceneOffset(10f.sceneUnit, 10f.sceneUnit),
                SceneOffset((-10f).sceneUnit, 10f.sceneUnit),
            ),
            armour = ArmourStats(hardness = 1f, density = 0f),
            mass = 20f,
        )
        val config = DemoScenarioConfig.playerShipConfig.copy(hull = hull)

        assertTrue(config.totalMass > 0f, "Should have positive mass even with zero armour density")
    }
}

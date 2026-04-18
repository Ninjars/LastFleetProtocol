package jez.lastfleetprotocol.prototype.components.game.managers

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.GunData
import jez.lastfleetprotocol.prototype.components.gamecore.data.ProjectileStats
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedKeel
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Slice B Unit 4 — verifies the two-stage spawn gate: structural conversion
 * must succeed AND the flightworthiness check (mass <= lift) must pass.
 *
 * These tests drive [evaluateSpawnGate] directly; they don't exercise the
 * GameStateManager coordination of skip-and-log + post-loop fallback, which
 * requires a Kubriko harness.
 */
class SpawnGateTest {

    private val triangleVertices = listOf(
        SceneOffset(10f.sceneUnit, 0f.sceneUnit),
        SceneOffset((-5f).sceneUnit, 5f.sceneUnit),
        SceneOffset((-5f).sceneUnit, (-5f).sceneUnit),
    )

    private val gunData = GunData(
        projectileStats = ProjectileStats(
            damage = 15f, armourPiercing = 6f, toHitModifier = 0.15f,
            speed = 200f, lifetimeMs = 4000,
        ),
        aimTolerance = AngleRadians.TwoPi / 1440f,
        magazineCapacity = 100,
        reloadMilliseconds = 2000,
        shotsPerBurst = 3,
        burstCycleMilliseconds = 100,
        cycleMilliseconds = 700,
    )

    private val turretGuns = mapOf("standard" to gunData)

    private fun makeKeelDef(
        id: String = "keel_def",
        mass: Float = 40f,
        lift: Float = 500f,
    ) = ItemDefinition(
        id = id,
        name = "Test Keel",
        vertices = triangleVertices,
        attributes = ItemAttributes.KeelAttributes(
            armour = SerializableArmourStats(3f, 1.5f),
            sizeCategory = "medium",
            mass = mass,
            maxHp = 150f,
            lift = lift,
            shipClass = "fighter",
        ),
    )

    private fun makeEngineDef(id: String = "engine_def", mass: Float = 15f) = ItemDefinition(
        id = id,
        name = "Engine",
        vertices = emptyList(),
        attributes = ItemAttributes.ModuleAttributes(
            systemType = "MAIN_ENGINE",
            maxHp = 80f, density = 4f, mass = mass,
            forwardThrust = 1200f, lateralThrust = 500f,
            reverseThrust = 500f, angularThrust = 300f,
        ),
    )

    private fun makeDesign(
        placedKeel: PlacedKeel? = PlacedKeel("pk", "keel_def", SceneOffset.Zero, 0f.rad),
        modules: List<PlacedModule> = emptyList(),
        itemDefs: List<ItemDefinition> = listOf(makeKeelDef()),
    ) = ShipDesign(
        name = "Test Ship",
        itemDefinitions = itemDefs,
        placedKeel = placedKeel,
        placedHulls = emptyList(),
        placedModules = modules,
        placedTurrets = emptyList(),
    )

    // --- Happy path ---

    @Test
    fun flightworthyDesign_returnsReady() {
        val result = evaluateSpawnGate(makeDesign(), turretGuns)
        val ready = assertIs<SpawnGateResult.Ready>(result)
        assertTrue(ready.config.hulls.isNotEmpty(), "Config should include the Keel as a hull piece")
    }

    @Test
    fun flightworthyDesignWithModules_returnsReady() {
        val design = makeDesign(
            modules = listOf(
                PlacedModule("pm", "engine_def", "MAIN_ENGINE", SceneOffset.Zero, 0f.rad, parentHullId = "pk"),
            ),
            itemDefs = listOf(makeKeelDef(), makeEngineDef()),
        )
        val result = evaluateSpawnGate(design, turretGuns)
        assertIs<SpawnGateResult.Ready>(result)
    }

    // --- Conversion-failure path ---

    @Test
    fun designWithoutKeel_returnsConversionFailed() {
        val design = makeDesign(placedKeel = null)
        val result = evaluateSpawnGate(design, turretGuns)
        val failed = assertIs<SpawnGateResult.ConversionFailed>(result)
        assertTrue(
            failed.reason.contains("no Keel", ignoreCase = true),
            "Reason should mention missing Keel: ${failed.reason}",
        )
    }

    @Test
    fun designWithMissingKeelDefinition_returnsConversionFailed() {
        val design = makeDesign(
            placedKeel = PlacedKeel("pk", "MISSING_DEF", SceneOffset.Zero, 0f.rad),
        )
        val result = evaluateSpawnGate(design, turretGuns)
        val failed = assertIs<SpawnGateResult.ConversionFailed>(result)
        assertTrue(failed.reason.contains("MISSING_DEF"))
    }

    @Test
    fun designWithUnknownSystemType_returnsConversionFailed() {
        val unknownDef = ItemDefinition(
            id = "plasma_def", name = "Plasma", vertices = emptyList(),
            attributes = ItemAttributes.ModuleAttributes(
                systemType = "PLASMA_CORE", maxHp = 100f, density = 8f, mass = 20f,
            ),
        )
        val design = makeDesign(
            modules = listOf(
                PlacedModule("pm", "plasma_def", "PLASMA_CORE", SceneOffset.Zero, 0f.rad, parentHullId = "pk"),
            ),
            itemDefs = listOf(makeKeelDef(), unknownDef),
        )
        val result = evaluateSpawnGate(design, turretGuns)
        val failed = assertIs<SpawnGateResult.ConversionFailed>(result)
        assertTrue(failed.reason.contains("PLASMA_CORE"))
    }

    // --- Flightworthiness path ---

    @Test
    fun designWithMassExceedingLift_returnsUnflightworthy() {
        // Keel mass = 200, armour adds 30 → keel mass = 230; lift = 100. Massively over.
        val heavyKeel = makeKeelDef(id = "heavy_keel", mass = 200f, lift = 100f)
        val design = makeDesign(
            placedKeel = PlacedKeel("pk", "heavy_keel", SceneOffset.Zero, 0f.rad),
            itemDefs = listOf(heavyKeel),
        )
        val result = evaluateSpawnGate(design, turretGuns)
        val unflight = assertIs<SpawnGateResult.Unflightworthy>(result)
        assertEquals(100f, unflight.totalLift)
        assertTrue(unflight.totalMass > unflight.totalLift, "mass must exceed lift to be unflightworthy")
    }

    @Test
    fun designWithZeroLiftKeel_returnsUnflightworthy() {
        val zeroLiftKeel = makeKeelDef(id = "zero_lift", mass = 10f, lift = 0f)
        val design = makeDesign(
            placedKeel = PlacedKeel("pk", "zero_lift", SceneOffset.Zero, 0f.rad),
            itemDefs = listOf(zeroLiftKeel),
        )
        val result = evaluateSpawnGate(design, turretGuns)
        val unflight = assertIs<SpawnGateResult.Unflightworthy>(result)
        assertEquals(0f, unflight.totalLift)
    }

    // --- Gate ordering: conversion fails before flightworthiness is even checked ---

    @Test
    fun conversionFailurePreemptsFlightworthinessCheck() {
        // No Keel AND mass would be over-lift — the conversion failure comes first.
        val design = makeDesign(placedKeel = null)
        val result = evaluateSpawnGate(design, turretGuns)
        assertIs<SpawnGateResult.ConversionFailed>(result)
    }
}

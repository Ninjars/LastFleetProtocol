package jez.lastfleetprotocol.prototype.components.game.combat

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.ArmourStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.CombatStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.ProjectileStats
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Deterministic random that returns a fixed sequence of floats.
 */
private class FixedRandom(private val values: List<Float>) : Random() {
    private var index = 0

    override fun nextFloat(): Float {
        val value = values[index % values.size]
        index++
        return value
    }

    override fun nextBits(bitCount: Int): Int = 0
}

class KineticImpactResolverTest {

    private fun defaultProjectile(
        damage: Float = 20f,
        armourPiercing: Float = 5f,
        toHitModifier: Float = 0.3f,
        speed: Float = 100f,
    ) = ProjectileStats(
        damage = damage,
        armourPiercing = armourPiercing,
        toHitModifier = toHitModifier,
        speed = speed,
        lifetimeMs = 5000,
    )

    private fun defaultArmour(
        hardness: Float = 3f,
        density: Float = 5f,
    ) = ArmourStats(hardness = hardness, density = density)

    // Velocity heading roughly straight at the target (into -Y direction)
    private val headOnVelocity = SceneOffset(0f.sceneUnit, (-100f).sceneUnit)
    // Normal pointing outward from target (toward bullet, i.e. +Y)
    private val headOnNormal = SceneOffset(0f.sceneUnit, 1f.sceneUnit)

    // --- Hit check tests ---

    @Test
    fun highToHit_lowEvasion_hits() {
        // random=0.5 + toHit=0.3 = 0.8 >= evasion=0.2 → hit
        val result = KineticImpactResolver.resolve(
            projectile = defaultProjectile(toHitModifier = 0.3f, armourPiercing = 10f),
            velocity = headOnVelocity,
            contactNormal = headOnNormal,
            armour = defaultArmour(hardness = 3f),
            combatStats = CombatStats(evasionModifier = 0.2f),
            random = FixedRandom(listOf(0.5f, 0.5f)),
        )
        assertIs<ImpactOutcome.Penetrate>(result)
    }

    @Test
    fun toHitJustBelowEvasion_misses() {
        // random=0.1 + toHit=0.1 = 0.2 < evasion=0.5 → miss
        val result = KineticImpactResolver.resolve(
            projectile = defaultProjectile(toHitModifier = 0.1f),
            velocity = headOnVelocity,
            contactNormal = headOnNormal,
            armour = defaultArmour(),
            combatStats = CombatStats(evasionModifier = 0.5f),
            random = FixedRandom(listOf(0.1f)),
        )
        assertIs<ImpactOutcome.Miss>(result)
    }

    // --- Ricochet test ---

    @Test
    fun shallowAngle_highHardness_ricochets() {
        // Velocity nearly parallel to hull surface → very low cos(angle) with normal
        // Velocity along X, normal along Y → dot product ≈ 0
        val grazingVelocity = SceneOffset(100f.sceneUnit, 0.1f.sceneUnit)
        val surfaceNormal = SceneOffset(0f.sceneUnit, 1f.sceneUnit)

        // cos(angle) ≈ 0.001, threshold = hardness(10) * 0.05 = 0.5 → ricochet
        val result = KineticImpactResolver.resolve(
            projectile = defaultProjectile(toHitModifier = 0.5f),
            velocity = grazingVelocity,
            contactNormal = surfaceNormal,
            armour = ArmourStats(hardness = 10f, density = 5f),
            combatStats = CombatStats(evasionModifier = 0.1f),
            random = FixedRandom(listOf(0.5f)),
        )
        assertIs<ImpactOutcome.Ricochet>(result)
    }

    // --- Deflection test ---

    @Test
    fun lowAP_highHardness_deflects() {
        // AP=1 + random(0.0)*0.2 = 1.0 < hardness=3.0 → deflect
        val result = KineticImpactResolver.resolve(
            projectile = defaultProjectile(
                toHitModifier = 0.5f,
                armourPiercing = 1f,
            ),
            velocity = headOnVelocity,
            contactNormal = headOnNormal,
            armour = defaultArmour(hardness = 3f),
            combatStats = CombatStats(evasionModifier = 0.1f),
            random = FixedRandom(listOf(0.5f, 0.0f)), // hit roll, then AP variance roll
        )
        assertIs<ImpactOutcome.Deflect>(result)
    }

    // --- Penetrate test ---

    @Test
    fun penetrate_returnsCorrectDamage() {
        // random=0.5 + toHit=0.3 = 0.8 >= evasion=0.2 → hit
        // Head-on: cos(angle) ≈ 1.0 >= hardness(3)*0.05=0.15 → no ricochet
        // AP=10 + random(0.5)*0.2 = 10.1 >= hardness=3 → penetrate
        val result = KineticImpactResolver.resolve(
            projectile = defaultProjectile(
                damage = 25f,
                armourPiercing = 10f,
                toHitModifier = 0.3f,
            ),
            velocity = headOnVelocity,
            contactNormal = headOnNormal,
            armour = defaultArmour(hardness = 3f),
            combatStats = CombatStats(evasionModifier = 0.2f),
            random = FixedRandom(listOf(0.5f, 0.5f)),
        )
        assertIs<ImpactOutcome.Penetrate>(result)
        assertEquals(25f, result.damage)
        assertEquals(10f, result.armourPiercing)
    }

    // --- Edge case: exactly at evasion threshold ---

    @Test
    fun hitRoll_exactlyEqualsEvasion_hits() {
        // random=0.2 + toHit=0.3 = 0.5 >= evasion=0.5 → hit (not miss)
        val result = KineticImpactResolver.resolve(
            projectile = defaultProjectile(toHitModifier = 0.3f, armourPiercing = 10f),
            velocity = headOnVelocity,
            contactNormal = headOnNormal,
            armour = defaultArmour(hardness = 3f),
            combatStats = CombatStats(evasionModifier = 0.5f),
            random = FixedRandom(listOf(0.2f, 0.5f)),
        )
        // Should not be a miss
        assertIs<ImpactOutcome.Penetrate>(result)
    }
}

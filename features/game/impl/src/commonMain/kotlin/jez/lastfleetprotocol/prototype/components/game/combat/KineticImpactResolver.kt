package jez.lastfleetprotocol.prototype.components.game.combat

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.ArmourStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.CombatStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.ProjectileStats
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Outcome of a kinetic projectile impact against a ship.
 */
sealed interface ImpactOutcome {
    /** Projectile missed entirely — bullet continues. */
    data object Miss : ImpactOutcome

    /** Projectile ricocheted off armour at a shallow angle — bullet destroyed. */
    data object Ricochet : ImpactOutcome

    /** Projectile struck armour but failed to penetrate — bullet destroyed. */
    data object Deflect : ImpactOutcome

    /** Projectile penetrated armour — bullet destroyed, damage applied. */
    data class Penetrate(
        val damage: Float,
        val armourPiercing: Float,
    ) : ImpactOutcome
}

/**
 * Stateless resolver for the kinetic impact chain:
 * hit check → ricochet → penetration → penetrating hit.
 *
 * Accepts a [Random] parameter for testability.
 */
object KineticImpactResolver {

    /** Multiplier for the ricochet threshold: cos(angle) < hardness * RICOCHET_FACTOR → ricochet. */
    const val RICOCHET_FACTOR = 0.05f

    /** Random variance applied to armour-piercing during the penetration check. */
    const val AP_VARIANCE = 0.2f

    /**
     * Resolve the full kinetic impact chain.
     *
     * @param projectile Stats of the incoming projectile.
     * @param velocity Bullet velocity vector in world space.
     * @param contactNormal Surface normal at the point of impact (unit vector pointing outward from the target hull).
     * @param armour Target ship's armour stats.
     * @param combatStats Target ship's combat stats (evasion).
     * @param random Random source — override for deterministic tests.
     */
    fun resolve(
        projectile: ProjectileStats,
        velocity: SceneOffset,
        contactNormal: SceneOffset,
        armour: ArmourStats,
        combatStats: CombatStats,
        random: Random = Random.Default,
    ): ImpactOutcome {
        // 1. Hit check: random + toHitModifier vs evasionModifier
        val hitRoll = random.nextFloat() + projectile.toHitModifier
        if (hitRoll < combatStats.evasionModifier) {
            return ImpactOutcome.Miss
        }

        // 2. Ricochet check: cos(angle of incidence) = abs(dot(normalize(velocity), contactNormal))
        val cosAngle = absDot(normalize(velocity), contactNormal)
        if (cosAngle < armour.hardness * RICOCHET_FACTOR) {
            return ImpactOutcome.Ricochet
        }

        // 3. Penetration check: AP + random variance vs hardness
        val effectiveAP = projectile.armourPiercing + random.nextFloat() * AP_VARIANCE
        if (effectiveAP < armour.hardness) {
            return ImpactOutcome.Deflect
        }

        // 4. Penetrating hit
        return ImpactOutcome.Penetrate(
            damage = projectile.damage,
            armourPiercing = projectile.armourPiercing,
        )
    }

    private fun normalize(v: SceneOffset): SceneOffset {
        val vx = v.x.raw
        val vy = v.y.raw
        val len = sqrt(vx * vx + vy * vy)
        if (len < 1e-8f) return SceneOffset(0f.sceneUnit, 0f.sceneUnit)
        return SceneOffset((vx / len).sceneUnit, (vy / len).sceneUnit)
    }

    private fun absDot(a: SceneOffset, b: SceneOffset): Float {
        return abs(a.x.raw * b.x.raw + a.y.raw * b.y.raw)
    }
}

package jez.lastfleetprotocol.prototype.components.game.ai

import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.normalized
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.Ship

/**
 * Basic combat AI that selects the nearest valid target from the ship's
 * target provider, manoeuvres to weapon range, and keeps turrets tracking.
 *
 * Item C unit 6: orbit-engagement distance is derived from the ship's actual
 * largest turret effective range (which itself is computed from drag-aware
 * projectile physics), not a global hardcode. The [fallbackWeaponRange] is
 * used only for ships with no turrets (e.g., unarmed scenarios) — armed
 * ships orbit at [ORBIT_RANGE_FRACTION] × largest-turret effective range,
 * comfortably inside their own firing range.
 */
class BasicAI(
    private val fallbackWeaponRange: Float = 300f,
    private val retargetIntervalMs: Int = 500,
) : AIModule {
    private var retargetTimerMs: Int = 0
    private var currentTarget: Ship? = null

    override fun update(ship: Ship, deltaMs: Int) {
        val targets = ship.targetProvider()

        retargetTimerMs -= deltaMs

        if (retargetTimerMs <= 0) {
            retargetTimerMs = retargetIntervalMs
            if (ship.shipSystems.hasFireControl()) {
                currentTarget = findNearestValidTarget(ship, targets)
            }
        }

        val target = currentTarget
        if (target != null && target.isValidTarget()) {
            val engagementRange = computeEngagementRange(ship)
            val engagementPosition = calculateEngagementPosition(ship, target, engagementRange)
            ship.moveTo(engagementPosition)
            ship.setTarget(target)
        } else {
            currentTarget = null
            ship.setTarget(null)
        }
    }

    /**
     * Orbit at [ORBIT_RANGE_FRACTION] × the ship's largest turret effective range.
     * Falls back to [fallbackWeaponRange] for ships without turrets.
     * Invariant: orbit distance < effective range (so the AI orbits inside firing range).
     */
    private fun computeEngagementRange(ship: Ship): Float {
        val maxTurretRange = ship.maxTurretEffectiveRangeM()
        return if (maxTurretRange > 0f) {
            maxTurretRange * ORBIT_RANGE_FRACTION
        } else {
            fallbackWeaponRange
        }
    }

    private fun findNearestValidTarget(
        ship: Ship,
        targets: List<Ship>,
    ): Ship? {
        var nearest: Ship? = null
        var nearestDistSq = Float.MAX_VALUE

        for (candidate in targets) {
            if (!candidate.isValidTarget()) continue
            val delta = candidate.body.position - ship.body.position
            val distSq = delta.length().raw.let { it * it }
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq
                nearest = candidate
            }
        }
        return nearest
    }

    private fun calculateEngagementPosition(
        ship: Ship,
        target: Ship,
        engagementRange: Float,
    ): SceneOffset {
        val toTarget = target.body.position - ship.body.position
        val distance = toTarget.length()

        if (distance.raw < 1f) {
            return target.body.position + SceneOffset(
                x = engagementRange.sceneUnit,
                y = 0f.sceneUnit,
            )
        }

        val direction = toTarget.normalized()
        val perpendicular = SceneOffset(
            x = (-direction.y.raw).sceneUnit,
            y = direction.x.raw.sceneUnit,
        )

        val rangeOffset = direction * engagementRange
        val sideOffset = perpendicular * (engagementRange * 0.3f)

        return target.body.position - rangeOffset + sideOffset
    }

    private companion object {
        /**
         * Orbit fraction of the ship's largest turret effective range. Invariant:
         * `ORBIT_RANGE_FRACTION < 1.0f` — the AI orbits inside firing range, so
         * its turrets can actually shoot the target it's pursuing.
         */
        const val ORBIT_RANGE_FRACTION = 0.8f
    }
}

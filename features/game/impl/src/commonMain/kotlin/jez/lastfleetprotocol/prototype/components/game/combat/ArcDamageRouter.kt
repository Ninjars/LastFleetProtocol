package jez.lastfleetprotocol.prototype.components.game.combat

import com.pandulapeter.kubriko.helpers.extensions.cos
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.helpers.extensions.sin
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemType
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

/**
 * Stateless utility that routes penetrating kinetic damage to internal systems
 * based on the arc (direction) from which the hit arrived.
 *
 * Arc layout (relative to ship forward = +X in atan2/Kubriko convention):
 * - Forward 90 arc (within 45 of forward) -> BRIDGE
 * - Rear 90 arc (within 45 of rear) -> MAIN_ENGINE
 * - Side arcs (everything else) -> KEEL
 *
 * Slice B promoted KEEL to the side-arc primary (demoting REACTOR to side-arc
 * overflow). Polygon-based spatial targeting of internal systems (including hitting
 * the Keel's polygon specifically) is deferred to a later damage-model slice.
 * Overflow cascading still uses the existing `shuffled(random)` mechanism — KEEL
 * joins the pool for forward/rear arcs automatically via [InternalSystemType.entries].
 */
object ArcDamageRouter {

    /** Half-width of the forward and rear arcs, in radians (45 degrees). */
    private const val ARC_HALF_WIDTH = (PI / 4.0).toFloat()

    /**
     * Route penetrating damage to internal systems based on impact direction.
     *
     * @param impactWorld Impact point in world coordinates.
     * @param shipPosition Ship centre in world coordinates.
     * @param shipRotation Ship facing rotation (radians). Ship forward is +X in local space (atan2 convention).
     * @param shipSystems The ship's internal systems to receive damage.
     * @param damage Total penetrating damage.
     * @param armourPiercing Armour-piercing value for damage absorption calculation.
     * @param random Random source for shuffling secondary targets.
     */
    fun routeDamage(
        impactWorld: SceneOffset,
        shipPosition: SceneOffset,
        shipRotation: AngleRadians,
        shipSystems: ShipSystems,
        damage: Float,
        armourPiercing: Float,
        random: Random = Random.Default,
    ) {
        val primaryType = determineArc(impactWorld, shipPosition, shipRotation)

        // Apply to primary system
        val absorbed = shipSystems.applyDamage(primaryType, damage, armourPiercing)
        var remaining = damage - absorbed

        if (remaining <= 0f) return

        // Over-penetration: route remaining damage to the other two systems in random order
        val secondaryTypes = InternalSystemType.entries
            .filter { it != primaryType }
            .shuffled(random)

        for (secondaryType in secondaryTypes) {
            if (remaining <= 0f) break
            val secondaryAbsorbed = shipSystems.applyDamage(secondaryType, remaining, armourPiercing)
            remaining -= secondaryAbsorbed
        }
    }

    /**
     * Determine which arc the impact hit based on the direction from ship centre
     * to the impact point, relative to the ship's facing.
     *
     * @return The [InternalSystemType] corresponding to the hit arc.
     */
    internal fun determineArc(
        impactWorld: SceneOffset,
        shipPosition: SceneOffset,
        shipRotation: AngleRadians,
    ): InternalSystemType {
        // Convert impact point to ship-local coordinates
        val dx = impactWorld.x.raw - shipPosition.x.raw
        val dy = impactWorld.y.raw - shipPosition.y.raw

        // Rotate by -shipRotation to get direction in ship-local space
        val cosR = shipRotation.cos
        val sinR = shipRotation.sin
        // Inverse rotation (rotate by -angle): x' = x*cos + y*sin, y' = -x*sin + y*cos
        val localX = dx * cosR + dy * sinR
        val localY = -dx * sinR + dy * cosR

        // Ship forward is +X in local space (atan2/Kubriko convention).
        // atan2(y, x) gives angle from +X axis, which is the forward direction.
        // So atan2(localY, localX) gives 0 when impact is directly ahead.
        val angleFromForward = atan2(localY, localX)
        val absAngle = abs(angleFromForward)

        return when {
            absAngle <= ARC_HALF_WIDTH -> InternalSystemType.BRIDGE         // Forward arc
            absAngle >= (PI.toFloat() - ARC_HALF_WIDTH) -> InternalSystemType.MAIN_ENGINE  // Rear arc
            else -> InternalSystemType.KEEL                                  // Side arcs (Slice B)
        }
    }
}

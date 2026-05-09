package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.angleTowards
import com.pandulapeter.kubriko.helpers.extensions.deg
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.rotateTowards
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.combat.LeadAim
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import jez.lastfleetprotocol.prototype.components.game.data.Gun
import jez.lastfleetprotocol.prototype.components.gamecore.data.GunData
import jez.lastfleetprotocol.prototype.components.gamecore.data.effectiveRangeM

/**
 * Turret size in scene units (isoceles triangle half-dimensions).
 * Length = apex to base centre; width = half the base width.
 */
private const val TURRET_HALF_LENGTH = 16f
private const val TURRET_HALF_WIDTH = 8f

class Turret(
    parent: Parent,
    offsetFromParentPivot: SceneOffset,
    private val gunData: GunData,
    val teamId: String,
    private val rotationSpeed: AngleRadians = 10f.deg.rad,
) : Child(
    parent = parent,
    offsetFromParentPivot = offsetFromParentPivot,
) {
    /**
     * Shooter rig — read for velocity (lead-aim relative motion + bullet
     * inheritance at spawn). Same instance the [Child] base captures privately;
     * we keep our own reference because [Child.parent] isn't exposed.
     */
    private val shooter: Parent = parent

    private lateinit var actorManager: ActorManager

    private var currentRotation: AngleRadians = AngleRadians.Zero

    override val body = BoxBody()

    var target: Targetable? = null

    /**
     * Cached at construction from this turret's `gunData.projectileStats.effectiveRangeM()`.
     * Item C unit 4: turrets only fire when target is within this range; outside,
     * they still rotate-toward-target for visual feedback. `ProjectileStats` is an
     * immutable `@Serializable` data class, so the cache is stable for the turret's
     * lifetime.
     *
     * `internal` — read by `Ship.maxTurretEffectiveRangeM()` (same module) so AI
     * modules can derive their orbit-engagement distance from the ship's actual
     * weapon reach rather than a global hardcode (item C unit 6). External callers
     * should go through `Ship.maxTurretEffectiveRangeM()` rather than reading this
     * directly.
     */
    internal val effectiveRangeM: Float = gunData.projectileStats.effectiveRangeM()

    /**
     * When false, [update] short-circuits: target is dropped, aim angle is cleared,
     * and the gun stops firing. Set by the parent [Ship] synchronously at the
     * lifecycle transition out of `Active` — avoids the ActorManager child-update
     * ordering race on the frame the Keel dies. See Slice B Unit 3 Approach.
     *
     * Turret doesn't self-check parent state (it only sees the generic [Parent] interface);
     * instead, Ship is the authority and pushes the flag down.
     */
    private var firingEnabled: Boolean = true

    fun setFiringEnabled(enabled: Boolean) {
        firingEnabled = enabled
        if (!enabled) {
            target = null
            currentAimPoint = null
            gun.angleToTarget = null
        }
    }

    /**
     * Last lead-aim point computed in [update]. Null when the turret has no
     * valid target. Read by `DebugVisualiser` to draw a cross + line per
     * turret without re-running the lead solver.
     */
    internal var currentAimPoint: SceneOffset? = null
        private set

    private val gun: Gun by lazy {
        Gun(
            turretBody = body,
            muzzleOffset = SceneOffset(TURRET_HALF_LENGTH.sceneUnit, 0f.sceneUnit),
            gunData = gunData,
            teamId = teamId,
            shooterVelocity = { shooter.velocity },
        )
    }

    private val turretPath by lazy {
        Path().apply {
            lineTo(
                body.size.width.raw,
                body.size.height.raw / 2f,
            )
            lineTo(
                0f,
                body.size.height.raw,
            )
            close()
        }
    }

    /** Turret outline colour — light grey, distinct from both team colours. */
    private val turretColor = Color(0.8f, 0.8f, 0.8f, 0.9f)
    private val turretFillColor = Color(0.5f, 0.5f, 0.5f, 0.4f)

    override fun onAdded(kubriko: Kubriko) {
        super.onAdded(kubriko)
        actorManager = kubriko.get()
        body.size = SceneSize(
            (TURRET_HALF_LENGTH * 2).sceneUnit,
            (TURRET_HALF_WIDTH * 2).sceneUnit,
        )
        body.pivot = SceneOffset(0f.sceneUnit, body.size.height / 2f)
    }

    override fun DrawScope.draw() {
        drawPath(turretPath, turretColor, style = Stroke(width = 1.5f))
        drawPath(turretPath, turretFillColor, style = Fill)
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        super.update(deltaTimeInMilliseconds)

        if (!firingEnabled) {
            // Parent ship is drifting or destroyed — do not track, rotate, or fire.
            // We still call super.update above so child-actor position-follow logic runs.
            return
        }

        target?.let {
            if (!it.isValidTarget()) {
                target = null
                currentAimPoint = null
                return@let
            }

            // Lead-aim point accounts for the target's constant-acceleration
            // motion, the shooter's instantaneous motion (bullets inherit it
            // verbatim at spawn), and the projectile's drag-aware time-of-flight.
            // See [LeadAim] for the derivation. Velocity is read instantaneously
            // for both rigs — physics-integrated velocity is already smooth
            // frame-to-frame, so further smoothing only adds lag — but
            // acceleration is smoothed to absorb per-frame AI thrust toggling.
            val aimPoint = LeadAim.computeAimPoint(
                turretPos = body.position,
                shooterVelocity = shooter.velocity,
                targetPos = it.body.position,
                targetVelocity = it.velocity,
                targetAcceleration = it.smoothedAcceleration,
                muzzleSpeed = gunData.projectileStats.speed,
                dragK = gunData.projectileStats.dragK,
            )
            currentAimPoint = aimPoint
            val angleToTarget = body.position.angleTowards(aimPoint)

            val targetRotation = angleToTarget - body.rotation
            currentRotation = currentRotation.rotateTowards(
                targetRotation,
                rotationSpeed / deltaTimeInMilliseconds
            )

            // Per-turret effective-range gate. Measured to the *aim point*, not
            // the target — when ships approach or recede the lead point can
            // sit meaningfully closer or further than the target itself. World
            // distance from turret to aim point equals the bullet's required
            // shooter-frame travel (the shooter-frame translation cancels with
            // the lead-aim correction terms — see [LeadAim]'s derivation), so
            // it's directly comparable to [effectiveRangeM].
            val aimDistance = (aimPoint - body.position).length().raw
            gun.angleToTarget = if (aimDistance <= effectiveRangeM) {
                angleToTarget - body.rotation - currentRotation
            } else {
                null
            }
        }

        if (target == null) {
            currentAimPoint = null
            gun.angleToTarget = null
            currentRotation =
                currentRotation.rotateTowards(0.rad, rotationSpeed / deltaTimeInMilliseconds)
        }

        body.rotation += currentRotation
        gun.update(deltaTimeInMilliseconds, actorManager)
    }

    override val drawingOrder: Float = DrawOrder.PLAYER_TURRET
}

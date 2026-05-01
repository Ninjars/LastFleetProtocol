package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.actor.traits.Visible
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.mask.CircleCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.maxDimension
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.combat.ArcDamageRouter
import jez.lastfleetprotocol.prototype.components.game.combat.ImpactOutcome
import jez.lastfleetprotocol.prototype.components.game.combat.KineticImpactResolver
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import jez.lastfleetprotocol.prototype.components.gamecore.data.ProjectileStats
import jez.lastfleetprotocol.prototype.components.game.managers.AudioManager
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.reflect.KClass

data class BulletData(
    val bulletSize: SceneSize,
)

internal class Bullet(
    initialPosition: SceneOffset,
    initialRotation: AngleRadians,
    initialVelocity: SceneOffset,
    private val bulletData: BulletData,
    val projectileStats: ProjectileStats,
    val teamId: String,
    override val collidableTypes: List<KClass<out Collidable>>,
) : Visible, Dynamic, CollisionDetector {
    override val isAlwaysActive: Boolean = true
    private var remainingLifetimeMs: Int = projectileStats.lifetimeMs

    /**
     * Mutable per-frame velocity. Item C unit 2: decays each frame under drag
     * when [ProjectileStats.dragK] is positive. The constructor parameter
     * [initialVelocity] is the muzzle vector; subsequent frames mutate this var.
     */
    private var velocity: SceneOffset = initialVelocity

    /** Cached muzzle speed for drag math + currentPenetration scaling. */
    private val muzzleSpeed: Float = sqrt(
        initialVelocity.x.raw * initialVelocity.x.raw +
            initialVelocity.y.raw * initialVelocity.y.raw,
    )

    /**
     * Drag-aware penetration value. Item C unit 2: at the muzzle this equals
     * [ProjectileStats.armourPiercing]; decays linearly with `currentSpeed / muzzleSpeed`.
     * Threaded into [KineticImpactResolver.resolve] at hit time so drag-aware
     * penetration reaches the hit path during C (not just E).
     */
    private var currentPenetration: Float = projectileStats.armourPiercing
    override val body = BoxBody(
        initialPosition = initialPosition,
        initialRotation = initialRotation,
        initialSize = bulletData.bulletSize,
    )
    private val radius = bulletData.bulletSize.maxDimension / 2f
    override val collisionMask = CircleCollisionMask(
        initialRadius = radius,
        initialPosition = body.position,
    )
    private lateinit var actorManager: ActorManager
    private lateinit var audioManager: AudioManager
    private lateinit var stateManager: StateManager
    private lateinit var viewportManager: ViewportManager

    override val drawingOrder = DrawOrder.BULLET

    override fun onAdded(kubriko: Kubriko) {
        actorManager = kubriko.get()
        audioManager = kubriko.get()
        stateManager = kubriko.get()
        viewportManager = kubriko.get()
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        if (!stateManager.isRunning.value) return

        val dtSeconds = deltaTimeInMilliseconds * 0.001f

        // Item C unit 2 — apply exponential drag when configured. The exp form is
        // numerically stable at any dt (never produces a negative-velocity sign flip
        // that the naive `velocity *= (1 - k * dt)` form would on slow frames).
        if (projectileStats.dragK > 0f) {
            val decay = exp(-projectileStats.dragK * dtSeconds)
            velocity = SceneOffset(
                (velocity.x.raw * decay).sceneUnit,
                (velocity.y.raw * decay).sceneUnit,
            )
        }

        // Compute current speed once; both penetration scaling and the
        // velocity-threshold expiration check read it.
        val currentSpeed = if (muzzleSpeed > 0f) {
            sqrt(velocity.x.raw * velocity.x.raw + velocity.y.raw * velocity.y.raw)
        } else {
            0f
        }

        // currentPenetration tracks the velocity ratio. At the muzzle this equals
        // projectileStats.armourPiercing; decays linearly with speed.
        currentPenetration = if (muzzleSpeed > 0f) {
            projectileStats.armourPiercing * (currentSpeed / muzzleSpeed)
        } else {
            projectileStats.armourPiercing
        }

        // Velocity-threshold expiration is the *primary* mechanism when drag is
        // configured. Guard on both dragK > 0 AND expirationVelocityFraction > 0
        // — if dragK is zero, velocity never decays and the threshold check would
        // never fire anyway, but the explicit guard prevents a config that has
        // expirationVelocityFraction > 0 with dragK = 0 from being silently broken.
        if (projectileStats.dragK > 0f &&
            projectileStats.expirationVelocityFraction > 0f &&
            currentSpeed < projectileStats.expirationVelocityFraction * muzzleSpeed
        ) {
            actorManager.remove(this)
            return
        }

        // lifetimeMs as the *secondary* safety cap — fires for legacy projectiles
        // (dragK = 0) or as a defensive ceiling for drag-aware ones that never quite
        // reach the velocity threshold.
        remainingLifetimeMs -= deltaTimeInMilliseconds
        if (remainingLifetimeMs <= 0) {
            actorManager.remove(this)
            return
        }

        body.position += velocity * 0.001f * deltaTimeInMilliseconds
        collisionMask.position = body.position
    }

    override fun onCollisionDetected(collidables: List<Collidable>) {
        for (collidable in collidables) {
            val hullCollider = collidable as? HullCollider ?: continue
            val ship = hullCollider.parentShip
            if (ship.teamId == teamId) continue // Skip friendly ships
            if (!ship.isValidTarget()) continue

            // Approximate contact normal: direction from bullet to ship centre, inverted
            // (pointing outward from ship hull toward bullet).
            val dx = body.position.x.raw - ship.body.position.x.raw
            val dy = body.position.y.raw - ship.body.position.y.raw
            val dist = sqrt(dx * dx + dy * dy)
            val contactNormal = if (dist > 1e-6f) {
                SceneOffset((dx / dist).sceneUnit, (dy / dist).sceneUnit)
            } else {
                SceneOffset(0f.sceneUnit, (-1f).sceneUnit)
            }

            val contactPoint = body.position

            // Use the struck hull's armour for impact resolution. Pass the
            // drag-decayed `currentPenetration` (item C unit 2) so the resolver's
            // penetration check + Penetrate.armourPiercing reflect velocity-at-impact,
            // not the muzzle value.
            val outcome = KineticImpactResolver.resolve(
                projectile = projectileStats,
                currentPenetration = currentPenetration,
                velocity = velocity,
                contactNormal = contactNormal,
                armour = hullCollider.hullDefinition.armour,
                combatStats = ship.spec.combatStats,
            )

            when (outcome) {
                is ImpactOutcome.Miss -> {
                    // Bullet continues — check remaining targets
                    continue
                }

                is ImpactOutcome.Ricochet,
                is ImpactOutcome.Deflect -> {
                    actorManager.remove(this)
                    return
                }

                is ImpactOutcome.Penetrate -> {
                    ArcDamageRouter.routeDamage(
                        impactWorld = contactPoint,
                        shipPosition = ship.body.position,
                        shipRotation = ship.body.rotation,
                        shipSystems = ship.shipSystems,
                        damage = outcome.damage,
                        armourPiercing = outcome.armourPiercing,
                    )
                    actorManager.remove(this)
                    return
                }
            }
        }
    }

    override fun DrawScope.draw() {
        drawCircle(
            color = Color.Yellow,
            radius = radius.raw,
            center = Offset(radius.raw, radius.raw),
        )
    }
}
package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.ai.AIModule
import jez.lastfleetprotocol.prototype.components.game.navigation.ShipNavigator
import jez.lastfleetprotocol.prototype.components.game.physics.ShipPhysics
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import jez.lastfleetprotocol.prototype.components.gamecore.data.TurretConfig
import kotlin.math.max
import kotlin.math.min

/**
 * A ship in the game world. Can be player-controlled or AI-controlled depending
 * on the [aiModules] provided. Team membership is identified by [teamId], which
 * is propagated to projectiles to prevent friendly fire.
 *
 * Collision detection is handled by [HullCollider] child actors — one per hull
 * piece. Ship itself does not participate in collision; bullets hit hull colliders
 * and route damage back through [shipSystems].
 *
 * Rendered as polygon outlines (hull silhouette + nose marker), not sprites.
 */
class Ship(
    internal val spec: ShipSpec,
    initialPosition: SceneOffset,
    val teamId: String,
    val targetProvider: () -> List<Ship> = { emptyList() },
    private val aiModules: List<AIModule> = emptyList(),
    initialVelocity: SceneOffset = SceneOffset.Zero,
    private val turretsConfig: List<TurretConfig> = emptyList(),
    val shipSystems: ShipSystems = ShipSystems(emptyList()),
    override val drawingOrder: Float = 0f,
) : Targetable, Dynamic, Parent {

    /**
     * Three-state lifecycle replacing the Slice A `isDestroyed` flag.
     * See [ShipLifecycle] for transitions and [updateLifecycle] for the state machine.
     */
    var lifecycle: ShipLifecycle = ShipLifecycle.Active
        private set

    /**
     * Fires at *every* lifecycle transition. Subscribed by `GameStateManager` to
     * re-tally match results via `none { is Active }` — so victory fires the instant
     * a Keel is destroyed (entry into `LiftFailed`), even though the ship's actor
     * remains in the scene during drift. See Slice B Key Decision 5.
     */
    var onLifecycleTransition: ((Ship, ShipLifecycle) -> Unit)? = null

    /**
     * Fires *only* at the terminal transition into [ShipLifecycle.Destroyed].
     * Handles actor cleanup and the cause-tagged `println` destruction log.
     */
    var onDestroyedCallback: ((Ship, DestructionCause) -> Unit)? = null

    private lateinit var viewportManager: ViewportManager
    private lateinit var actorManager: ActorManager

    /** Hull colliders — one per hull piece. Created at construction, added as child actors. */
    internal val hullColliders: List<HullCollider> = spec.hulls.map { hull ->
        HullCollider(
            parentShip = this,
            hullDefinition = hull,
            initialPosition = initialPosition,
        )
    }

    /** Heading indicator — small triangle at the forward-most hull vertex. */
    private val headingIndicator: HeadingIndicator = HeadingIndicator(
        parent = this,
        offsetFromParentPivot = computeNoseOffset(),
    )

    private val turrets = turretsConfig.map { tc ->
        Turret(
            parent = this,
            offsetFromParentPivot = SceneOffset(Offset(tc.offsetX, tc.offsetY)),
            gunData = tc.gunData,
            teamId = teamId,
        )
    }

    override val actors: List<Actor> = hullColliders + turrets + headingIndicator

    override var velocity: SceneOffset
        get() = physics.velocity
        set(value) {
            physics.velocity = value
        }

    /** Current movement destination, exposed for debug visualisation. */
    var destination: SceneOffset? = null
        private set

    internal val physics: ShipPhysics = ShipPhysics(
        mass = spec.totalMass,
        initialVelocity = initialVelocity,
    )

    private val navigator: ShipNavigator = ShipNavigator(
        hullRadius = computeHullRadius(),
    )

    override val body: BoxBody = BoxBody(
        initialPosition = initialPosition,
    )

    override val isAlwaysActive: Boolean = true

    /** Team colour for hull fill — cyan-family for player, red-family for enemy. */
    private val teamFillColor: Color
        get() = if (teamId == TEAM_PLAYER) {
            Color(0f, 0.7f, 0.8f, 0.25f) // translucent cyan
        } else {
            Color(0.9f, 0.2f, 0.1f, 0.25f) // translucent red
        }

    private val teamStrokeColor: Color
        get() = if (teamId == TEAM_PLAYER) {
            Color(0f, 0.9f, 1f, 0.9f) // bright cyan
        } else {
            Color(1f, 0.3f, 0.2f, 0.9f) // bright red
        }

    /**
     * Pre-computed hull vertices in local space (relative to body pivot).
     * Each hull is a list of (x, y) pairs for drawing.
     * For single-hull ships (all current defaults) this is one entry.
     */
    private val hullLocalVertices: List<List<Offset>> by lazy {
        val centreOffset = hullBoundsSize.center.raw

        spec.hulls.map { hull ->
            hull.vertices.map { Offset(it.x.raw + centreOffset.x, it.y.raw + centreOffset.y) }
        }
    }

    /** Pre-computed AABB size from all hull vertices, used for body.size */
    private val hullBoundsSize: SceneSize by lazy {
        println("hullBoundsSize")
        val allVerts = spec.hulls.flatMap { hull ->
            hull.vertices.map { Offset(it.x.raw, it.y.raw) }
        }
        if (allVerts.isEmpty()) return@lazy SceneSize(10.sceneUnit, 10.sceneUnit)
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (v in allVerts) {
            minX = min(minX, v.x)
            maxX = max(maxX, v.x)
            minY = min(minY, v.y)
            maxY = max(maxY, v.y)
        }
        SceneSize((maxX - minX).sceneUnit, (maxY - minY).sceneUnit)
    }

    override fun DrawScope.draw() {
        // Minimum-viable drift visual: desaturate (halve alpha on both stroke and fill)
        // while the ship is drifting under `LiftFailed`. Satisfies the brainstorm's
        // "visibly drift before despawning" success criterion without particle effects.
        // Richer drift visuals (smoke trail, heading fade, turret slouch) stay deferred.
        val alphaScale = if (lifecycle is ShipLifecycle.LiftFailed) 0.5f else 1f
        val fillColor = teamFillColor.copy(alpha = teamFillColor.alpha * alphaScale)
        val strokeColor = teamStrokeColor.copy(alpha = teamStrokeColor.alpha * alphaScale)

        for (verts in hullLocalVertices) {
            if (verts.size < 3) continue

            val path = Path()
            for (i in verts.indices) {
                if (i == 0) path.moveTo(verts[i].x, verts[i].y)
                else path.lineTo(verts[i].x, verts[i].y)
            }
            path.close()

            drawPath(path, fillColor, style = Fill)
            drawPath(path, strokeColor, style = Stroke(width = 2f))
        }
        // Heading indicator (nose marker) is a separate child actor — see HeadingIndicator
    }

    override fun onAdded(kubriko: Kubriko) {
        viewportManager = kubriko.get()
        actorManager = kubriko.get()
        body.size = hullBoundsSize
        body.pivot = body.size.center
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        when (val lc = lifecycle) {
            is ShipLifecycle.Active -> {
                // Full control path: AI, navigation, thrust, drag, integration.
                for (ai in aiModules) {
                    ai.update(this, deltaTimeInMilliseconds)
                }
                destination = navigator.navigate(
                    movementConfig = spec.movementConfig,
                    physics = physics,
                    body = body,
                    combatTarget = combatTarget,
                    destination = destination,
                    deltaMs = deltaTimeInMilliseconds,
                )
                val result = physics.integrate(deltaTimeInMilliseconds)
                body.position += result.positionDelta
                // Rotation handled by navigator via turn-rate model (not physics-integrated)
            }

            is ShipLifecycle.LiftFailed -> {
                // Drift path: no AI, no navigation, no thrust, no turret updates. Drag
                // keeps running so the ship decelerates naturally. Countdown inside
                // updateLifecycle.
                physics.applyDrag(spec.movementConfig, body.rotation)
                val result = physics.integrate(deltaTimeInMilliseconds)
                body.position += result.positionDelta
            }

            is ShipLifecycle.Destroyed -> Unit // Actor is being / has been removed.
        }

        updateLifecycle(deltaTimeInMilliseconds)
    }

    override fun isValidTarget(): Boolean = lifecycle is ShipLifecycle.Active

    /**
     * Resolve the next lifecycle state, fire transition hooks, and tick the drift
     * countdown. Called once per frame at the end of [update].
     *
     * Transition order (reactor-priority):
     * 1. `Active` + reactor destroyed → `Destroyed(HULL)` directly (skips LiftFailed).
     * 2. `Active` + keel destroyed    → `LiftFailed(DRIFT_WINDOW_MS)`.
     * 3. `LiftFailed`                 → decrement `remainingMs`; when ≤ 0 → `Destroyed(LIFT_FAILURE)`.
     *
     * A same-frame reactor+keel double-kill lands on `Destroyed(HULL)` — no drift.
     * See Slice B plan Unit 3 Approach + Risks.
     */
    fun updateLifecycle(deltaTimeInMilliseconds: Int) {
        val next: ShipLifecycle? = when (val current = lifecycle) {
            is ShipLifecycle.Active -> when {
                shipSystems.isReactorDestroyed() -> ShipLifecycle.Destroyed(DestructionCause.HULL)
                shipSystems.isKeelDestroyed() -> ShipLifecycle.LiftFailed(DRIFT_WINDOW_MS)
                else -> null
            }

            is ShipLifecycle.LiftFailed -> {
                val remaining = current.remainingMs - deltaTimeInMilliseconds
                if (remaining <= 0) ShipLifecycle.Destroyed(DestructionCause.LIFT_FAILURE)
                else ShipLifecycle.LiftFailed(remaining)
            }

            is ShipLifecycle.Destroyed -> null
        }

        if (next == null || next == lifecycle) return

        val previous = lifecycle
        lifecycle = next

        // Entering a non-Active state disables turret firing synchronously. This
        // sidesteps the Kubriko ActorManager child-update-ordering race — a turret
        // update scheduled for the same frame as the parent's transition won't see
        // the stale `Active` state.
        if (previous is ShipLifecycle.Active && next !is ShipLifecycle.Active) {
            for (turret in turrets) turret.setFiringEnabled(false)
        }

        onLifecycleTransition?.invoke(this, next)

        if (next is ShipLifecycle.Destroyed) {
            onDestroyedCallback?.invoke(this, next.cause)
            // `actorManager` is set in onAdded; unit tests drive the lifecycle
            // directly without a Kubriko harness, so guard the side-effect.
            if (::actorManager.isInitialized) actorManager.remove(this)
        }
    }

    /** The ship's current combat target, used for facing between manoeuvres. */
    var combatTarget: Targetable? = null
        private set

    fun setTarget(mobile: Targetable?) {
        combatTarget = mobile
        for (turret in turrets) {
            turret.target = mobile
        }
    }

    fun moveTo(destination: SceneOffset) {
        this.destination = destination
    }

    /** Test whether a scene-space point is inside any of this ship's hull polygons. */
    fun isPointInHull(point: SceneOffset): Boolean =
        hullColliders.any { it.collisionMask.isSceneOffsetInside(point) }

    /**
     * Find the forward-most hull vertex (+X in ship-local space) and return it
     * as a SceneOffset from the ship's pivot. Used to position the [HeadingIndicator].
     */
    private fun computeNoseOffset(): SceneOffset {
        val allVertices = spec.hulls.flatMap { it.vertices }
        return if (allVertices.isNotEmpty()) {
            allVertices.maxBy { it.x.raw }
        } else {
            SceneOffset.Zero
        }
    }

    /**
     * Compute the average vertex distance across all hulls, used as a
     * bounding radius for the navigator's arrival/braking calculations.
     */
    private fun computeHullRadius(): Float {
        val allVertices = spec.hulls.flatMap { it.vertices }
        return if (allVertices.isNotEmpty()) {
            allVertices.map { it.length().raw }.average().toFloat()
        } else {
            5f // fallback matching ARRIVAL_THRESHOLD
        }
    }

    companion object {
        const val TEAM_PLAYER = "player"
        const val TEAM_ENEMY = "enemy"

        /**
         * Drift window after Keel destruction before the ship is removed from the scene.
         * Tuneable in playtest — starting value per Slice B plan's Deferred to Implementation.
         */
        const val DRIFT_WINDOW_MS = 3000
    }
}

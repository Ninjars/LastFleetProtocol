package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder

/**
 * A small forward-pointing triangle drawn at the ship's nose vertex to indicate
 * heading direction. Implemented as a [Child] actor so it can extend beyond
 * the parent ship's body bounding box (Kubriko clips drawing to body.size).
 *
 * Positioned at the forward-most hull vertex via [offsetFromParentPivot].
 * Inherits the parent ship's rotation automatically through [Child].
 */
class HeadingIndicator(
    parent: Parent,
    offsetFromParentPivot: SceneOffset,
) : Child(
    parent = parent,
    offsetFromParentPivot = offsetFromParentPivot,
) {
    override val body = BoxBody()

    override fun onAdded(kubriko: Kubriko) {
        super.onAdded(kubriko)
        body.size = SceneSize(LENGTH.sceneUnit, WIDTH.sceneUnit)
        // Pivot at the left-centre of the body: this is the "attachment point"
        // where the indicator connects to the ship's nose. The triangle extends
        // forward (rightward in body-local space) from this point.
        body.pivot = SceneOffset(0f.sceneUnit, (WIDTH / 2f).sceneUnit)
    }

    override fun DrawScope.draw() {
        // Isoceles triangle pointing right (+X = forward in ship convention).
        // Body-local coordinates: (0,0) is top-left, +X right, +Y down.
        val path = Path()
        path.moveTo(LENGTH, WIDTH / 2f)   // tip: right-centre (forward)
        path.lineTo(0f, 0f)               // base top-left
        path.lineTo(0f, WIDTH)            // base bottom-left
        path.close()

        drawPath(path, Color.White, style = Stroke(width = 2f))
    }

    // Draw on top of the ship hull
    override val drawingOrder: Float = DrawOrder.PLAYER_SHIP - 1f

    companion object {
        private const val LENGTH = 14f
        private const val WIDTH = 10f
    }
}

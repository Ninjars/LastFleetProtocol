package jez.lastfleetprotocol.prototype.components.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.pointerInput.PointerInputManager

/**
 * Debug overlay rendered on top of the game viewport. Item C unit 8 — gated on
 * `state.canShowDebugOverlay` (DevToolsGate.isAvailable). Renders three
 * components:
 *
 * 1. **Distance rings** at 1 km, 3 km, and 5 km centred on the camera-view
 *    centre. Helps the dev visually verify weapon-range tuning during
 *    empirical playtest sessions.
 * 2. **Mouse-cursor world position** in metres (e.g. `(1234, -567) m`),
 *    sourced from `pointerInputManager.hoveringPointerPosition`.
 * 3. **FPS counter** averaged over the last [FPS_SAMPLE_WINDOW] frames.
 *
 * **Pointer input.** The overlay does **not** install its own
 * `Modifier.pointerInput`. Doing so on a fillMaxSize sibling of
 * `KubrikoViewport` intercepts pointer events at the modifier level — even
 * when the inner `awaitPointerEventScope` does not call `consume()`,
 * KubrikoViewport's pan / zoom never fires. Instead, this composable
 * subscribes to `PointerInputManager.hoveringPointerPosition`, which Kubriko
 * already tracks internally for all input-aware actors. The mouse position
 * displayed here is the same position the engine sees — no competing
 * gesture detector at the Compose layer, no event interception.
 *
 * Reads three state flows: `viewportManager.cameraPosition`,
 * `viewportManager.scaleFactor`, and `pointerInputManager.hoveringPointerPosition`.
 * All are read via `collectAsStateWithLifecycle` — recomposition-safe; no
 * writes to engine state from inside the recompose.
 */
@Composable
internal fun DebugOverlay(
    viewportManager: ViewportManager,
    pointerInputManager: PointerInputManager,
    modifier: Modifier = Modifier,
) {
    val cameraPosition by viewportManager.cameraPosition.collectAsStateWithLifecycle()
    val scaleFactor by viewportManager.scaleFactor.collectAsStateWithLifecycle()
    val viewportSize by viewportManager.size.collectAsStateWithLifecycle()
    val hoveringPointerPosition by pointerInputManager.hoveringPointerPosition.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        // Distance rings — radii expressed in metres, drawn in screen px via
        // scaleFactor. Centred at the canvas centre (which is the camera
        // viewport centre in screen coords).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centreX = size.width / 2f
            val centreY = size.height / 2f
            val pxPerMetre = scaleFactor.horizontal
            for (rangeM in DISTANCE_RING_METRES) {
                val radiusPx = rangeM * pxPerMetre
                if (radiusPx > 0f && radiusPx < maxOf(size.width, size.height) * 2f) {
                    drawCircle(
                        color = RING_COLOR,
                        radius = radiusPx,
                        center = Offset(centreX, centreY),
                        style = Stroke(width = 1.5f),
                    )
                }
            }
        }

        // Mouse world-position readout — bottom-left corner. Sourced from
        // Kubriko's already-tracked hovering pointer state; no competing
        // pointerInput modifier here.
        val mouseWorld = hoveringPointerPosition?.let { px ->
            screenToWorld(
                screenPx = px,
                viewportSize = viewportSize,
                cameraPosition = Offset(cameraPosition.x.raw, cameraPosition.y.raw),
                scaleFactor = scaleFactor.horizontal,
            )
        }
        Text(
            text = mouseWorld?.let { "(%.0f, %.0f) m".format(it.x, it.y) } ?: "—",
            color = OVERLAY_TEXT_COLOR,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        )

        // FPS counter — top-right corner.
        FpsCounter(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
    }
}

/**
 * Rolling-average FPS counter. Updates every frame via `withFrameMillis`.
 * Reset between displayed frames — small CPU footprint, dev-only.
 */
@Composable
private fun FpsCounter(modifier: Modifier = Modifier) {
    var fps by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastFrameTimeMs = withFrameMillis { it }
        val deltas = ArrayDeque<Long>(FPS_SAMPLE_WINDOW)
        while (true) {
            val now = withFrameMillis { it }
            val delta = now - lastFrameTimeMs
            lastFrameTimeMs = now
            if (delta > 0) {
                deltas.addLast(delta)
                if (deltas.size > FPS_SAMPLE_WINDOW) deltas.removeFirst()
                val avg = deltas.average().toFloat()
                fps = if (avg > 0f) 1000f / avg else 0f
            }
        }
    }
    Text(
        text = "%.1f fps".format(fps),
        color = OVERLAY_TEXT_COLOR,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

/**
 * Convert a screen-pixel position to world coordinates given the current
 * camera state. Inverse of `world = camera + (screen - viewportCentre) / scale`.
 */
private fun screenToWorld(
    screenPx: Offset,
    viewportSize: Size,
    cameraPosition: Offset,
    scaleFactor: Float,
): Offset {
    if (scaleFactor <= 0f) return cameraPosition
    val viewportCentre = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    val deltaPx = screenPx - viewportCentre
    return cameraPosition + Offset(deltaPx.x / scaleFactor, deltaPx.y / scaleFactor)
}

private val RING_COLOR = Color(0.5f, 0.9f, 0.5f, 0.5f) // semi-transparent green
private val OVERLAY_TEXT_COLOR = Color(0.9f, 0.9f, 0.9f, 0.85f)
private val DISTANCE_RING_METRES = listOf(1000f, 3000f, 5000f)
private const val FPS_SAMPLE_WINDOW = 30

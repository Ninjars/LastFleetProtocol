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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.kubriko.manager.ViewportManager

/**
 * Debug overlay rendered on top of the game viewport. Item C unit 8 — gated on
 * `state.canShowDebugOverlay` (DevToolsGate.isAvailable). Renders three
 * components:
 *
 * 1. **Distance rings** at 1 km, 3 km, and 5 km centred on the camera-view
 *    centre. Helps the dev visually verify weapon-range tuning during
 *    empirical playtest sessions.
 * 2. **Mouse-cursor world position** in metres (e.g. `(1234, -567) m`),
 *    captured via `pointerInput` *without* calling `consume()` so events still
 *    propagate to `KubrikoViewport`'s pan/zoom handler.
 * 3. **FPS counter** averaged over the last [FPS_SAMPLE_WINDOW] frames.
 *
 * Reads `viewportManager.cameraPosition` and `viewportManager.scaleFactor` via
 * `collectAsStateWithLifecycle` — recomposition-safe; no writes to viewport
 * state from inside the recompose. The mouse-position `pointerInput` does
 * NOT consume events, so the underlying `KubrikoViewport` continues to
 * receive pan + zoom gestures unchanged.
 */
@Composable
internal fun DebugOverlay(
    viewportManager: ViewportManager,
    modifier: Modifier = Modifier,
) {
    val cameraPosition by viewportManager.cameraPosition.collectAsStateWithLifecycle()
    val scaleFactor by viewportManager.scaleFactor.collectAsStateWithLifecycle()
    val viewportSize by viewportManager.size.collectAsStateWithLifecycle()

    // Mouse position captured without consuming the event — KubrikoViewport
    // still receives pan/zoom gestures.
    var mouseScreenPx by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // Track move/enter; explicitly do NOT call
                        // event.changes.forEach { it.consume() } — events
                        // propagate to children (KubrikoViewport) unchanged.
                        if (event.type == PointerEventType.Move ||
                            event.type == PointerEventType.Enter ||
                            event.type == PointerEventType.Press
                        ) {
                            mouseScreenPx = event.changes.firstOrNull()?.position
                        } else if (event.type == PointerEventType.Exit) {
                            mouseScreenPx = null
                        }
                    }
                }
            },
    ) {
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

        // Mouse world-position readout — bottom-left corner.
        val mouseWorld = mouseScreenPx?.let { px ->
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
    viewportSize: androidx.compose.ui.geometry.Size,
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

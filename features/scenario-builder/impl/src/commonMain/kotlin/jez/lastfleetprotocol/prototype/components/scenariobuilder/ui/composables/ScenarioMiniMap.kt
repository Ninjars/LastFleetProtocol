package jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioBuilderState
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioTeam
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.SlotEntry
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import org.jetbrains.compose.resources.stringResource

/**
 * Read-only spatial preview of the scenario's slot positions. World bounds
 * are mapped from `±WORLD_BOUNDS` SceneUnits to the canvas; broken slots
 * are excluded so the dev sees what will actually launch.
 *
 * No `Modifier.pointerInput` — input is forbidden by R1. The mini-map
 * subscribes to the VM's StateFlow via the [state] argument so positions
 * update live as x/y inputs change.
 */
@Composable
fun ScenarioMiniMap(
    state: ScenarioBuilderState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(8.dp)) {
        val playerColor = MaterialTheme.colorScheme.primary
        val enemyColor = MaterialTheme.colorScheme.error
        val boundsColor = MaterialTheme.colorScheme.outline

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))

            // Dashed world bounds rectangle.
            drawRect(
                color = boundsColor,
                topLeft = Offset.Zero,
                size = size,
                style = Stroke(width = 2f, pathEffect = dashEffect),
            )

            val livingSlots = state.slots.filterNot { it.id in state.brokenSlotIds }
            for (slot in livingSlots) {
                val (cx, cy) = slot.toCanvasPosition(w, h)
                drawCircle(
                    color = if (slot.team == ScenarioTeam.PLAYER) playerColor else enemyColor,
                    radius = MARKER_RADIUS_PX,
                    center = Offset(cx, cy),
                )
            }
        }

        if (state.slots.isEmpty()) {
            Text(
                text = stringResource(LFRes.String.scenario_minimap_empty),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun SlotEntry.toCanvasPosition(width: Float, height: Float): Pair<Float, Float> {
    // World coords: x in [-WORLD_BOUNDS, +WORLD_BOUNDS], y in [-WORLD_BOUNDS, +WORLD_BOUNDS].
    // Canvas coords: x in [0, width], y in [0, height] (Y grows downward, as per Compose).
    val nx = ((x + WORLD_BOUNDS) / (2 * WORLD_BOUNDS)).coerceIn(0f, 1f)
    val ny = ((y + WORLD_BOUNDS) / (2 * WORLD_BOUNDS)).coerceIn(0f, 1f)
    return nx * width to ny * height
}

private const val WORLD_BOUNDS = 500f
private const val MARKER_RADIUS_PX = 8f

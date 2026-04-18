package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedKeel
import jez.lastfleetprotocol.prototype.components.gamecore.stats.ShipStats
import jez.lastfleetprotocol.prototype.ui.common.composables.LFTextButton
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import org.jetbrains.compose.resources.stringResource

/**
 * Three-state flightworthiness display derived from `(placedKeel, stats)`.
 * Surfaces the reason for unflightworthiness so users know what to fix.
 * `internal` (not `private`) so the derivation logic is unit-testable without
 * requiring a Compose test harness for the whole panel. See Slice B Unit 6.
 */
internal sealed interface FlightworthinessDisplay {
    data object NoKeel : FlightworthinessDisplay
    data object Flightworthy : FlightworthinessDisplay
    data class MassExceedsLift(val mass: Float, val lift: Float) : FlightworthinessDisplay
}

internal fun flightworthinessDisplay(
    placedKeel: PlacedKeel?,
    stats: ShipStats,
): FlightworthinessDisplay = when {
    placedKeel == null -> FlightworthinessDisplay.NoKeel
    stats.isFlightworthy -> FlightworthinessDisplay.Flightworthy
    else -> FlightworthinessDisplay.MassExceedsLift(stats.totalMass, stats.totalLift)
}

@Composable
fun StatsPanel(
    stats: ShipStats,
    placedKeel: PlacedKeel?,
    designName: String,
    onNameChanged: (String) -> Unit,
    onLoadClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FlightworthinessIndicator(
            display = flightworthinessDisplay(placedKeel, stats),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )

        Text(
            text = stringResource(LFRes.String.builder_stats),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )

        Column {
            OutlinedTextField(
                value = designName,
                onValueChange = onNameChanged,
                label = { Text(stringResource(LFRes.String.builder_design_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Mass + Lift — Mass is tinted when it exceeds Lift, matching the
            // indicator state. Lift row only shows when a Keel is placed.
            val massOverLift = placedKeel != null && stats.totalMass > stats.totalLift
            StatRow(
                label = stringResource(LFRes.String.builder_mass),
                value = "%.1f".format(stats.totalMass),
                valueColor = if (massOverLift) MaterialTheme.colorScheme.error else null,
            )
            if (placedKeel != null) {
                StatRow(
                    label = stringResource(LFRes.String.builder_lift),
                    value = "%.1f".format(stats.totalLift),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(LFRes.String.builder_thrust),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatRow(
                stringResource(LFRes.String.builder_forward),
                "%.1f".format(stats.forwardThrust)
            )
            StatRow(
                stringResource(LFRes.String.builder_lateral),
                "%.1f".format(stats.lateralThrust)
            )
            StatRow(
                stringResource(LFRes.String.builder_reverse),
                "%.1f".format(stats.reverseThrust)
            )
            StatRow(
                stringResource(LFRes.String.builder_angular),
                "%.1f".format(stats.angularThrust)
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(LFRes.String.builder_terminal_velocity),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val unlimited = stringResource(LFRes.String.builder_unlimited)
            StatRow(stringResource(LFRes.String.builder_forward), formatTerminalVel(stats.terminalVelForward, unlimited))
            StatRow(stringResource(LFRes.String.builder_lateral), formatTerminalVel(stats.terminalVelLateral, unlimited))
            StatRow(stringResource(LFRes.String.builder_reverse), formatTerminalVel(stats.terminalVelReverse, unlimited))
            StatRow(stringResource(LFRes.String.builder_turn_rate), "%.1f".format(stats.turnRate))

            Spacer(modifier = Modifier.height(12.dp))

            LFTextButton(
                text = stringResource(LFRes.String.builder_load_design),
                onClick = onLoadClicked,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatTerminalVel(value: Float, unlimitedLabel: String): String =
    if (value >= Float.MAX_VALUE / 2f) unlimitedLabel else "%.1f".format(value)

@Composable
private fun StatRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (valueColor != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = valueColor,
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FlightworthinessIndicator(
    display: FlightworthinessDisplay,
    modifier: Modifier = Modifier,
) {
    val (bg, fg, text) = when (display) {
        FlightworthinessDisplay.Flightworthy -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            stringResource(LFRes.String.builder_flightworthy),
        )
        FlightworthinessDisplay.NoKeel -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            stringResource(LFRes.String.builder_no_keel),
        )
        is FlightworthinessDisplay.MassExceedsLift -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            stringResource(LFRes.String.builder_mass_exceeds_lift) +
                " (%.0f / %.0f)".format(display.mass, display.lift),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

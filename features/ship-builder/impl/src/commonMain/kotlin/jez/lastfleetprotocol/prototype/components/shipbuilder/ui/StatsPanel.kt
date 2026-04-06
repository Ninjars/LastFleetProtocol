package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jez.lastfleetprotocol.prototype.components.shipbuilder.stats.ShipStats
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import org.jetbrains.compose.resources.stringResource

@Composable
fun StatsPanel(
    stats: ShipStats,
    designName: String,
    onNameChanged: (String) -> Unit,
    onLoadClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "${if (expanded) "v" else ">"} ${stringResource(LFRes.String.builder_stats)}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
        )

        AnimatedVisibility(visible = expanded) {
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

                StatRow(stringResource(LFRes.String.builder_mass), "%.1f".format(stats.totalMass))

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(LFRes.String.builder_thrust),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatRow(stringResource(LFRes.String.builder_forward), "%.1f".format(stats.forwardThrust))
                StatRow(stringResource(LFRes.String.builder_lateral), "%.1f".format(stats.lateralThrust))
                StatRow(stringResource(LFRes.String.builder_reverse), "%.1f".format(stats.reverseThrust))
                StatRow(stringResource(LFRes.String.builder_angular), "%.1f".format(stats.angularThrust))

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(LFRes.String.builder_acceleration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatRow(stringResource(LFRes.String.builder_forward), "%.1f".format(stats.forwardAccel))
                StatRow(stringResource(LFRes.String.builder_lateral), "%.1f".format(stats.lateralAccel))
                StatRow(stringResource(LFRes.String.builder_reverse), "%.1f".format(stats.reverseAccel))
                StatRow(stringResource(LFRes.String.builder_angular), "%.1f".format(stats.angularAccel))

                Spacer(modifier = Modifier.height(12.dp))

                FilledTonalButton(
                    onClick = onLoadClicked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(LFRes.String.builder_load_design))
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

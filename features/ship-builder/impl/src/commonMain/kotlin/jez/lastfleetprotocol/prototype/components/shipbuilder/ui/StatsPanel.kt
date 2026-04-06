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
            text = "${if (expanded) "v" else ">"} Stats",
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
                    label = { Text("Design Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                StatRow("Mass", "%.1f".format(stats.totalMass))

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Thrust",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatRow("Forward", "%.1f".format(stats.forwardThrust))
                StatRow("Lateral", "%.1f".format(stats.lateralThrust))
                StatRow("Reverse", "%.1f".format(stats.reverseThrust))
                StatRow("Angular", "%.1f".format(stats.angularThrust))

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Acceleration",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatRow("Forward", "%.1f".format(stats.forwardAccel))
                StatRow("Lateral", "%.1f".format(stats.lateralAccel))
                StatRow("Reverse", "%.1f".format(stats.reverseAccel))
                StatRow("Angular", "%.1f".format(stats.angularAccel))

                Spacer(modifier = Modifier.height(12.dp))

                FilledTonalButton(
                    onClick = onLoadClicked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Load Design")
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

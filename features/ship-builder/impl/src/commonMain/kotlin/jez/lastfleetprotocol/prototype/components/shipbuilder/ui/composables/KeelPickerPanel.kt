package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.ui.common.composables.LFTextButton
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import org.jetbrains.compose.resources.stringResource

/**
 * Slice B Unit 5: the mandatory Keel-picker first step. Shown in the right panel
 * when [jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.EditorMode.PickingKeel]
 * is active. Canvas and parts panel are hidden at the screen level while this is on-screen.
 *
 * User picks exactly one Keel — the choice defines ship class and lift budget
 * per R9/R10. Committing a Keel places it at origin and transitions to
 * `EditingShip`. Cancelling pops back to the landing screen.
 */
@Composable
fun KeelPickerPanel(
    keels: List<ItemDefinition>,
    onPickKeel: (ItemDefinition) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = stringResource(LFRes.String.builder_pick_keel_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
        Text(
            text = stringResource(LFRes.String.builder_pick_keel_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (keels.isEmpty()) {
                Text(
                    text = stringResource(LFRes.String.builder_keel_no_options),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                for (keel in keels) {
                    KeelRow(keel = keel, onClick = { onPickKeel(keel) })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LFTextButton(
            text = stringResource(LFRes.String.button_cancel),
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun KeelRow(keel: ItemDefinition, onClick: () -> Unit) {
    val attrs = keel.attributes as? ItemAttributes.KeelAttributes ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Text(
            text = keel.name,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatChip(
                label = stringResource(LFRes.String.builder_keel_class),
                value = attrs.shipClass.ifBlank { "—" },
            )
            StatChip(
                label = stringResource(LFRes.String.builder_keel_lift),
                value = "%.0f".format(attrs.lift),
            )
            StatChip(
                label = stringResource(LFRes.String.builder_mass),
                value = "%.0f".format(attrs.mass),
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

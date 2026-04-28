package jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.SlotEntry
import jez.lastfleetprotocol.prototype.ui.common.composables.LFIconButton
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import org.jetbrains.compose.resources.stringResource

/**
 * Single row in a team panel: ship-name dropdown, x/y position fields, AI
 * checkbox, remove icon. Vertically stacked because the panel is fixed at
 * 200dp wide — too narrow to fit everything on one line readably.
 *
 * When the row's slot id is in `state.brokenSlotIds`, the design dropdown
 * border is tinted via the theme's error colour, x/y inputs are disabled,
 * and a warning icon prefixes the dropdown row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotRow(
    slot: SlotEntry,
    libraryShipNames: List<String>,
    isBroken: Boolean,
    onShipChange: (String) -> Unit,
    onPositionChange: (x: Float, y: Float) -> Unit,
    onToggleAi: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (isBroken) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Row 1: warning + dropdown + remove
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBroken) {
                    Icon(
                        painter = org.jetbrains.compose.resources.painterResource(LFRes.Drawable.ic_warning),
                        contentDescription = null,
                        modifier = Modifier.width(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                ShipNameDropdown(
                    selected = slot.designName,
                    options = libraryShipNames,
                    onSelect = onShipChange,
                    isBroken = isBroken,
                    modifier = Modifier.weight(1f),
                )
                LFIconButton(
                    onClick = onRemove,
                    drawable = LFRes.Drawable.ic_delete,
                    contentDescription = stringResource(LFRes.String.scenario_remove_slot),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: x / y position fields
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = slot.x.toCleanString(),
                    onValueChange = { value ->
                        val parsed = value.toFloatOrNull() ?: return@OutlinedTextField
                        onPositionChange(parsed, slot.y)
                    },
                    label = { Text(stringResource(LFRes.String.scenario_x_position)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isBroken,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = slot.y.toCleanString(),
                    onValueChange = { value ->
                        val parsed = value.toFloatOrNull() ?: return@OutlinedTextField
                        onPositionChange(slot.x, parsed)
                    },
                    label = { Text(stringResource(LFRes.String.scenario_y_position)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isBroken,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Row 3: AI toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = slot.withAI, onCheckedChange = { onToggleAi() })
                Text(stringResource(LFRes.String.scenario_ai_toggle))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShipNameDropdown(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    isBroken: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            colors = if (isBroken) {
                OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.error,
                    focusedBorderColor = MaterialTheme.colorScheme.error,
                )
            } else {
                OutlinedTextFieldDefaults.colors()
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

private fun Float.toCleanString(): String =
    if (this == this.toInt().toFloat()) this.toInt().toString() else this.toString()

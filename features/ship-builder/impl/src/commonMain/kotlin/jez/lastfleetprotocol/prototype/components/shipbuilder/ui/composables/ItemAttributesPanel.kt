package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemType
import jez.lastfleetprotocol.prototype.components.shipbuilder.geometry.calculatePolygonArea
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.EditorMode
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import org.jetbrains.compose.resources.stringResource

/**
 * Type-specific attribute editor for items being created.
 * Renders different fields based on the item type (Hull, Module, Turret).
 */
@Composable
fun ItemAttributesPanel(
    creatingItem: EditorMode.CreatingItem,
    onNameChanged: (String) -> Unit,
    onAttributesChanged: (ItemAttributes) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        Text(
            text = stringResource(LFRes.String.builder_item_attributes),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        OutlinedTextField(
            value = creatingItem.name,
            onValueChange = onNameChanged,
            label = { Text(stringResource(LFRes.String.builder_item_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Area display (shared across all types)
        val area = calculatePolygonArea(creatingItem.vertices)
        AttributeReadOnlyRow(
            label = stringResource(LFRes.String.builder_area),
            value = "%.1f".format(area),
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        when (creatingItem.itemType) {
            ItemType.HULL -> HullAttributesContent(
                attributes = creatingItem.attributes as ItemAttributes.HullAttributes,
                onAttributesChanged = onAttributesChanged,
            )

            ItemType.MODULE -> ModuleAttributesContent(
                attributes = creatingItem.attributes as ItemAttributes.ModuleAttributes,
                onAttributesChanged = onAttributesChanged,
                onNameChanged = onNameChanged,
                currentName = creatingItem.name,
            )

            ItemType.TURRET -> TurretAttributesContent(
                attributes = creatingItem.attributes as ItemAttributes.TurretAttributes,
                area = area,
                onAttributesChanged = onAttributesChanged,
            )
        }
    }
}

// --- Hull ---

@Composable
private fun HullAttributesContent(
    attributes: ItemAttributes.HullAttributes,
    onAttributesChanged: (ItemAttributes) -> Unit,
) {
    NumericField(
        label = stringResource(LFRes.String.builder_armour_hardness),
        value = attributes.armour.hardness,
        onValueChange = {
            onAttributesChanged(attributes.copy(armour = attributes.armour.copy(hardness = it)))
        },
    )

    Spacer(modifier = Modifier.height(4.dp))

    NumericField(
        label = stringResource(LFRes.String.builder_armour_density),
        value = attributes.armour.density,
        onValueChange = {
            onAttributesChanged(attributes.copy(armour = attributes.armour.copy(density = it)))
        },
    )

    Spacer(modifier = Modifier.height(4.dp))

    NumericField(
        label = stringResource(LFRes.String.builder_mass),
        value = attributes.mass,
        onValueChange = { onAttributesChanged(attributes.copy(mass = it)) },
    )

    Spacer(modifier = Modifier.height(4.dp))

    NumericField(
        label = stringResource(LFRes.String.builder_drag_forward),
        value = attributes.forwardDragModifier,
        onValueChange = { onAttributesChanged(attributes.copy(forwardDragModifier = it)) },
    )

    Spacer(modifier = Modifier.height(4.dp))

    NumericField(
        label = stringResource(LFRes.String.builder_drag_lateral),
        value = attributes.lateralDragModifier,
        onValueChange = { onAttributesChanged(attributes.copy(lateralDragModifier = it)) },
    )

    Spacer(modifier = Modifier.height(4.dp))

    NumericField(
        label = stringResource(LFRes.String.builder_drag_reverse),
        value = attributes.reverseDragModifier,
        onValueChange = { onAttributesChanged(attributes.copy(reverseDragModifier = it)) },
    )

    Spacer(modifier = Modifier.height(4.dp))

    AttributeReadOnlyRow(
        label = stringResource(LFRes.String.builder_size_category),
        value = attributes.sizeCategory,
    )
}

// --- Module ---

private val MODULE_TYPES = listOf("REACTOR", "MAIN_ENGINE", "BRIDGE")
private val MODULE_TYPE_DISPLAY = mapOf(
    "REACTOR" to "Reactor",
    "MAIN_ENGINE" to "Main Engine",
    "BRIDGE" to "Bridge",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModuleAttributesContent(
    attributes: ItemAttributes.ModuleAttributes,
    onAttributesChanged: (ItemAttributes) -> Unit,
    onNameChanged: (String) -> Unit,
    currentName: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentDisplayName = MODULE_TYPE_DISPLAY[attributes.systemType] ?: attributes.systemType

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = currentDisplayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(LFRes.String.builder_module_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (type in MODULE_TYPES) {
                val displayName = MODULE_TYPE_DISPLAY[type] ?: type
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                        expanded = false
                        val oldDisplayName = MODULE_TYPE_DISPLAY[attributes.systemType]
                            ?: attributes.systemType
                        val newAttrs = when (type) {
                            "MAIN_ENGINE" -> attributes.copy(
                                systemType = type,
                                forwardThrust = if (attributes.systemType == "MAIN_ENGINE") attributes.forwardThrust else 1200f,
                                lateralThrust = if (attributes.systemType == "MAIN_ENGINE") attributes.lateralThrust else 500f,
                                reverseThrust = if (attributes.systemType == "MAIN_ENGINE") attributes.reverseThrust else 500f,
                                angularThrust = if (attributes.systemType == "MAIN_ENGINE") attributes.angularThrust else 300f,
                            )

                            else -> attributes.copy(
                                systemType = type,
                                forwardThrust = 0f,
                                lateralThrust = 0f,
                                reverseThrust = 0f,
                                angularThrust = 0f,
                            )
                        }
                        onAttributesChanged(newAttrs)
                        // Update name if it still matches the old type name
                        if (currentName == oldDisplayName || currentName == "Custom Module") {
                            onNameChanged(displayName)
                        }
                    },
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    NumericField(
        label = stringResource(LFRes.String.builder_max_hp),
        value = attributes.maxHp,
        onValueChange = { onAttributesChanged(attributes.copy(maxHp = it)) },
    )

    Spacer(modifier = Modifier.height(4.dp))

    NumericField(
        label = stringResource(LFRes.String.builder_density),
        value = attributes.density,
        onValueChange = { onAttributesChanged(attributes.copy(density = it)) },
    )

    Spacer(modifier = Modifier.height(4.dp))

    NumericField(
        label = stringResource(LFRes.String.builder_mass),
        value = attributes.mass,
        onValueChange = { onAttributesChanged(attributes.copy(mass = it)) },
    )

    // Thrust fields for MAIN_ENGINE
    AnimatedVisibility(visible = attributes.systemType == "MAIN_ENGINE") {
        Column {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = stringResource(LFRes.String.builder_thrust),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            NumericField(
                label = stringResource(LFRes.String.builder_forward_thrust),
                value = attributes.forwardThrust,
                onValueChange = { onAttributesChanged(attributes.copy(forwardThrust = it)) },
            )

            Spacer(modifier = Modifier.height(4.dp))

            NumericField(
                label = stringResource(LFRes.String.builder_lateral_thrust),
                value = attributes.lateralThrust,
                onValueChange = { onAttributesChanged(attributes.copy(lateralThrust = it)) },
            )

            Spacer(modifier = Modifier.height(4.dp))

            NumericField(
                label = stringResource(LFRes.String.builder_reverse_thrust),
                value = attributes.reverseThrust,
                onValueChange = { onAttributesChanged(attributes.copy(reverseThrust = it)) },
            )

            Spacer(modifier = Modifier.height(4.dp))

            NumericField(
                label = stringResource(LFRes.String.builder_angular_thrust),
                value = attributes.angularThrust,
                onValueChange = { onAttributesChanged(attributes.copy(angularThrust = it)) },
            )
        }
    }
}

// --- Turret ---

private fun sizeFromArea(area: Float): String = when {
    area < 200f -> "Small"
    area < 500f -> "Medium"
    else -> "Large"
}

@Composable
private fun TurretAttributesContent(
    attributes: ItemAttributes.TurretAttributes,
    area: Float,
    onAttributesChanged: (ItemAttributes) -> Unit,
) {
    AttributeReadOnlyRow(
        label = stringResource(LFRes.String.builder_size_category),
        value = sizeFromArea(area),
    )

    Spacer(modifier = Modifier.height(8.dp))

    NumericField(
        label = stringResource(LFRes.String.builder_mass),
        value = attributes.mass,
        onValueChange = { onAttributesChanged(attributes.copy(mass = it)) },
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Fixed vs Rotating toggle
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = if (attributes.isFixed) {
                stringResource(LFRes.String.builder_fixed)
            } else {
                stringResource(LFRes.String.builder_rotating)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = !attributes.isFixed,
            onCheckedChange = { rotating ->
                onAttributesChanged(attributes.copy(isFixed = !rotating))
            },
        )
    }

    // Fields visible only when rotating
    AnimatedVisibility(visible = !attributes.isFixed) {
        Column {
            NumericField(
                label = stringResource(LFRes.String.builder_default_facing),
                value = Math.toDegrees(attributes.defaultFacing.toDouble()).toFloat(),
                onValueChange = {
                    onAttributesChanged(
                        attributes.copy(defaultFacing = Math.toRadians(it.toDouble()).toFloat())
                    )
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Limited rotation toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(LFRes.String.builder_limited_rotation),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = attributes.isLimitedRotation,
                    onCheckedChange = {
                        onAttributesChanged(attributes.copy(isLimitedRotation = it))
                    },
                )
            }

            // Min/max angle fields
            AnimatedVisibility(visible = attributes.isLimitedRotation) {
                Column {
                    NumericField(
                        label = stringResource(LFRes.String.builder_min_angle),
                        value = Math.toDegrees(attributes.minAngle.toDouble()).toFloat(),
                        onValueChange = {
                            onAttributesChanged(
                                attributes.copy(minAngle = Math.toRadians(it.toDouble()).toFloat())
                            )
                        },
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    NumericField(
                        label = stringResource(LFRes.String.builder_max_angle),
                        value = Math.toDegrees(attributes.maxAngle.toDouble()).toFloat(),
                        onValueChange = {
                            onAttributesChanged(
                                attributes.copy(maxAngle = Math.toRadians(it.toDouble()).toFloat())
                            )
                        },
                    )
                }
            }
        }
    }
}

// --- Shared components ---

@Composable
private fun NumericField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textValue by remember(value) { mutableStateOf("%.1f".format(value)) }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            textValue = newText
            newText.toFloatOrNull()?.let { onValueChange(it) }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun AttributeReadOnlyRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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

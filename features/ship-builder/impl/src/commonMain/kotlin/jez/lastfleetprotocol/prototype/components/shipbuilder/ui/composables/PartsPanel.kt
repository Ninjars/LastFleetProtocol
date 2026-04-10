package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemType
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.PartsCatalog
import jez.lastfleetprotocol.prototype.ui.common.composables.LFIconButton
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import lastfleetprotocol.components.design.generated.resources.Res
import lastfleetprotocol.components.design.generated.resources.button_add
import lastfleetprotocol.components.design.generated.resources.ic_add_2
import lastfleetprotocol.components.design.generated.resources.ic_copy
import lastfleetprotocol.components.design.generated.resources.ic_delete
import lastfleetprotocol.components.design.generated.resources.ic_edit
import lastfleetprotocol.components.design.generated.resources.pointer_down
import lastfleetprotocol.components.design.generated.resources.pointer_right
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PartsPanel(
    onAddItem: (ItemDefinition) -> Unit,
    onCreateItem: (ItemType) -> Unit,
    onDuplicateItem: (ItemDefinition) -> Unit,
    onEditItem: (ItemDefinition) -> Unit,
    onDeleteItem: (ItemDefinition) -> Unit,
    modifier: Modifier = Modifier,
    customItems: List<ItemDefinition> = emptyList(),
) {
    val customHulls = customItems.filter { it.itemType == ItemType.HULL }
    val customModules = customItems.filter { it.itemType == ItemType.MODULE }
    val customTurrets = customItems.filter { it.itemType == ItemType.TURRET }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        CollapsibleSection(
            title = stringResource(LFRes.String.builder_hull_pieces),
            onAdd = { onCreateItem(ItemType.HULL) },
        ) {
            for (item in PartsCatalog.hullItems) {
                ItemRow(
                    name = item.name,
                    detail = (item.attributes as? ItemAttributes.HullAttributes)?.sizeCategory,
                    mass = item.attributes.mass,
                    previewColor = Color.Cyan,
                    previewVerts = item.vertices,
                    onClick = { onAddItem(item) },
                    onDuplicate = null,
                    onEdit = null,
                    onDelete = null,
                )
            }
            for (item in customHulls) {
                ItemRow(
                    name = item.name,
                    detail = (item.attributes as? ItemAttributes.HullAttributes)?.sizeCategory,
                    mass = item.attributes.mass,
                    previewColor = Color.Cyan,
                    previewVerts = item.vertices,
                    onClick = { onAddItem(item) },
                    onDuplicate = { onDuplicateItem(item) },
                    onEdit = { onEditItem(item) },
                    onDelete = { onDeleteItem(item) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        CollapsibleSection(
            title = stringResource(LFRes.String.builder_systems),
            onAdd = { onCreateItem(ItemType.MODULE) },
        ) {
            for (item in PartsCatalog.moduleItems) {
                ItemRow(
                    name = item.name,
                    detail = (item.attributes as? ItemAttributes.ModuleAttributes)?.systemType,
                    mass = item.attributes.mass,
                    previewColor = Color.Yellow,
                    previewVerts = item.vertices,
                    onClick = { onAddItem(item) },
                    onDuplicate = null,
                    onEdit = null,
                    onDelete = null,
                )
            }
            for (item in customModules) {
                ItemRow(
                    name = item.name,
                    detail = (item.attributes as? ItemAttributes.ModuleAttributes)?.systemType,
                    mass = item.attributes.mass,
                    previewColor = Color.Yellow,
                    previewVerts = item.vertices,
                    onClick = { onAddItem(item) },
                    onDuplicate = { onDuplicateItem(item) },
                    onEdit = { onEditItem(item) },
                    onDelete = { onDeleteItem(item) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        CollapsibleSection(
            title = stringResource(LFRes.String.builder_turrets),
            onAdd = { onCreateItem(ItemType.TURRET) },
        ) {
            for (item in PartsCatalog.turretItems) {
                ItemRow(
                    name = item.name,
                    detail = (item.attributes as? ItemAttributes.TurretAttributes)?.sizeCategory,
                    mass = item.attributes.mass,
                    previewColor = Color.Red,
                    previewVerts = item.vertices,
                    onClick = { onAddItem(item) },
                    onDuplicate = null,
                    onEdit = null,
                    onDelete = null,
                )
            }
            for (item in customTurrets) {
                ItemRow(
                    name = item.name,
                    detail = (item.attributes as? ItemAttributes.TurretAttributes)?.sizeCategory,
                    mass = item.attributes.mass,
                    previewColor = Color.Red,
                    previewVerts = item.vertices,
                    onClick = { onAddItem(item) },
                    onDuplicate = { onDuplicateItem(item) },
                    onEdit = { onEditItem(item) },
                    onDelete = { onDeleteItem(item) },
                )
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp)
    ) {
        Image(
            painter = painterResource(
                if (expanded) {
                    Res.drawable.pointer_down
                } else {
                    Res.drawable.pointer_right
                }
            ),
            contentDescription = stringResource(Res.string.button_add),
            colorFilter = MaterialTheme.colorScheme.onBackground.let { ColorFilter.tint(it) },
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        LFIconButton(
            drawable = Res.drawable.ic_add_2,
            contentDescription = stringResource(Res.string.button_add),
            onClick = onAdd
        )
    }

    AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(start = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun ItemRow(
    name: String,
    detail: String?,
    mass: Float,
    previewColor: Color,
    previewVerts: List<SceneOffset>,
    onClick: () -> Unit,
    onDuplicate: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        ItemPreview(previewVerts = previewVerts, color = previewColor)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "$detail - ${mass}t",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            if (onEdit != null) {
                LFIconButton(
                    drawable = Res.drawable.ic_edit,
                    contentDescription = stringResource(LFRes.String.builder_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                )
            }
            if (onDuplicate != null) {
                LFIconButton(
                    drawable = Res.drawable.ic_copy,
                    contentDescription = stringResource(LFRes.String.builder_duplicate),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onDuplicate,
                    modifier = Modifier.size(32.dp)
                )
            }
            if (onDelete != null) {
                LFIconButton(
                    drawable = Res.drawable.ic_delete,
                    contentDescription = stringResource(LFRes.String.builder_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun ItemPreview(
    previewVerts: List<SceneOffset>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val previewSize = 40.dp
    Canvas(modifier = modifier.size(previewSize)) {
        if (previewVerts.isEmpty()) return@Canvas

        // Find bounds to scale preview
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (v in previewVerts) {
            val vx = v.x.raw
            val vy = v.y.raw
            if (vx < minX) minX = vx
            if (vx > maxX) maxX = vx
            if (vy < minY) minY = vy
            if (vy > maxY) maxY = vy
        }

        val rangeX = maxX - minX
        val rangeY = maxY - minY
        val scale = if (rangeX > 0 && rangeY > 0) {
            minOf(size.width / rangeX, size.height / rangeY) * 0.8f
        } else 1f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f

        val path = Path()
        for (i in previewVerts.indices) {
            val px = cx + (previewVerts[i].x.raw - midX) * scale
            val py = cy + (previewVerts[i].y.raw - midY) * scale
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        drawPath(path = path, color = color.copy(alpha = 0.3f))
        drawPath(path = path, color = color, style = Stroke(width = 1f))
    }
}

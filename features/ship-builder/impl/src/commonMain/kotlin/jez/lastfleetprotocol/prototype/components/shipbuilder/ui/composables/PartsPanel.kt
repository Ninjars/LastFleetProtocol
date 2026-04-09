package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemType
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.PartsCatalog
import jez.lastfleetprotocol.prototype.ui.common.composables.LFIconButton
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import lastfleetprotocol.components.design.generated.resources.Res
import lastfleetprotocol.components.design.generated.resources.button_add
import lastfleetprotocol.components.design.generated.resources.ic_add_2
import lastfleetprotocol.components.design.generated.resources.pointer_down
import lastfleetprotocol.components.design.generated.resources.pointer_right
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PartsPanel(
    onAddItem: (ItemDefinition) -> Unit,
    onCreateItem: (ItemType) -> Unit,
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
                HullPieceItem(
                    item = item,
                    onClick = { onAddItem(item) },
                )
            }
            for (item in customHulls) {
                HullPieceItem(
                    item = item,
                    onClick = { onAddItem(item) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        CollapsibleSection(
            title = stringResource(LFRes.String.builder_systems),
            onAdd = { onCreateItem(ItemType.MODULE) },
        ) {
            for (item in PartsCatalog.moduleItems) {
                SystemModuleItem(
                    item = item,
                    onClick = { onAddItem(item) },
                )
            }
            for (item in customModules) {
                SystemModuleItem(
                    item = item,
                    onClick = { onAddItem(item) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        CollapsibleSection(
            title = stringResource(LFRes.String.builder_turrets),
            onAdd = { onCreateItem(ItemType.TURRET) },
        ) {
            for (item in PartsCatalog.turretItems) {
                TurretModuleItem(
                    item = item,
                    onClick = { onAddItem(item) },
                )
            }
            for (item in customTurrets) {
                TurretModuleItem(
                    item = item,
                    onClick = { onAddItem(item) },
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
//        LFIconButton(
//            drawable = if (expanded) {
//                Res.drawable.pointer_down
//            } else {
//                Res.drawable.pointer_right
//            },
//            contentDescription = stringResource(Res.string.button_add),
//            modifier = Modifier.size(36.dp)
//        ) { expanded = !expanded }
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
private fun HullPieceItem(
    item: ItemDefinition,
    onClick: () -> Unit,
) {
    val attrs = item.attributes as? ItemAttributes.HullAttributes
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        ItemPreview(item = item, color = Color.Cyan)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (attrs != null) {
                Text(
                    text = "${attrs.sizeCategory} - ${attrs.mass}t",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ItemPreview(
    item: ItemDefinition,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val previewSize = 40.dp
    Canvas(modifier = modifier.size(previewSize)) {
        if (item.vertices.isEmpty()) return@Canvas

        // Find bounds to scale preview
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (v in item.vertices) {
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
        for (i in item.vertices.indices) {
            val px = cx + (item.vertices[i].x.raw - midX) * scale
            val py = cy + (item.vertices[i].y.raw - midY) * scale
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        drawPath(path = path, color = color.copy(alpha = 0.3f))
        drawPath(path = path, color = color, style = Stroke(width = 1f))
    }
}

@Composable
private fun SystemModuleItem(
    item: ItemDefinition,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        ItemPreview(item = item, color = Color.Yellow)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TurretModuleItem(
    item: ItemDefinition,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        ItemPreview(item = item, color = Color.Red)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

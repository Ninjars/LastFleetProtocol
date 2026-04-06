package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.CatalogHullPiece
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.CatalogSystemModule
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.CatalogTurretModule
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.PartsCatalog

@Composable
fun PartsPanel(
    onAddHullPiece: (CatalogHullPiece) -> Unit,
    onAddModule: (CatalogSystemModule) -> Unit,
    onAddTurret: (CatalogTurretModule) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        CollapsibleSection(title = "Hull Pieces") {
            for (piece in PartsCatalog.hullPieces) {
                HullPieceItem(
                    piece = piece,
                    onClick = { onAddHullPiece(piece) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        CollapsibleSection(title = "Systems") {
            for (module in PartsCatalog.systemModules) {
                SystemModuleItem(
                    module = module,
                    onClick = { onAddModule(module) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        CollapsibleSection(title = "Turrets") {
            for (turret in PartsCatalog.turretModules) {
                TurretModuleItem(
                    turret = turret,
                    onClick = { onAddTurret(turret) },
                )
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Text(
        text = "${if (expanded) "v" else ">"} $title",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
    )

    AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(start = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun HullPieceItem(
    piece: CatalogHullPiece,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        HullPreview(piece = piece)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = piece.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${piece.sizeCategory} - ${piece.mass}t",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HullPreview(
    piece: CatalogHullPiece,
    modifier: Modifier = Modifier,
) {
    val previewSize = 40.dp
    Canvas(modifier = modifier.size(previewSize)) {
        if (piece.vertices.isEmpty()) return@Canvas

        // Find bounds to scale preview
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (v in piece.vertices) {
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
        for (i in piece.vertices.indices) {
            val px = cx + (piece.vertices[i].x.raw - midX) * scale
            val py = cy + (piece.vertices[i].y.raw - midY) * scale
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        drawPath(path = path, color = Color.Cyan.copy(alpha = 0.3f))
        drawPath(path = path, color = Color.Cyan, style = Stroke(width = 1f))
    }
}

@Composable
private fun SystemModuleItem(
    module: CatalogSystemModule,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            drawRect(
                color = Color.Yellow.copy(alpha = 0.4f),
                topLeft = Offset(4f, 4f),
                size = androidx.compose.ui.geometry.Size(size.width - 8f, size.height - 8f),
            )
            drawRect(
                color = Color.Yellow,
                topLeft = Offset(4f, 4f),
                size = androidx.compose.ui.geometry.Size(size.width - 8f, size.height - 8f),
                style = Stroke(width = 1f),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = module.name,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TurretModuleItem(
    turret: CatalogTurretModule,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            drawCircle(
                color = Color.Red.copy(alpha = 0.4f),
                radius = size.minDimension / 2f - 4f,
            )
            drawCircle(
                color = Color.Red,
                radius = size.minDimension / 2f - 4f,
                style = Stroke(width = 1f),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = turret.name,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

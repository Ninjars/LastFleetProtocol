package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.CanvasState
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.DesignCanvas
import jez.lastfleetprotocol.prototype.ui.common.HandleSideEffect
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import org.jetbrains.compose.resources.stringResource
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

typealias ShipBuilderScreen = @Composable (NavController) -> Unit

@Inject
@Composable
fun ShipBuilderScreen(
    viewModelFactory: () -> ShipBuilderVM,
    @Assisted navController: NavController,
) {
    val viewModel = viewModel { viewModelFactory() }
    viewModel.HandleSideEffect {
        when (it) {
            is ShipBuilderSideEffect.NavigateBack -> navController.popBackStack()
        }
    }

    ShipBuilderScreen(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onIntent = viewModel::accept,
    )
}

@Composable
private fun ShipBuilderScreen(
    state: ShipBuilderState,
    onIntent: (ShipBuilderIntent) -> Unit,
) {
    // Canvas pan/zoom is view-only state — kept out of the VM to avoid
    // recomposition restarting the gesture coroutine mid-drag/pinch.
    var canvasState by remember { mutableStateOf(CanvasState()) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel: Parts
        PartsPanel(
            onAddHullPiece = { onIntent(ShipBuilderIntent.AddHullPiece(it)) },
            onAddModule = { onIntent(ShipBuilderIntent.AddModule(it)) },
            onAddTurret = { onIntent(ShipBuilderIntent.AddTurret(it)) },
            modifier = Modifier.width(200.dp).fillMaxHeight(),
        )

        // Center: Design canvas with transform toolbar overlay
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            DesignCanvas(
                state = state,
                canvasState = canvasState,
                onCanvasStateChanged = { canvasState = it },
                onSelectItem = { onIntent(ShipBuilderIntent.SelectItem(it)) },
                onDeselect = { onIntent(ShipBuilderIntent.Deselect) },
                onMoveItem = { id, pos -> onIntent(ShipBuilderIntent.MoveItem(id, pos)) },
                onRotateItem = { id, angle -> onIntent(ShipBuilderIntent.RotateItem(id, angle)) },
                modifier = Modifier.fillMaxSize(),
            )

            TransformToolbar(
                isVisible = state.selectedItemId != null,
                onMirrorX = {
                    state.selectedItemId?.let { onIntent(ShipBuilderIntent.MirrorItemX(it)) }
                },
                onMirrorY = {
                    state.selectedItemId?.let { onIntent(ShipBuilderIntent.MirrorItemY(it)) }
                },
                onRotateCW = {
                    state.selectedItemId?.let { onIntent(ShipBuilderIntent.RotateCW(it)) }
                },
                onRotateCCW = {
                    state.selectedItemId?.let { onIntent(ShipBuilderIntent.RotateCCW(it)) }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // Right panel: Stats
        StatsPanel(
            stats = state.stats,
            designName = state.designName,
            onNameChanged = { onIntent(ShipBuilderIntent.RenameDesign(it)) },
            onLoadClicked = { onIntent(ShipBuilderIntent.LoadDesignClicked) },
            modifier = Modifier.width(200.dp).fillMaxHeight(),
        )
    }

    // Load design dialog
    if (state.showLoadDialog) {
        LoadDesignDialog(
            designs = state.savedDesigns,
            onSelect = { onIntent(ShipBuilderIntent.ConfirmLoad(it)) },
            onDismiss = { onIntent(ShipBuilderIntent.DismissLoadDialog) },
        )
    }
}

@Composable
private fun LoadDesignDialog(
    designs: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(LFRes.String.builder_load_design)) },
        text = {
            if (designs.isEmpty()) {
                Text(stringResource(LFRes.String.builder_no_saved_designs))
            } else {
                LazyColumn {
                    items(designs) { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(name) }
                                .padding(vertical = 8.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(LFRes.String.button_cancel))
            }
        },
    )
}

package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemType
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.DesignCanvas
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.EditorMode
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderIntent
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderSideEffect
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderState
import jez.lastfleetprotocol.prototype.ui.common.HandleSideEffect
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.stringResource

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
        onCanvasIntent = viewModel::handleCanvasIntent,
    )
}

@Composable
private fun ShipBuilderScreen(
    state: ShipBuilderState,
    onIntent: (ShipBuilderIntent) -> Unit,
    onCanvasIntent: (ShipBuilderIntent) -> Boolean,
) {
    val editorMode = state.editorMode

    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel: Parts (editing mode) or minimal creation controls
        when (editorMode) {
            is EditorMode.EditingShip -> {
                PartsPanel(
                    onAddItem = { onIntent(ShipBuilderIntent.AddItem(it)) },
                    onCreateHull = { onIntent(ShipBuilderIntent.EnterCreationMode(ItemType.HULL)) },
                    onCreateModule = { onIntent(ShipBuilderIntent.EnterCreationMode(ItemType.MODULE)) },
                    onCreateTurret = { onIntent(ShipBuilderIntent.EnterCreationMode(ItemType.TURRET)) },
                    customItems = state.customItemDefinitions,
                    modifier = Modifier.width(200.dp).fillMaxHeight(),
                )
            }

            is EditorMode.CreatingItem -> {
                // Minimal left panel in creation mode — just cancel/finish
                CreationControlsPanel(
                    onCancel = { onIntent(ShipBuilderIntent.ExitCreationMode) },
                    onFinish = { onIntent(ShipBuilderIntent.FinishCreation) },
                    modifier = Modifier.width(200.dp).fillMaxHeight(),
                )
            }
        }

        // Center: Design canvas with transform toolbar overlay
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            DesignCanvas(
                state = state,
                onIntent = onCanvasIntent,
                modifier = Modifier.fillMaxSize(),
            )

            if (editorMode is EditorMode.EditingShip) {
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
        }

        // Right panel: Stats (editing mode) or Item Attributes (creation mode)
        when (editorMode) {
            is EditorMode.EditingShip -> {
                StatsPanel(
                    stats = state.stats,
                    designName = state.designName,
                    onNameChanged = { onIntent(ShipBuilderIntent.RenameDesign(it)) },
                    onLoadClicked = { onIntent(ShipBuilderIntent.LoadDesignClicked) },
                    modifier = Modifier.width(200.dp).fillMaxHeight(),
                )
            }

            is EditorMode.CreatingItem -> {
                ItemCreationAttributesPanel(
                    creatingItem = editorMode,
                    onNameChanged = { onIntent(ShipBuilderIntent.UpdateCreationName(it)) },
                    onAttributesChanged = { onIntent(ShipBuilderIntent.UpdateCreationAttributes(it)) },
                    onFinish = { onIntent(ShipBuilderIntent.FinishCreation) },
                    onCancel = { onIntent(ShipBuilderIntent.ExitCreationMode) },
                    modifier = Modifier.width(200.dp).fillMaxHeight(),
                )
            }
        }
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
private fun CreationControlsPanel(
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(8.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        FilledTonalButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(LFRes.String.builder_finish))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(LFRes.String.builder_cancel_creation))
        }
    }
}

@Composable
private fun ItemCreationAttributesPanel(
    creatingItem: EditorMode.CreatingItem,
    onNameChanged: (String) -> Unit,
    onAttributesChanged: (jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(8.dp),
    ) {
        ItemAttributesPanel(
            creatingItem = creatingItem,
            onNameChanged = onNameChanged,
            onAttributesChanged = onAttributesChanged,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        FilledTonalButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(LFRes.String.builder_finish))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(LFRes.String.builder_cancel_creation))
        }
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

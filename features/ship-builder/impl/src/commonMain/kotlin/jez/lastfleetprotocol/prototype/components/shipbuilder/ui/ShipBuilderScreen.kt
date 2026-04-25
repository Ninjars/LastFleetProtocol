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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.DesignCanvas
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables.ItemAttributesPanel
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables.KeelPickerPanel
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables.PartsPanel
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables.isValidForSave
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables.StatsPanel
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables.TransformToolbar
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.EditorMode
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderIntent
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderSideEffect
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderState
import jez.lastfleetprotocol.prototype.ui.common.HandleSideEffect
import jez.lastfleetprotocol.prototype.ui.common.composables.LFTextButton
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // The new `LocalClipboard` API (CMP 1.10+) requires constructing platform-specific
    // ClipEntry instances (Transferable on Desktop, ClipData on Android) under
    // @ExperimentalComposeUiApi. The deprecated `LocalClipboardManager.setText` works
    // identically across both platforms with a single AnnotatedString call. Using the
    // deprecated API for v1; revisit if/when CMP removes it (likely 2.0+).
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    viewModel.HandleSideEffect {
        when (it) {
            is ShipBuilderSideEffect.NavigateBack -> navController.popBackStack()
            is ShipBuilderSideEffect.ShowToast -> coroutineScope.launch {
                snackbarHostState.showSnackbar(it.text)
            }
            is ShipBuilderSideEffect.CopyToClipboard -> coroutineScope.launch {
                clipboardManager.setText(AnnotatedString(it.text))
                snackbarHostState.showSnackbar(it.toastMessage)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ShipBuilderScreen(
            state = viewModel.state.collectAsStateWithLifecycle().value,
            onIntent = viewModel::accept,
            onCanvasIntent = viewModel::handleCanvasIntent,
        )

        // Snackbar host pinned to the bottom of the screen for export-action feedback
        // (and any future transient messaging). Sits above the main layout so it's
        // visible regardless of which sub-screen the editor is in.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
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
                    onCreateItem = { onIntent(ShipBuilderIntent.EnterCreationMode(it)) },
                    onDuplicateItem = { onIntent(ShipBuilderIntent.DuplicateLibraryItem(it)) },
                    onEditItem = { onIntent(ShipBuilderIntent.EditLibraryItem(it)) },
                    onDeleteItem = { onIntent(ShipBuilderIntent.DeleteLibraryItem(it)) },
                    // Asset export (Item A): only surfaces when the runtime gate is open.
                    // Null on Android, null in packaged Desktop builds, null when running
                    // from IDE without lfp.repo.root set. Hidden, not disabled-with-tooltip.
                    onExportItem = if (state.canExport) {
                        { onIntent(ShipBuilderIntent.ExportLibraryItem(it)) }
                    } else null,
                    customItems = state.customItemDefinitions,
                    modifier = Modifier.width(200.dp).fillMaxHeight(),
                )
            }

            is EditorMode.CreatingItem,
            is EditorMode.PickingKeel -> {
                // Hide items sidebar whilst creating or picking
            }
        }

        // Center: Design canvas with transform toolbar overlay.
        // Hidden entirely during PickingKeel — the picker is the focus.
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            if (editorMode !is EditorMode.PickingKeel) {
                DesignCanvas(
                    state = state,
                    onIntent = onCanvasIntent,
                    modifier = Modifier.fillMaxSize(),
                )
            }

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

        // Right panel: Stats (editing) / Item Attributes (creation) / Keel picker (picking)
        Column(
            modifier = Modifier.width(200.dp).padding(8.dp).fillMaxHeight(),
        ) {
            when (editorMode) {
                is EditorMode.EditingShip -> {
                    StatsPanel(
                        stats = state.stats,
                        placedKeel = state.placedKeel,
                        designName = state.designName,
                        onNameChanged = { onIntent(ShipBuilderIntent.RenameDesign(it)) },
                        onLoadClicked = { onIntent(ShipBuilderIntent.LoadDesignClicked) },
                        // Asset export (Item A): hidden when the runtime gate is closed.
                        onExportClicked = if (state.canExport) {
                            { onIntent(ShipBuilderIntent.ExportCurrentDesign) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is EditorMode.CreatingItem -> {
                    ItemCreationAttributesPanel(
                        creatingItem = editorMode,
                        onNameChanged = { onIntent(ShipBuilderIntent.UpdateCreationName(it)) },
                        onAttributesChanged = {
                            onIntent(
                                ShipBuilderIntent.UpdateCreationAttributes(
                                    it
                                )
                            )
                        },
                        onFinish = { onIntent(ShipBuilderIntent.FinishCreation) },
                        onCancel = { onIntent(ShipBuilderIntent.ExitCreationMode) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is EditorMode.PickingKeel -> {
                    KeelPickerPanel(
                        keels = state.availableKeels,
                        onPickKeel = { onIntent(ShipBuilderIntent.PickKeel(it)) },
                        onCancel = { onIntent(ShipBuilderIntent.CancelKeelPick) },
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    )
                }
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
private fun ItemCreationAttributesPanel(
    creatingItem: EditorMode.CreatingItem,
    onNameChanged: (String) -> Unit,
    onAttributesChanged: (ItemAttributes) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
    ) {
        ItemAttributesPanel(
            creatingItem = creatingItem,
            onNameChanged = onNameChanged,
            onAttributesChanged = onAttributesChanged,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        LFTextButton(
            text = stringResource(LFRes.String.builder_finish),
            onClick = onFinish,
            enabled = creatingItem.attributes.isValidForSave(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LFTextButton(
            text = stringResource(LFRes.String.builder_cancel_creation),
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
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

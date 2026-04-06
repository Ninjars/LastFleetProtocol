package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.DesignCanvas
import jez.lastfleetprotocol.prototype.ui.common.HandleSideEffect
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
                onPan = { onIntent(ShipBuilderIntent.Pan(it)) },
                onZoom = { onIntent(ShipBuilderIntent.Zoom(it)) },
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

        // Right panel: Stats (stub for now)
        Column(modifier = Modifier.width(200.dp).fillMaxHeight()) {
            Text("Stats")
        }
    }
}

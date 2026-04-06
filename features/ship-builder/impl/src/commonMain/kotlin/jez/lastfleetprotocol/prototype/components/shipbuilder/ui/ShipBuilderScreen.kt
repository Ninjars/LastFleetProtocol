package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
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
    )
}

@Composable
private fun ShipBuilderScreen(
    state: ShipBuilderState,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.width(200.dp).fillMaxHeight()) {
            Text("Parts")
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text("Canvas")
        }
        Column(modifier = Modifier.width(200.dp).fillMaxHeight()) {
            Text("Stats")
        }
    }
}

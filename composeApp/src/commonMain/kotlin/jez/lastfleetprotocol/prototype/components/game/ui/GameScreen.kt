package jez.lastfleetprotocol.prototype.components.game.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pandulapeter.kubriko.KubrikoViewport
import jez.lastfleetprotocol.prototype.ui.common.composables.LFIconButton
import lastfleetprotocol.composeapp.generated.resources.Res
import lastfleetprotocol.composeapp.generated.resources.ic_menu
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import java.util.function.Consumer


typealias GameScreen = @Composable (NavController) -> Unit

@Inject
@Composable
fun GameScreen(
    viewModelFactory: () -> GameVM,
    @Assisted navController: NavController,
) {
    val viewModel = viewModel { viewModelFactory() }
    GameScreen(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        eventHandler = viewModel,
    )
}

@Composable
private fun GameScreen(
    state: GameState,
    eventHandler: Consumer<GameEvent>,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        KubrikoViewport(
            kubriko = state.kubriko,
            modifier = Modifier
                .fillMaxSize(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth()
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
        ) {
            LFIconButton(
                drawable = Res.drawable.ic_menu,
            ) {
                eventHandler.accept(GameEvent.OpenMenuClicked)
            }
        }
    }
}

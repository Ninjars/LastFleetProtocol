package jez.lastfleetprotocol.prototype.components.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
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

@OptIn(ExperimentalComposeUiApi::class)
@Inject
@Composable
fun GameScreen(
    viewModelFactory: () -> GameVM,
    @Assisted navController: NavController,
) {
    val viewModel = viewModel { viewModelFactory() }
    BackHandler(true) {
        viewModel.accept(GameIntent.BackPressed)
    }
    GameScreen(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        eventHandler = viewModel,
    )
}

@Composable
private fun GameScreen(
    state: GameState,
    eventHandler: Consumer<GameIntent>,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        KubrikoViewport(
            kubriko = state.kubriko,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary),
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
                eventHandler.accept(GameIntent.OpenMenuClicked)
            }
        }
    }
}

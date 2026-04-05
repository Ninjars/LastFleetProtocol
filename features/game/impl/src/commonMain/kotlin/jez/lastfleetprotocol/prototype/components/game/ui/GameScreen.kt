package jez.lastfleetprotocol.prototype.components.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pandulapeter.kubriko.KubrikoViewport
import jez.lastfleetprotocol.prototype.components.game.managers.GameStateManager.GameResult
import jez.lastfleetprotocol.prototype.ui.common.HandleSideEffect
import jez.lastfleetprotocol.prototype.ui.common.composables.LFIconButton
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
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

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            GameSideEffect.NavigateBack -> navController.popBackStack()
        }
    }

    // Pause when window loses focus
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) {
            viewModel.onWindowFocusLost()
        }
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

        // HUD: menu button (only when not paused and no result)
        if (!state.isPaused && state.gameResult == null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
            ) {
                LFIconButton(
                    drawable = LFRes.Drawable.ic_menu,
                    tint = MaterialTheme.colorScheme.inverseOnSurface
                ) {
                    eventHandler.accept(GameIntent.OpenMenuClicked)
                }
            }
        }

        // Pause menu overlay
        if (state.isPaused) {
            OverlayMenu(
                title = "Paused",
            ) {
                Button(
                    onClick = { eventHandler.accept(GameIntent.ResumeClicked) },
                    modifier = Modifier.fillMaxWidth(0.5f),
                ) {
                    Text(text = "Resume")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { eventHandler.accept(GameIntent.RestartClicked) },
                    modifier = Modifier.fillMaxWidth(0.5f),
                ) {
                    Text(text = "Restart")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { eventHandler.accept(GameIntent.ExitClicked) },
                    modifier = Modifier.fillMaxWidth(0.5f),
                ) {
                    Text(text = "Exit")
                }
            }
        }

        // Game result overlay
        if (state.gameResult != null) {
            OverlayMenu(
                title = when (state.gameResult) {
                    GameResult.VICTORY -> "Victory!"
                    GameResult.DEFEAT -> "Defeat"
                },
            ) {
                Button(
                    onClick = { eventHandler.accept(GameIntent.RestartClicked) },
                ) {
                    Text(text = "Restart")
                }
            }
        }
    }
}

/**
 * Shared overlay design for pause menu and game result screens.
 * Semi-transparent background with centered content.
 */
@Composable
private fun OverlayMenu(
    title: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            Spacer(modifier = Modifier.height(24.dp))
            content()
        }
    }
}

package jez.lastfleetprotocol.prototype.components.landingscreen.ui

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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.KubrikoViewport
import jez.lastfleetprotocol.prototype.ui.common.HandleSideEffect
import jez.lastfleetprotocol.prototype.ui.common.PreviewWrapper
import jez.lastfleetprotocol.prototype.ui.common.composables.LFIconButton
import jez.lastfleetprotocol.prototype.ui.common.composables.LFTextButton
import jez.lastfleetprotocol.prototype.ui.navigation.LFNavDestination
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.util.function.Consumer


typealias LandingScreen = @Composable (NavController) -> Unit

@Inject
@Composable
fun LandingScreen(
    viewModelFactory: () -> LandingVM,
    @Assisted navController: NavController,
) {
    val viewModel = viewModel { viewModelFactory() }
    viewModel.HandleSideEffect {
        when (it) {
            is LandingSideEffect.GoToSettings -> navController.navigate(LFNavDestination.SETTINGS)
            is LandingSideEffect.StartNewGame -> navController.navigate(LFNavDestination.GAME)
            is LandingSideEffect.GoToShipBuilder -> navController.navigate(LFNavDestination.SHIP_BUILDER)
        }
    }

    LandingScreen(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        eventHandler = viewModel
    )
}

@Composable
private fun LandingScreen(
    state: LandingState,
    eventHandler: Consumer<LandingIntent>,
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        KubrikoViewport(
            kubriko = state.kubriko,
            modifier = Modifier
                .fillMaxSize(),
        )
        LandingScreenContent(state, eventHandler)
    }
}

@Composable
private fun LandingScreenContent(
    state: LandingState,
    eventHandler: Consumer<LandingIntent>,
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth()
        ) {
            LFIconButton(
                drawable = if (state.musicEnabled) LFRes.Drawable.ic_music_on else LFRes.Drawable.ic_music_off,
                contentDescription = stringResource(LFRes.String.desc_toggle_music)
            ) {
                eventHandler.accept(LandingIntent.ToggleMusicClicked(!state.musicEnabled))
            }
            LFIconButton(
                drawable = if (state.soundEffectsEnabled) LFRes.Drawable.ic_sound_effects_on else LFRes.Drawable.ic_sound_effects_off,
                contentDescription = stringResource(LFRes.String.desc_toggle_sound_effects)
            ) {
                eventHandler.accept(LandingIntent.ToggleSoundEffectsClicked(!state.soundEffectsEnabled))
            }
        }
        Spacer(modifier = Modifier.weight(5f))

        LFTextButton(
            textRes = if (state.hasSaveGame == true) {
                LFRes.String.button_continue
            } else {
                LFRes.String.button_new_game
            },
        ) {
            eventHandler.accept(LandingIntent.PlayClicked)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LFTextButton(
            textRes = LFRes.String.button_ship_builder,
        ) {
            eventHandler.accept(LandingIntent.ShipBuilderClicked)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LFTextButton(
            textRes = LFRes.String.button_settings,
        ) {
            eventHandler.accept(LandingIntent.ShowSettingsClicked)
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
@Preview
private fun LandingScreenPreview() {
    PreviewWrapper {
        LandingScreenContent(
            state = LandingState(
                musicEnabled = true,
                soundEffectsEnabled = false,
                hasSaveGame = true,
                kubriko = Kubriko.newInstance(),
            ),
        ) {}
    }
}

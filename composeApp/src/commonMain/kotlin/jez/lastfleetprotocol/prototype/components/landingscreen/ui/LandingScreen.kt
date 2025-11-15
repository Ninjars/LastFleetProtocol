package jez.lastfleetprotocol.prototype.components.landingscreen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.KubrikoViewport
import jez.lastfleetprotocol.prototype.ui.common.PreviewWrapper
import jez.lastfleetprotocol.prototype.ui.common.composables.LFIconButton
import jez.lastfleetprotocol.prototype.ui.common.composables.LFTextButton
import lastfleetprotocol.composeapp.generated.resources.*
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
    LandingScreen(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        eventHandler = viewModel
    )
}

@Composable
private fun LandingScreen(
    state: LandingState,
    eventHandler: Consumer<LandingEvent>,
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
    eventHandler: Consumer<LandingEvent>,
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth()
        ) {
            LFIconButton(
                drawable = if (state.musicEnabled) Res.drawable.ic_music_on else Res.drawable.ic_music_off,
                contentDescription = stringResource(Res.string.desc_toggle_music)
            ) {
                eventHandler.accept(LandingEvent.ToggleMusicClicked(!state.musicEnabled))
            }
            LFIconButton(
                drawable = if (state.soundEffectsEnabled) Res.drawable.ic_sound_effects_on else Res.drawable.ic_sound_effects_off,
                contentDescription = stringResource(Res.string.desc_toggle_sound_effects)
            ) {
                eventHandler.accept(LandingEvent.ToggleSoundEffectsClicked(!state.soundEffectsEnabled))
            }
        }
        Spacer(modifier = Modifier.weight(5f))

        LFTextButton(
            textRes = if (state.hasSaveGame == true) {
                Res.string.button_continue
            } else {
                Res.string.button_new_game
            },
            enabled = state.hasSaveGame != null,
        ) {
            eventHandler.accept(LandingEvent.PlayClicked)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LFTextButton(
            textRes = Res.string.button_settings,
        ) {
            eventHandler.accept(LandingEvent.ShowSettingsClicked)
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

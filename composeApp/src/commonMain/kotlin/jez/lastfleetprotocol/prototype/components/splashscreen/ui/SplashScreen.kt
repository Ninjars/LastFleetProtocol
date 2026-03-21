package jez.lastfleetprotocol.prototype.components.splashscreen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.navOptions
import jez.lastfleetprotocol.prototype.components.game.managers.LoadingManager
import jez.lastfleetprotocol.prototype.ui.common.HandleSideEffect
import jez.lastfleetprotocol.prototype.ui.common.PreviewWrapper
import jez.lastfleetprotocol.prototype.ui.navigation.LFNavDestination
import lastfleetprotocol.composeapp.generated.resources.Res
import lastfleetprotocol.composeapp.generated.resources.title_splash
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

typealias SplashScreen = @Composable (NavController) -> Unit

@Inject
@Composable
fun SplashScreen(
    viewModelFactory: () -> SplashVM,
    loadingManager: LoadingManager,
    @Assisted navController: NavController,
) {
    val viewModel = viewModel { viewModelFactory() }

    if (loadingManager.isGameLoaded()) {
        viewModel.accept(SplashIntent.OnKubrikoInitialized)
    }

    viewModel.HandleSideEffect {
        when (it) {
            SplashSideEffect.LoadingComplete -> {
                navController.navigate(
                    LFNavDestination.Landing, navOptions {
                        popUpTo(LFNavDestination.Splash) { inclusive = true }
                        launchSingleTop = true
                    }
                )
            }
        }
    }
    SplashScreen()
}

@Composable
private fun SplashScreen(
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Text(
            text = stringResource(Res.string.title_splash),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Preview
@Composable
private fun SplashScreenPreview() {
    PreviewWrapper {
        SplashScreen()
    }
}

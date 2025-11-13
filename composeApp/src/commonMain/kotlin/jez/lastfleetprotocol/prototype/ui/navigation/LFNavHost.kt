package jez.lastfleetprotocol.prototype.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jez.lastfleetprotocol.prototype.components.landingscreen.ui.LandingScreen
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject

typealias LFNavHost = @Composable () -> Unit

@Serializable
sealed interface LFNavDestination {
    @Serializable
    object Splash

    @Serializable
    object Landing

    @Serializable
    object Game

    @Serializable
    object Settings
}

@Inject
@Composable
fun LFNavHost(
    landingScreen: LandingScreen
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = LFNavDestination.Landing
    ) {
        composable<LFNavDestination.Splash> {}
        composable<LFNavDestination.Landing> {
            landingScreen()
        }
        composable<LFNavDestination.Game> {}
        composable<LFNavDestination.Settings> {}
    }
}
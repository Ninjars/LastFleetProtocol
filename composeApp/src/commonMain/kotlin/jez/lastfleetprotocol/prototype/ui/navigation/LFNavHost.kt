package jez.lastfleetprotocol.prototype.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jez.lastfleetprotocol.prototype.components.game.ui.GameScreen
import jez.lastfleetprotocol.prototype.components.landingscreen.ui.LandingScreen
import jez.lastfleetprotocol.prototype.components.splashscreen.ui.SplashScreen
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
    splashScreen: SplashScreen,
    landingScreen: LandingScreen,
    gameScreen: GameScreen,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = LFNavDestination.Splash
    ) {
        composable<LFNavDestination.Splash> {
            splashScreen(navController)
        }
        composable<LFNavDestination.Landing> {
            landingScreen(navController)
        }
        composable<LFNavDestination.Game> {
            gameScreen(navController)
        }
        composable<LFNavDestination.Settings> {}
    }
}
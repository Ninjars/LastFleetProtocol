package jez.lastfleetprotocol.prototype.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jez.lastfleetprotocol.prototype.components.game.GameScreenEntry
import jez.lastfleetprotocol.prototype.components.landingscreen.LandingScreenEntry
import jez.lastfleetprotocol.prototype.components.splashscreen.SplashScreenEntry
import me.tatarka.inject.annotations.Inject

typealias LFNavHost = @Composable () -> Unit

@Inject
@Composable
fun LFNavHost(
    splashScreen: SplashScreenEntry,
    landingScreen: LandingScreenEntry,
    gameScreen: GameScreenEntry,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = LFNavDestination.SPLASH,
    ) {
        composable(LFNavDestination.SPLASH) {
            splashScreen(navController)
        }
        composable(LFNavDestination.LANDING) {
            landingScreen(navController)
        }
        composable(LFNavDestination.GAME) {
            gameScreen(navController)
        }
        composable(LFNavDestination.SETTINGS) {}
    }
}
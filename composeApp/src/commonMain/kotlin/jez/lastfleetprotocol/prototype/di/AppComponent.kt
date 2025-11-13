package jez.lastfleetprotocol.prototype.di

import jez.lastfleetprotocol.prototype.components.landingscreen.ui.LandingScreen
import jez.lastfleetprotocol.prototype.ui.navigation.LFNavHost
import me.tatarka.inject.annotations.Component

@Component
abstract class AppComponent {
    abstract val navHost: LFNavHost

    abstract val landingScreen: LandingScreen
}
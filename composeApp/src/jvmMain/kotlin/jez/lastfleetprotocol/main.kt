package jez.lastfleetprotocol

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import jez.lastfleetprotocol.prototype.di.AppComponent
import jez.lastfleetprotocol.prototype.di.create

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "lastfleetprotocol",
    ) {
        App(AppComponent::class.create(enableLogging = true))
    }
}
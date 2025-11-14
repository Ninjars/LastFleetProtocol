package jez.lastfleetprotocol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import jez.lastfleetprotocol.prototype.di.AppComponent
import jez.lastfleetprotocol.prototype.di.create

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(AppComponent::class.create(enableLogging = true))
        }
    }
}

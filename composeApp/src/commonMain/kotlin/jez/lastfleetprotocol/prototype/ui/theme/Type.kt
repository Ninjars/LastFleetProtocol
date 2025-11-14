package jez.lastfleetprotocol.prototype.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import lastfleetprotocol.composeapp.generated.resources.*
import org.jetbrains.compose.resources.Font

@Composable
private fun bodyFontFamily() = FontFamily(
    Font(Res.font.sairacondensed_regular, weight = FontWeight.Normal),
    Font(Res.font.sairacondensed_bold, weight = FontWeight.Bold),
    Font(Res.font.sairacondensed_light, weight = FontWeight.Light),
    Font(Res.font.sairacondensed_medium, weight = FontWeight.Medium),
    Font(Res.font.sairacondensed_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.sairacondensed_black, weight = FontWeight.Black),
    Font(Res.font.sairacondensed_thin, weight = FontWeight.Thin),
)

@Composable
private fun titleFontFamily() = FontFamily(
    Font(Res.font.megrim_regular)
)

// Default Material 3 typography values
val baseline = Typography()

@Composable
fun appTypography(): Typography {
    val title = titleFontFamily()
    val body = bodyFontFamily()
    return Typography(
        displayLarge = baseline.displayLarge.copy(fontFamily = title),
        displayMedium = baseline.displayMedium.copy(fontFamily = title),
        displaySmall = baseline.displaySmall.copy(fontFamily = title),
        headlineLarge = baseline.headlineLarge.copy(fontFamily = title),
        headlineMedium = baseline.headlineMedium.copy(fontFamily = title),
        headlineSmall = baseline.headlineSmall.copy(fontFamily = title),
        titleLarge = baseline.titleLarge.copy(fontFamily = title),
        titleMedium = baseline.titleMedium.copy(fontFamily = title),
        titleSmall = baseline.titleSmall.copy(fontFamily = title),
        bodyLarge = baseline.bodyLarge.copy(fontFamily = body),
        bodyMedium = baseline.bodyMedium.copy(fontFamily = body),
        bodySmall = baseline.bodySmall.copy(fontFamily = body),
        labelLarge = baseline.labelLarge.copy(fontFamily = body),
        labelMedium = baseline.labelMedium.copy(fontFamily = body),
        labelSmall = baseline.labelSmall.copy(fontFamily = body),
    )
}

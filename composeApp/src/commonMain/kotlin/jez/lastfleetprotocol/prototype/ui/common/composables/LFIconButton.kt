package jez.lastfleetprotocol.prototype.ui.common.composables

import androidx.compose.foundation.Image
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import jez.lastfleetprotocol.prototype.ui.common.PreviewWrapper
import lastfleetprotocol.composeapp.generated.resources.Res
import lastfleetprotocol.composeapp.generated.resources.ic_exit
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun LFIconButton(
    drawable: DrawableResource,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color? = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(drawable),
            contentDescription = contentDescription,
            colorFilter = tint?.let { ColorFilter.tint(it) }
        )
    }
}

@Preview
@Composable
private fun LFIconButtonPreview() {
    PreviewWrapper {
        LFIconButton(
            onClick = {},
            drawable = Res.drawable.ic_exit
        )
    }
}

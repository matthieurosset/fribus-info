package ch.fribusinfo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Colors {
    val Bar = Color(0xFFA80231)
    val MinutesButton = Color(0xFF018BC1)
    val DelayBadge = Color(0xFFFFD100)
    val Divider = Color(0xFFE0E0E0)
    val Secondary = Color(0xFF858585)
    val PearlLine = Color(0xFF999696)
    val PearlCurrent = Color(0xFF7289BA)
    val PearlEnd = Color(0xFFD4DAE7)
}

@Composable
fun FriBusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Colors.Bar,
            onPrimary = Color.White,
            surface = Color.White,
            background = Color.White,
        ),
        content = content,
    )
}

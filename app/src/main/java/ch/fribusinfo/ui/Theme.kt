package ch.fribusinfo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Colors {
    val Bar = Color(0xFFA80231)
    val MinutesTop = Color(0xFF0085B9)
    val MinutesBottom = Color(0xFF008DC5)
    val PassedTop = Color(0xFFD4D4D4)
    val PassedBottom = Color(0xFFDDDDDD)
    val Gray = Color(0xFF666666)
    val Disabled = Color(0xFF8F8F8F)
    val Divider = Color(0xFFAAAAAA)
    val Subtitle = Color(0xFF888888)   // list_subtitle_normal
    val DrawerText = Color(0xFF666666)
    val DrawerSelected = Color(0xFFE6E6E6)
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

package ch.fribusinfo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.fribusinfo.data.Line

/** Small square-cornered line badge (white background + thin black border for the funicular). */
@Composable
fun LineBadge(line: Line, small: Boolean = false) {
    val bg = Color(0xFF000000.toInt() or line.bg)
    val fg = Color(0xFF000000.toInt() or line.fg)
    val size = if (small) 15.dp else 18.dp
    Box(
        Modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .background(bg)
            .let { if (line.bordered) it.border(1.dp, Color.Black) else it }
            .padding(horizontal = if (small) 2.dp else 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            line.label,
            color = fg,
            fontSize = if (small) 11.sp else 14.sp,
            fontWeight = if (line.bordered) FontWeight.Normal else FontWeight.Bold,
            maxLines = 1,
        )
    }
}

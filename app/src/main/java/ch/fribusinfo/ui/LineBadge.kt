package ch.fribusinfo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.fribusinfo.data.Line

/** [inTitleBar]: the slightly taller badge of the trip screen title.
 *  Small square line badge (about 17.5 dp, wider only when the label needs it; white background +
 *  thin black border for the funicular). Fixed height so the text line height never stretches it. */
@Composable
fun LineBadge(line: Line, inTitleBar: Boolean = false) {
    val bg = Color(0xFF000000.toInt() or line.bg)
    val fg = Color(0xFF000000.toInt() or line.fg)
    Box(
        Modifier
            .height(if (inTitleBar) 19.5.dp else 18.dp)
            .widthIn(min = if (inTitleBar) 15.dp else 17.5.dp)
            .background(bg)
            .let { if (line.bordered) it.border(1.dp, Color.Black) else it }
            .padding(horizontal = if (line.bordered) 3.dp else 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            line.label,
            color = fg,
            fontSize = if (inTitleBar) 13.5.sp else if (line.bordered) 11.sp else 12.sp,
            fontWeight = if (line.bordered) FontWeight.Normal else FontWeight.Medium,
            maxLines = 1,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
    }
}

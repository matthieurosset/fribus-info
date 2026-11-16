package ch.fribusinfo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.fribusinfo.data.Line
import ch.fribusinfo.data.StopWithLines

/** « 150 m »: always rounded UP to the next multiple of 25 m, like the old app (0-24 m -> 25 m,
 *  25 m -> 50 m), so never « 0 m ». */
fun formatDistance(meters: Int): String = "${(meters / 25 + 1) * 25} m"

/** Which badges to show (indices) and whether a trailing « … » follows: all up to 8 lines,
 *  else 7 badges + « … ». */
fun badgeSlots(count: Int): Pair<List<Int>, Boolean> =
    if (count <= 8) (1..count).toList() to false else (1..7).toList() to true

/** Reusable stop row (Nearby, Search, Favorites). Distance column hidden when distance is null. */
@Composable
fun StopRow(item: StopWithLines, onClick: () -> Unit) {
    Column(Modifier.testTag("stop-row").clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (item.distanceMeters != null) 64.5.dp else 56.5.dp)
                .padding(start = if (item.distanceMeters != null) 10.dp else 0.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val d = item.distanceMeters
            if (d != null) {
                Column(Modifier.width(50.dp).padding(top = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = Colors.Subtitle, modifier = Modifier.size(26.dp).graphicsLayer(scaleX = 0.87f))
                    Text(formatDistance(d), color = Colors.Subtitle, fontSize = 12.sp, maxLines = 1)
                }
            } else {
                Box(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f).padding(start = if (d != null) 2.dp else 0.dp, end = 8.dp)) {
                Text(
                    modifier = Modifier.offset(y = (-2.5).dp),
                    text = item.stop.name, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold,
                    color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(item.stop.commune, fontSize = 13.5.sp, lineHeight = 15.5.sp, color = Colors.Subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Badges(item.lines)
        }
        HorizontalDivider(color = Colors.Divider, thickness = Dp.Hairline)
    }
}

/** Max 4 badges per row x 2 rows, right-aligned; the « … » takes the last slot. */
@Composable
fun Badges(lines: List<Line>) {
    val (shown, more) = badgeSlots(lines.size)
    val cells: List<Line?> = shown.map { lines[it - 1] } + if (more) listOf(null) else emptyList()
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        cells.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { line ->
                    if (line != null) LineBadge(line)
                    else Text("…", color = Colors.Subtitle, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

package ch.fribusinfo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.fribusinfo.data.Line
import ch.fribusinfo.data.StopWithLines

/** « 125 m »: nearest 25 m, always meters (like the old app). */
fun formatDistance(meters: Int): String = "${((meters + 12) / 25) * 25} m"

/** Which badges to show (indices) and whether a trailing « … » follows: all up to 8 lines,
 *  else 7 badges + « … ». */
fun badgeSlots(count: Int): Pair<List<Int>, Boolean> =
    if (count <= 8) (1..count).toList() to false else (1..7).toList() to true

/** Reusable stop row (Nearby, Search, Favorites). Distance column hidden when distance is null. */
@Composable
fun StopRow(item: StopWithLines, onClick: () -> Unit) {
    Column(Modifier.testTag("stop-row").clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 65.dp).padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val d = item.distanceMeters
            if (d != null) {
                Column(Modifier.width(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = Colors.Secondary, modifier = Modifier.size(32.dp))
                    Text(formatDistance(d), color = Colors.Secondary, fontSize = 13.sp, maxLines = 1)
                }
            } else {
                Box(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                Text(
                    item.stop.name, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(item.stop.commune, fontSize = 16.sp, color = Colors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Badges(item.lines)
        }
        HorizontalDivider(color = Colors.Divider, thickness = 1.dp)
    }
}

/** Max 4 badges per row x 2 rows, right-aligned; the « … » takes the last slot. */
@Composable
private fun Badges(lines: List<Line>) {
    val (shown, more) = badgeSlots(lines.size)
    val cells: List<Line?> = shown.map { lines[it - 1] } + if (more) listOf(null) else emptyList()
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { line ->
                    if (line != null) LineBadge(line)
                    else Text("…", color = Colors.Secondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

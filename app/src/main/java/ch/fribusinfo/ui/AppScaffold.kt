package ch.fribusinfo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch

object Routes {
    const val NEARBY = "nearby"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val STOP = "stop/{stopId}"
    const val TRIP = "trip/{tripId}/{serviceDate}/{fromSeq}?delay={delay}&version={version}"

    fun stop(stopId: Long) = "stop/$stopId"
    /** [delaySeconds]: realtime delay of the chosen departure (0 = none). [version]: `meta.version`
     *  of the database the ids come from. */
    fun trip(tripId: Long, serviceDate: Int, fromSeq: Int, delaySeconds: Int = 0, version: String = "") =
        "trip/$tripId/$serviceDate/$fromSeq?delay=$delaySeconds&version=$version"
}

private class DrawerEntry(val route: String, val label: String, val icon: ImageVector)

private val entries = listOf(
    DrawerEntry(Routes.NEARBY, "À proximité", Icons.Filled.MyLocation),
    DrawerEntry(Routes.SEARCH, "Rechercher", Icons.Filled.Search),
    DrawerEntry(Routes.FAVORITES, "Favoris", Icons.Filled.Star),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    navController: NavController,
    onBack: (() -> Unit)? = null,
    titleBadge: ch.fribusinfo.data.Line? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentRoute by navController.currentBackStackEntryAsState()

    // Black system bars like the reference: the app draws inside the insets over a black base.
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        val drawerOpen = drawerState.targetValue == DrawerValue.Open
        androidx.activity.compose.BackHandler(enabled = drawerOpen) { scope.launch { drawerState.close() } }
        // Custom 56 dp bar (M3 TopAppBar is fixed at 64 dp; the reference is 56). It stays above the
        // drawer; while the drawer is open it shows a back arrow that closes it and hides the actions.
        Row(
            Modifier.fillMaxWidth().height(56.dp).background(Colors.Bar).padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (drawerOpen) {
                IconButton(onClick = { scope.launch { drawerState.close() } }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer le menu", tint = Color.White)
                }
            } else if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                }
            } else {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White)
                }
            }
            if (titleContent != null) {
                Box(Modifier.weight(1f).padding(start = 8.dp)) { if (!drawerOpen) titleContent() }
            } else Row(Modifier.weight(1f).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (titleBadge != null) {
                    Box(Modifier.testTag("title-badge")) { LineBadge(titleBadge, inTitleBar = true) }
                    Spacer(Modifier.width(2.5.dp))
                }
                Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!drawerOpen) CompositionLocalProvider(LocalContentColor provides Color.White) { actions() }
        }
        ModalNavigationDrawer(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = 0.6f),
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(240.dp),
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    drawerContainerColor = Color.White,
                    drawerShape = androidx.compose.ui.graphics.RectangleShape,
                ) {
                    entries.forEach { e ->
                        val selected = currentRoute?.destination?.route == e.route
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(if (selected) Colors.DrawerSelected else Color.White)
                                .clickable {
                                    scope.launch { drawerState.close() }
                                    if (!selected) {
                                        navController.navigate(e.route) {
                                            popUpTo(Routes.NEARBY)
                                            launchSingleTop = true
                                        }
                                    }
                                }
                                .padding(start = 33.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(e.icon, contentDescription = null, tint = Colors.Secondary, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(27.dp))
                            Text(e.label, fontSize = 17.sp, color = Colors.DrawerText)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Données : opentransportdata.swiss · transport.opendata.ch",
                        color = Colors.Secondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            },
        ) {
            Box(Modifier.fillMaxSize().background(Color.White)) { content() }
        }
    }
}

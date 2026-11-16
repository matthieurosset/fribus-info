package ch.fribusinfo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Spacer
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
    const val TRIP = "trip/{tripId}/{serviceDate}/{fromSeq}"

    fun stop(stopId: Long) = "stop/$stopId"
    fun trip(tripId: Long, serviceDate: Int, fromSeq: Int) = "trip/$tripId/$serviceDate/$fromSeq"
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
    Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                drawerContainerColor = Color.White,
                drawerShape = androidx.compose.ui.graphics.RectangleShape,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Colors.Bar)
                        .height(56.dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("FriBus Info", color = Color.White, fontSize = 22.sp)
                }
                Spacer(Modifier.height(8.dp))
                entries.forEach { e ->
                    NavigationDrawerItem(
                        label = { Text(e.label, fontSize = 17.sp) },
                        icon = { Icon(e.icon, contentDescription = null) },
                        selected = currentRoute?.destination?.route == e.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (currentRoute?.destination?.route != e.route) {
                                navController.navigate(e.route) {
                                    popUpTo(Routes.NEARBY)
                                    launchSingleTop = true
                                }
                            }
                        },
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFFE5E5E5),
                            unselectedContainerColor = Color.White,
                            selectedIconColor = Colors.Secondary,
                            unselectedIconColor = Colors.Secondary,
                            selectedTextColor = Color(0xFF424242),
                            unselectedTextColor = Color(0xFF424242),
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Données : opentransportdata.swiss · transport.opendata.ch",
                    color = Colors.Secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                // Custom 56 dp bar (M3 TopAppBar is fixed at 64 dp; the reference is 56).
                Row(
                    Modifier.fillMaxWidth().height(56.dp).background(Colors.Bar),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                        }
                    } else {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    }
                    if (titleContent != null) {
                        Box(Modifier.weight(1f).padding(start = 8.dp)) { titleContent() }
                    } else Row(Modifier.weight(1f).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (titleBadge != null) {
                            Box(Modifier.testTag("title-badge")) { LineBadge(titleBadge) }
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CompositionLocalProvider(LocalContentColor provides Color.White) { actions() }
                }
            },
            containerColor = Color.White,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }
    }
}

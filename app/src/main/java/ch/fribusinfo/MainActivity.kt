package ch.fribusinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ch.fribusinfo.location.AndroidLocationSource
import ch.fribusinfo.ui.NearbyScreen
import ch.fribusinfo.ui.DeparturesScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.fribusinfo.ui.SearchScreen
import ch.fribusinfo.ui.FavoritesScreen
import ch.fribusinfo.ui.FriBusTheme
import ch.fribusinfo.ui.Routes
import ch.fribusinfo.ui.TripScreen

class MainActivity : ComponentActivity() {
    // Screens (next tasks) read the repository from here; the updater swaps it.
    val timetable get() = (application as FriBusApp).timetable
    val favorites get() = (application as FriBusApp).favorites

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Black status and navigation bars with light icons (reference look).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        setContent { FriBusTheme { App() } }
    }
}

@Composable
private fun App() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.NEARBY) {
        composable(Routes.NEARBY) {
            val ctx = LocalContext.current
            val locations = remember { AndroidLocationSource(ctx.applicationContext) }
            val repo by (ctx.applicationContext as FriBusApp).timetable.repository.collectAsState()
            NearbyScreen(nav, repo, locations)
        }
        composable(Routes.SEARCH) {
            val repo by (LocalContext.current.applicationContext as FriBusApp).timetable.repository.collectAsState()
            SearchScreen(nav, repo)
        }
        composable(Routes.FAVORITES) {
            val app = LocalContext.current.applicationContext as FriBusApp
            val repo by app.timetable.repository.collectAsState()
            FavoritesScreen(nav, repo, app.favorites)
        }
        composable(
            Routes.STOP,
            arguments = listOf(navArgument("stopId") { type = NavType.LongType }),
        ) { entry ->
            val app = LocalContext.current.applicationContext as FriBusApp
            val repo by app.timetable.repository.collectAsState()
            DeparturesScreen(nav, repo, app.favorites, entry.arguments!!.getLong("stopId"))
        }
        composable(
            Routes.TRIP,
            arguments = listOf(
                navArgument("tripId") { type = NavType.LongType },
                navArgument("serviceDate") { type = NavType.IntType },
                navArgument("fromSeq") { type = NavType.IntType },
            ),
        ) { entry ->
            val app = LocalContext.current.applicationContext as FriBusApp
            val repo by app.timetable.repository.collectAsState()
            val d = entry.arguments!!.getInt("serviceDate")
            TripScreen(
                entry.arguments!!.getLong("tripId"),
                java.time.LocalDate.of(d / 10000, d / 100 % 100, d % 100),
                entry.arguments!!.getInt("fromSeq"),
                null,
                nav,
                repo,
            )
        }
    }
}

package ch.fribusinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.fribusinfo.ui.AppScaffold
import ch.fribusinfo.ui.FriBusTheme
import ch.fribusinfo.ui.Routes

class MainActivity : ComponentActivity() {
    // Screens (next tasks) read the repository from here; the updater swaps it.
    val timetable get() = (application as FriBusApp).timetable
    val favorites get() = (application as FriBusApp).favorites

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FriBusTheme { App() } }
    }
}

@Composable
private fun App() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.NEARBY) {
        composable(Routes.NEARBY) { Placeholder("À proximité", nav) }
        composable(Routes.SEARCH) { Placeholder("Rechercher", nav) }
        composable(Routes.FAVORITES) { Placeholder("Favoris", nav) }
        composable(
            Routes.STOP,
            arguments = listOf(navArgument("stopId") { type = NavType.LongType }),
        ) { Placeholder("Arrêt", nav) }
        composable(
            Routes.TRIP,
            arguments = listOf(
                navArgument("tripId") { type = NavType.LongType },
                navArgument("serviceDate") { type = NavType.IntType },
                navArgument("fromSeq") { type = NavType.IntType },
            ),
        ) { Placeholder("Trajet", nav) }
    }
}

// Ecrans vides : remplis par les taches suivantes.
@Composable
private fun Placeholder(title: String, nav: androidx.navigation.NavController) {
    AppScaffold(title = title, navController = nav) { }
}

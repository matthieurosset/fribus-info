package ch.fribusinfo

import android.app.Application
import ch.fribusinfo.data.Favorites
import ch.fribusinfo.data.TimetableHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class FriBusApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob())
    val timetable: TimetableHolder by lazy { TimetableHolder(this) }
    val favorites: Favorites by lazy { Favorites(this) }

    override fun onCreate() {
        super.onCreate()
        timetable.start(appScope)
    }
}

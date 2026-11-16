package ch.fribusinfo

import android.app.Application
import ch.fribusinfo.data.Favorites
import ch.fribusinfo.data.TimetableHolder
import ch.fribusinfo.realtime.RealtimeCache
import ch.fribusinfo.realtime.StationboardClient
import kotlinx.coroutines.CoroutineScope
import ch.fribusinfo.update.HttpFetcher
import ch.fribusinfo.update.TimetableUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FriBusApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob())
    val timetable: TimetableHolder by lazy { TimetableHolder(this) }
    val favorites: Favorites by lazy { Favorites(this) }
    val realtime: RealtimeCache by lazy { RealtimeCache(StationboardClient()) }

    override fun onCreate() {
        super.onCreate()
        timetable.start(appScope)
        appScope.launch(Dispatchers.IO) {
            timetable.repository.filterNotNull().first()
            TimetableUpdater(this@FriBusApp, HttpFetcher(), onInstalled = timetable::replace)
                .checkIfDue()
        }
    }
}

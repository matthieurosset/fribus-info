package ch.fribusinfo

import android.app.Application
import ch.fribusinfo.data.Favorites
import ch.fribusinfo.data.TimetableHolder

class FriBusApp : Application() {
    val timetable: TimetableHolder by lazy { TimetableHolder(this) }
    val favorites: Favorites by lazy { Favorites(this) }
}

package ch.fribusinfo.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the current repository. Created once (see FriBusApp); a later updater calls [replace]
 *  after installing a newer database, and screens collect [repository]. */
class TimetableHolder(context: Context) {
    private var db: SQLiteDatabase = TimetableDb.open(context)
    private val _repository = MutableStateFlow(TimetableRepository(db))
    val repository: StateFlow<TimetableRepository> = _repository.asStateFlow()

    /** Reopens the installed database file and publishes a fresh repository. */
    @Synchronized
    fun replace(context: Context) {
        val old = db
        db = TimetableDb.open(context)
        _repository.value = TimetableRepository(db)
        old.close()
    }
}

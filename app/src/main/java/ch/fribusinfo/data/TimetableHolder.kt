package ch.fribusinfo.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns the current repository. [repository] is null while the database is being opened
 *  ([start], on Dispatchers.IO). A later updater calls [replace] after installing a newer
 *  database; screens collect [repository]. */
class TimetableHolder(
    private val context: Context,
    private val asset: String = "timetable.db",
    private val fileName: String = TimetableDb.FILE,
) {
    private val _repository = MutableStateFlow<TimetableRepository?>(null)
    val repository: StateFlow<TimetableRepository?> = _repository.asStateFlow()

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Opens the database in [scope] on IO and publishes the warmed-up repository.
     *  Idempotent: only the first call does anything. */
    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) { _repository.value = openRepository() }
    }

    /** Reopens the installed database file and publishes a fresh repository. The previous
     *  database is deliberately NOT closed: queries may still be running on it, so the old
     *  handle lives until process death. */
    suspend fun replace() {
        val repo = withContext(Dispatchers.IO) { openRepository() }
        _repository.value = repo
    }

    private suspend fun openRepository(): TimetableRepository {
        // open() may install the bundled asset through shared temp/probe files
        val db = TimetableDb.installLock.withLock { TimetableDb.open(context, asset, fileName) }
        return TimetableRepository(db).also { it.warmUp() }
    }
}

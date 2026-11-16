package ch.fribusinfo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Favourite stop ids and the "show times instead of minutes" choice, kept in SharedPreferences. */
class Favorites(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    private val _ids = MutableStateFlow(
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
    )
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    private val _absoluteTime = MutableStateFlow(prefs.getBoolean(KEY_ABSOLUTE, false))
    val absoluteTime: StateFlow<Boolean> = _absoluteTime.asStateFlow()

    fun toggle(id: Long) {
        val next = _ids.value.let { if (id in it) it - id else it + id }
        _ids.value = next
        prefs.edit().putStringSet(KEY_IDS, next.map { it.toString() }.toSet()).apply()
    }

    fun setAbsoluteTime(value: Boolean) {
        _absoluteTime.value = value
        prefs.edit().putBoolean(KEY_ABSOLUTE, value).apply()
    }

    private companion object {
        const val KEY_IDS = "ids"
        const val KEY_ABSOLUTE = "absolute_time"
    }
}

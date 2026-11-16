package ch.fribusinfo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Ids with [id] added at the head (most recent first), or removed when already present. */
fun toggledFavorites(current: List<Long>, id: Long): List<Long> =
    if (id in current) current - id else listOf(id) + current

/** Stored order (newest first, comma separated); without it, the legacy unordered set, by id. */
fun parseFavorites(ordered: String?, legacy: Set<String>): List<Long> =
    if (ordered != null) ordered.split(',').mapNotNull { it.toLongOrNull() }.distinct()
    else legacy.mapNotNull { it.toLongOrNull() }.sorted()

/** Favourite stop ids (most recently added first) and the "show times instead of minutes" choice, kept in SharedPreferences. */
class Favorites(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    private val _ids = MutableStateFlow(
        parseFavorites(prefs.getString(KEY_ORDERED, null), prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()),
    )
    val ids: StateFlow<List<Long>> = _ids.asStateFlow()

    private val _absoluteTime = MutableStateFlow(prefs.getBoolean(KEY_ABSOLUTE, false))
    val absoluteTime: StateFlow<Boolean> = _absoluteTime.asStateFlow()

    fun toggle(id: Long) {
        _ids.update { toggledFavorites(it, id) }
        prefs.edit().putString(KEY_ORDERED, _ids.value.joinToString(",")).apply()
    }

    fun setAbsoluteTime(value: Boolean) {
        _absoluteTime.value = value
        prefs.edit().putBoolean(KEY_ABSOLUTE, value).apply()
    }

    private companion object {
        const val KEY_IDS = "ids"            // legacy: unordered set
        const val KEY_ORDERED = "ids_ordered"
        const val KEY_ABSOLUTE = "absolute_time"
    }
}

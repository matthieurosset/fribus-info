package ch.fribusinfo.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

object TimetableDb {
    private const val ASSET = "timetable.db"
    const val FILE = "timetable.db"

    /** Copies the bundled timetable to filesDir on first run or when the bundled
     *  meta.version is newer than the installed one, then opens it read-only. */
    fun open(context: Context): SQLiteDatabase {
        val target = File(context.filesDir, FILE)
        val bundled = bundledVersion(context)
        if (!target.exists() || (bundled != null && bundled > (versionOf(target) ?: "")) ) {
            copyBundled(context, target)
        }
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun versionOf(file: File): String? = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT value FROM meta WHERE key = 'version'", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun bundledVersion(context: Context): String? {
        val tmp = File(context.cacheDir, "bundled-probe.db")
        return try {
            copyAsset(context, tmp)
            versionOf(tmp)
        } finally {
            tmp.delete()
        }
    }

    private fun copyBundled(context: Context, target: File) {
        val tmp = File(target.parentFile, "$FILE.tmp")
        copyAsset(context, tmp)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "cannot install timetable" }
        }
    }

    private fun copyAsset(context: Context, to: File) {
        context.assets.open(ASSET).use { i -> to.outputStream().use { i.copyTo(it) } }
    }
}

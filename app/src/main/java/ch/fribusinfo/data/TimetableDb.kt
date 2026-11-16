package ch.fribusinfo.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

object TimetableDb {
    private const val ASSET = "timetable.db"
    const val FILE = "timetable.db"

    /** Blocking (file copy, sqlite): call off the main thread.
     *  Copies the bundled timetable to filesDir on first run or when the bundled meta.version is
     *  newer than the installed one, then opens it read-only. The bundled asset is only re-checked
     *  when the app package changed (PackageInfo.lastUpdateTime differs from the one stored at the
     *  last check); otherwise a readable installed file is opened without touching the asset. */
    fun open(context: Context, asset: String = ASSET, fileName: String = FILE): SQLiteDatabase {
        val target = File(context.filesDir, fileName)
        val prefs = context.getSharedPreferences("timetable_db", Context.MODE_PRIVATE)
        val key = "checked_update_time_$fileName"
        val stamp = packageStamp(context)
        if (target.exists() && prefs.getLong(key, -1L) == stamp) {
            try {
                return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: Exception) {
                // unreadable: fall through to the full check / reinstall
            }
        }
        val bundled = bundledVersion(context, asset)
        val installed = if (target.exists()) versionOf(target) else null
        if (installed == null || (bundled != null && bundled > installed)) {
            copyBundled(context, asset, target)
        }
        prefs.edit().putLong(key, stamp).apply()
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun packageStamp(context: Context): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    } catch (e: Exception) {
        0L
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

    private fun bundledVersion(context: Context, asset: String): String? {
        context.cacheDir.mkdirs()
        val tmp = File(context.cacheDir, "bundled-probe.db")
        return try {
            copyAsset(context, asset, tmp)
            versionOf(tmp)
        } finally {
            tmp.delete()
        }
    }

    private fun copyBundled(context: Context, asset: String, target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        copyAsset(context, asset, tmp)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "cannot install timetable" }
        }
    }

    private fun copyAsset(context: Context, asset: String, to: File) {
        context.assets.open(asset).use { i -> to.outputStream().use { i.copyTo(it) } }
    }
}

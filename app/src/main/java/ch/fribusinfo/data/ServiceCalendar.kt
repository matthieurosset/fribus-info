package ch.fribusinfo.data

import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate

/** Which services run on which dates. days bit0 = Monday ... bit6 = Sunday;
 *  exception type 1 = added, 2 = removed (an exception wins over the weekly pattern). */
class ServiceCalendar(
    private val rules: Map<Long, Rule>,
    private val exceptions: Map<Long, Map<Int, Int>>,
) {
    class Rule(val start: Int, val end: Int, val days: Int)

    fun isActive(serviceId: Long, date: LocalDate): Boolean {
        val rule = rules[serviceId] ?: return false
        val ymd = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        when (exceptions[serviceId]?.get(ymd)) {
            1 -> return true
            2 -> return false
        }
        if (ymd < rule.start || ymd > rule.end) return false
        return (rule.days shr (date.dayOfWeek.value - 1)) and 1 == 1
    }

    companion object {
        fun load(db: SQLiteDatabase): ServiceCalendar {
            val rules = HashMap<Long, Rule>()
            db.rawQuery("""SELECT id, start, "end", days FROM service""", null).use { c ->
                while (c.moveToNext()) rules[c.getLong(0)] = Rule(c.getInt(1), c.getInt(2), c.getInt(3))
            }
            val exc = HashMap<Long, MutableMap<Int, Int>>()
            db.rawQuery("SELECT service_id, date, type FROM service_exception", null).use { c ->
                while (c.moveToNext()) exc.getOrPut(c.getLong(0)) { HashMap() }[c.getInt(1)] = c.getInt(2)
            }
            return ServiceCalendar(rules, exc)
        }
    }
}

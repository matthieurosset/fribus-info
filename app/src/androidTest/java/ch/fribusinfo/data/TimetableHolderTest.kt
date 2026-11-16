package ch.fribusinfo.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TimetableHolderTest {
    // assets from the test APK (they hold test.db); storage from the app under test, because
    // the test APK has no data directory of its own
    private val target0 = InstrumentationRegistry.getInstrumentation().targetContext
    private val ctx: Context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().context) {
        override fun getFilesDir() = target0.filesDir
        override fun getCacheDir() = target0.cacheDir
        override fun getSharedPreferences(name: String, mode: Int) = target0.getSharedPreferences(name, mode)
    }
    private val name = "holder-test.db"
    private val target get() = File(ctx.filesDir, name)

    @Before fun setUp() {
        target.delete()
        ctx.getSharedPreferences("timetable_db", 0).edit().clear().commit()
    }

    @After fun tearDown() {
        target.delete()
    }

    @Test fun holderEventuallyEmitsRepository() = runBlocking {
        val holder = TimetableHolder(ctx, "test.db", name)
        assertNull(holder.repository.value)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        holder.start(scope)
        val repo = withTimeout(10_000) { holder.repository.filterNotNull().first() }
        assertEquals("Tilleul/Cathédrale", repo.stop(8587255)!!.stop.name)
        scope.cancel()
    }

    @Test fun secondOpenWithUnchangedPackageDoesNotCopy() {
        TimetableDb.open(ctx, "test.db", name).close()
        assertTrue(target.exists())
        target.setLastModified(1_000_000L)
        TimetableDb.open(ctx, "test.db", name).close()
        assertEquals(1_000_000L, target.lastModified())
    }

    @Test fun missingInstalledFileIsReinstalled() {
        TimetableDb.open(ctx, "test.db", name).close()
        target.delete()
        val db = TimetableDb.open(ctx, "test.db", name)
        assertNotNull(db)
        db.close()
        assertTrue(target.exists())
    }
}

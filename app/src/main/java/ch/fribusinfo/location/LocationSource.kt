package ch.fribusinfo.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** Source of the user's position. A null emission means "unavailable" (no permission, no provider,
 *  no fix yet after a grace period); a later real fix replaces it. */
interface LocationSource {
    fun updates(): Flow<Location?>
}

/** LocationManager (network + GPS providers): last known location first, then live updates. */
class AndroidLocationSource(private val context: Context) : LocationSource {
    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun updates(): Flow<Location?> = callbackFlow {
        val allowed = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!allowed) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter {
            try { lm.isProviderEnabled(it) } catch (e: Exception) { false }
        }
        var haveFix = false
        val last = providers.mapNotNull {
            try { lm.getLastKnownLocation(it) } catch (e: Exception) { null }
        }.maxByOrNull { it.time }
        if (last != null) { haveFix = true; trySend(last) }
        val listener = LocationListener { loc -> haveFix = true; trySend(loc) }
        providers.forEach {
            try { lm.requestLocationUpdates(it, 5_000L, 10f, listener, Looper.getMainLooper()) } catch (e: Exception) { }
        }
        // Never wait forever: without a fix after a grace period the screen says so.
        launch {
            delay(if (providers.isEmpty()) 0L else 10_000L)
            if (!haveFix) trySend(null)
        }
        awaitClose { lm.removeUpdates(listener) }
    }
}

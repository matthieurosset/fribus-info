package ch.fribusinfo.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Half-side of the square around the position (old app: `locate_me_radius` = 500 m). */
const val NEARBY_RADIUS_METERS = 500

/** The list is only recomputed when the position moved at least this far (old app:
 *  `MINIMUM_DISTANCE_TO_RELOAD_STATIONS` = 100 m). */
const val MIN_MOVE_TO_RELOAD_METERS = 100

private const val METERS_PER_DEGREE = 111_195.0   // 6 371 km * pi / 180

/** Great-circle distance in meters. */
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = p2 - p1
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * r * asin(sqrt(a))
}

/** Like the old app (bounding square in metric coordinates, no circular filter): a stop is
 *  nearby when it is within [radius] meters north-south AND east-west of the position, so
 *  the corners reach about 707 m. */
fun isInNearbySquare(
    lat: Double, lon: Double, stopLat: Double, stopLon: Double, radius: Int = NEARBY_RADIUS_METERS,
): Boolean {
    val north = (stopLat - lat) * METERS_PER_DEGREE
    val east = (stopLon - lon) * METERS_PER_DEGREE * cos(Math.toRadians(lat))
    return Math.abs(north) <= radius && Math.abs(east) <= radius
}

/** True when there is no previous query position or the position moved [min] meters or more. */
fun shouldReloadNearby(
    fromLat: Double?, fromLon: Double?, lat: Double, lon: Double, min: Int = MIN_MOVE_TO_RELOAD_METERS,
): Boolean = fromLat == null || fromLon == null || haversineMeters(fromLat, fromLon, lat, lon) >= min

/** A forced reload (target button) ignores the 100 m rule; otherwise [shouldReloadNearby]. */
fun nearbyReloadNeeded(forced: Boolean, fromLat: Double?, fromLon: Double?, lat: Double, lon: Double): Boolean =
    forced || shouldReloadNearby(fromLat, fromLon, lat, lon)

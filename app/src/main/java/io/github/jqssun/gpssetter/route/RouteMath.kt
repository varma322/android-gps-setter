package io.github.jqssun.gpssetter.route

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Pure geometry for route playback, so it's unit-tested.
object RouteMath {
    private const val EARTH_R = 6371000.0 // mean earth radius, metres

    data class Fix(val lat: Double, val lng: Double, val altitude: Double, val bearing: Float)

    fun haversineMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_R * atan2(sqrt(h), sqrt(1 - h))
    }

    fun bearingDeg(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val lat1 = Math.toRadians(aLat); val lat2 = Math.toRadians(bLat)
        val dLng = Math.toRadians(bLng - aLng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun pathLength(points: List<RoutePoint>): Double {
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += haversineMeters(points[i].lat, points[i].lng, points[i + 1].lat, points[i + 1].lng)
        }
        return total
    }

    // Position/heading/altitude at cumulative [distanceMeters] along the polyline.
    // Clamps to the first point at/below 0 and the last point at/beyond the end.
    fun pointAtDistance(points: List<RoutePoint>, distanceMeters: Double): Fix? {
        if (points.isEmpty()) return null
        if (points.size == 1 || distanceMeters <= 0.0) {
            val p = points.first()
            val b = if (points.size > 1) bearingDeg(points[0].lat, points[0].lng, points[1].lat, points[1].lng) else 0.0
            return Fix(p.lat, p.lng, p.altitude, b.toFloat())
        }
        var remaining = distanceMeters
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            val seg = haversineMeters(a.lat, a.lng, b.lat, b.lng)
            if (seg <= 0.0) continue
            if (remaining <= seg) {
                val f = remaining / seg
                return Fix(
                    lat = a.lat + (b.lat - a.lat) * f,
                    lng = a.lng + (b.lng - a.lng) * f,
                    altitude = a.altitude + (b.altitude - a.altitude) * f,
                    bearing = bearingDeg(a.lat, a.lng, b.lat, b.lng).toFloat()
                )
            }
            remaining -= seg
        }
        // past the end: last point, heading of the final segment
        val last = points.last(); val prev = points[points.size - 2]
        return Fix(last.lat, last.lng, last.altitude, bearingDeg(prev.lat, prev.lng, last.lat, last.lng).toFloat())
    }
}

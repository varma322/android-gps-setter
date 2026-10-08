package io.github.jqssun.gpssetter.xposed

import kotlin.math.cos

// Metres -> degrees for the "random position" jitter. Longitude must be scaled by cos(latitude),
// otherwise the east-west jitter grows wrong away from the equator. Pure so it's unit-tested.
object GeoOffset {
    private const val EARTH_RADIUS_M = 6378137.0

    fun latOffsetDegrees(metersNorth: Double): Double =
        Math.toDegrees(metersNorth / EARTH_RADIUS_M)

    fun lngOffsetDegrees(metersEast: Double, atLatDegrees: Double): Double =
        Math.toDegrees(metersEast / (EARTH_RADIUS_M * cos(Math.toRadians(atLatDegrees))))
}

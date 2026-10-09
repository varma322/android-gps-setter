package io.github.jqssun.gpssetter.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteMathTest {
    @Test fun haversineKnownDistance() {
        // ~111.2 km per degree of latitude
        val d = RouteMath.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111195.0, d, 200.0)
    }

    @Test fun bearingCardinal() {
        assertEquals(0.0, RouteMath.bearingDeg(0.0, 0.0, 1.0, 0.0), 0.5)    // north
        assertEquals(90.0, RouteMath.bearingDeg(0.0, 0.0, 0.0, 1.0), 0.5)   // east
    }

    @Test fun interpolatesMidSegment() {
        val pts = listOf(RoutePoint(0.0, 0.0, 100.0), RoutePoint(0.0, 1.0, 200.0))
        val len = RouteMath.pathLength(pts)
        val mid = RouteMath.pointAtDistance(pts, len / 2)!!
        assertEquals(0.5, mid.lng, 1e-6)
        assertEquals(150.0, mid.altitude, 1.0)   // altitude interpolated
        assertEquals(90.0, mid.bearing.toDouble(), 0.5) // heading east
    }

    @Test fun clampsEnds() {
        val pts = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 1.0))
        assertEquals(0.0, RouteMath.pointAtDistance(pts, -5.0)!!.lng, 1e-9) // before start
        assertEquals(1.0, RouteMath.pointAtDistance(pts, 1e9)!!.lng, 1e-9)  // past end
        assertNull(RouteMath.pointAtDistance(emptyList(), 0.0))
    }
}

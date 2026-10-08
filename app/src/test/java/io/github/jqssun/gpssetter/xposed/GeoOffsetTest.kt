package io.github.jqssun.gpssetter.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

// one degree of latitude is ~111320 m everywhere
private const val METERS_PER_DEG = 111319.49

class GeoOffsetTest {
    @Test fun latOffsetIsLatitudeIndependent() {
        assertEquals(1.0, GeoOffset.latOffsetDegrees(METERS_PER_DEG), 1e-4)
        assertEquals(0.0, GeoOffset.latOffsetDegrees(0.0), 1e-9)
        assertEquals(-0.5, GeoOffset.latOffsetDegrees(-METERS_PER_DEG / 2), 1e-4)
    }

    @Test fun lngOffsetScalesWithLatitude() {
        // at the equator a degree of longitude spans the same metres as latitude
        assertEquals(1.0, GeoOffset.lngOffsetDegrees(METERS_PER_DEG, 0.0), 1e-4)
        // at 60 deg, cos(60)=0.5, so the same metres cover twice the longitude
        assertEquals(2.0, GeoOffset.lngOffsetDegrees(METERS_PER_DEG, 60.0), 1e-3)
    }
}

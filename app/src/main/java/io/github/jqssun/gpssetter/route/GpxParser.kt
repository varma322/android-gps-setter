package io.github.jqssun.gpssetter.route

// ponytail: regex over GPX point elements (trkpt/rtept/wpt). Standard exporters are regular enough;
// swap for an XmlPullParser if an exotic file breaks it. Pure, so it's unit-tested.
private val POINT = Regex("""<(trkpt|rtept|wpt)\b([^>]*?)(?:/>|>(.*?)</\1>)""", RegexOption.DOT_MATCHES_ALL)
private val LAT = Regex("""lat\s*=\s*["']([^"']+)["']""")
private val LON = Regex("""lon\s*=\s*["']([^"']+)["']""")
private val ELE = Regex("""<ele>\s*([^<]+)</ele>""")

fun parseGpx(text: String): List<RoutePoint> =
    POINT.findAll(text).mapNotNull { m ->
        val attrs = m.groupValues[2]
        val lat = LAT.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
        val lon = LON.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
        val ele = m.groupValues[3].let { ELE.find(it)?.groupValues?.get(1)?.trim()?.toDoubleOrNull() } ?: 0.0
        RoutePoint(lat, lon, ele)
    }.toList()

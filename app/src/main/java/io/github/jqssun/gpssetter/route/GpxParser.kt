package io.github.jqssun.gpssetter.route

// ponytail: regex over GPX point elements (trkpt/rtept/wpt). Standard exporters are regular enough;
// swap for an XmlPullParser if an exotic file breaks it. Pure, so it's unit-tested.
private val POINT = Regex("""<(trkpt|rtept|wpt)\b([^>]*?)(?:/>|>(.*?)</\1>)""", RegexOption.DOT_MATCHES_ALL)
private val LAT = Regex("""lat\s*=\s*["']([^"']+)["']""")
private val LON = Regex("""lon\s*=\s*["']([^"']+)["']""")
private val ELE = Regex("""<ele>\s*([^<]+)</ele>""")
private val NAME = Regex("""<name>\s*([^<]*?)\s*</name>""")
// a route stop that toGpx wrote: a <wpt> typed "stop", kept apart from the track
private val STOP_TYPE = Regex("""<type>\s*stop\s*</type>""")

// A stop of a saved route (A, via stops, destination) and the name it was shown with.
data class GpxStop(val point: RoutePoint, val name: String?)

private fun MatchResult.isStop() = groupValues[1] == "wpt" && STOP_TYPE.containsMatchIn(groupValues[3])

private fun MatchResult.point(): RoutePoint? {
    val attrs = groupValues[2]
    val lat = LAT.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    val lon = LON.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    val ele = ELE.find(groupValues[3])?.groupValues?.get(1)?.trim()?.toDoubleOrNull() ?: 0.0
    return RoutePoint(lat, lon, ele)
}

private fun String.unescapeXml() = replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
private fun String.escapeXml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

// The track: every trkpt/rtept/wpt except the stops toGpx writes.
fun parseGpx(text: String): List<RoutePoint> =
    POINT.findAll(text).filterNot { it.isStop() }.mapNotNull { it.point() }.toList()

// The stops toGpx wrote, in order; empty for other files.
fun gpxStops(text: String): List<GpxStop> =
    POINT.findAll(text).filter { it.isStop() }.mapNotNull { m ->
        val p = m.point() ?: return@mapNotNull null
        GpxStop(p, NAME.find(m.groupValues[3])?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.unescapeXml())
    }.toList()

// The file's first <name> (the route's, in what toGpx writes), if it has one.
fun gpxName(text: String): String? =
    NAME.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.unescapeXml()

// A one-track GPX file of [points], with the route's [stops] as typed waypoints; what Saved stores for a
// route and exports. The name sits in <metadata> first, so it stays the file's first <name>.
// Round-trips through parseGpx, gpxStops and gpxName.
fun toGpx(name: String, points: List<RoutePoint>, stops: List<GpxStop> = emptyList()): String = buildString {
    val safe = name.escapeXml()
    append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
    append("""<gpx version="1.1" creator="GPS Setter" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
    append("<metadata><name>").append(safe).append("</name></metadata>\n")
    for (s in stops) {
        append("""<wpt lat="${s.point.lat}" lon="${s.point.lng}">""")
        s.name?.let { append("<name>").append(it.escapeXml()).append("</name>") }
        append("<type>stop</type></wpt>\n")
    }
    append("<trk><name>").append(safe).append("</name><trkseg>\n")
    for (p in points) append("""<trkpt lat="${p.lat}" lon="${p.lng}"><ele>${p.altitude}</ele></trkpt>""").append('\n')
    append("</trkseg></trk>\n</gpx>\n")
}

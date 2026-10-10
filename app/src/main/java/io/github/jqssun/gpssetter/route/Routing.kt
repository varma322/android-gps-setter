package io.github.jqssun.gpssetter.route

import com.google.gson.JsonParser
import io.github.jqssun.gpssetter.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// A→B road route. Prefers OpenRouteService (BuildConfig.ORS_KEY), falls back to keyless OSRM,
// and finally to a straight line so the feature always works. Uses HttpURLConnection so it does
// not depend on which OkHttp version a flavor happens to pull in.
object Routing {

    suspend fun road(a: RoutePoint, b: RoutePoint): List<RoutePoint> = withContext(Dispatchers.IO) {
        if (BuildConfig.ORS_KEY.isNotBlank()) {
            runCatching { ors(a, b) }.getOrNull()?.takeIf { it.size >= 2 }?.let { return@withContext it }
        }
        runCatching { osrm(a, b) }.getOrNull()?.takeIf { it.size >= 2 }?.let { return@withContext it }
        listOf(a, b)
    }

    private fun ors(a: RoutePoint, b: RoutePoint): List<RoutePoint> {
        val json = post(
            "https://api.openrouteservice.org/v2/directions/driving-car/geojson",
            """{"coordinates":[[${a.lng},${a.lat}],[${b.lng},${b.lat}]]}""",
            mapOf("Authorization" to BuildConfig.ORS_KEY, "Content-Type" to "application/json")
        )
        return coordsFromGeoJson(json, "features")
    }

    private fun osrm(a: RoutePoint, b: RoutePoint): List<RoutePoint> {
        val json = get(
            "https://router.project-osrm.org/route/v1/driving/" +
                "${a.lng},${a.lat};${b.lng},${b.lat}?overview=full&geometries=geojson"
        )
        return coordsFromGeoJson(json, "routes")
    }

    private fun get(url: String): String = open(url, "GET").run {
        try { inputStream.bufferedReader().use { it.readText() } } finally { disconnect() }
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String =
        open(url, "POST").run {
            try {
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                doOutput = true
                outputStream.use { it.write(body.toByteArray()) }
                inputStream.bufferedReader().use { it.readText() }
            } finally { disconnect() }
        }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
        }
}

// Pull geometry.coordinates ([lng,lat] pairs) from the first feature/route of a GeoJSON response.
// Pure, so it's unit-tested. ORS keys it under "features", OSRM under "routes".
@Suppress("DEPRECATION")
fun coordsFromGeoJson(json: String, arrayKey: String): List<RoutePoint> {
    return try {
        val root = JsonParser().parse(json).asJsonObject
        val arr = root.getAsJsonArray(arrayKey) ?: return emptyList()
        if (arr.size() == 0) return emptyList()
        val coords = arr[0].asJsonObject.getAsJsonObject("geometry").getAsJsonArray("coordinates")
        coords.map { el ->
            val c = el.asJsonArray
            RoutePoint(c[1].asDouble, c[0].asDouble)
        }
    } catch (e: Exception) {
        emptyList()
    }
}

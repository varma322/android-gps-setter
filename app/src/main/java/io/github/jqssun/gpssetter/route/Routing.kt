package io.github.jqssun.gpssetter.route

import com.google.gson.JsonParser
import io.github.jqssun.gpssetter.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// A→B road route. Prefers OpenRouteService (BuildConfig.ORS_KEY), falls back to keyless OSRM,
// and finally to a straight line so the feature always works.
object Routing {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    suspend fun road(a: RoutePoint, b: RoutePoint): List<RoutePoint> = withContext(Dispatchers.IO) {
        if (BuildConfig.ORS_KEY.isNotBlank()) {
            runCatching { ors(a, b) }.getOrNull()?.takeIf { it.size >= 2 }?.let { return@withContext it }
        }
        runCatching { osrm(a, b) }.getOrNull()?.takeIf { it.size >= 2 }?.let { return@withContext it }
        listOf(a, b)
    }

    private fun ors(a: RoutePoint, b: RoutePoint): List<RoutePoint> {
        val body = """{"coordinates":[[${a.lng},${a.lat}],[${b.lng},${b.lat}]]}"""
            .toRequestBody("application/json".toMediaTypeOrNull())
        val req = Request.Builder()
            .url("https://api.openrouteservice.org/v2/directions/driving-car/geojson")
            .addHeader("Authorization", BuildConfig.ORS_KEY)
            .post(body).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: return emptyList()
            return coordsFromGeoJson(text, "features")
        }
    }

    private fun osrm(a: RoutePoint, b: RoutePoint): List<RoutePoint> {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
            "${a.lng},${a.lat};${b.lng},${b.lat}?overview=full&geometries=geojson"
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val text = resp.body?.string() ?: return emptyList()
            return coordsFromGeoJson(text, "routes")
        }
    }
}

// Pull geometry.coordinates ([lng,lat] pairs) from the first feature/route of a GeoJSON response.
// Pure, so it's unit-tested. ORS keys it under "features", OSRM under "routes".
fun coordsFromGeoJson(json: String, arrayKey: String): List<RoutePoint> {
    return try {
        val root = JsonParser.parseString(json).asJsonObject
        val arr = root.getAsJsonArray(arrayKey) ?: return emptyList()
        if (arr.size() == 0) return emptyList()
        val coords = arr[0].asJsonObject.getAsJsonObject("geometry").getAsJsonArray("coordinates")
        coords.map { it.asJsonArray.let { c -> RoutePoint(c[1].asDouble, c[0].asDouble) } }
    } catch (e: Exception) {
        emptyList()
    }
}

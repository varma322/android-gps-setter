package io.github.jqssun.gpssetter.xposed

import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.jqssun.gpssetter.BuildConfig
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedModule
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.*
import kotlin.math.cos

object LocationHook {

    private const val TAG = "GPS Setter"
    var newlat: Double = 45.0000
    var newlng: Double = 0.0000
    private const val pi = 3.14159265359
    private var accuracy: Float = 0.0f
    private val rand: Random = Random()
    private const val earth = 6378137.0
    private lateinit var module: XposedModule
    private lateinit var settings: Xshare
    private var mLastUpdated: Long = 0
    private var started = false
    private val ignorePkg = arrayListOf("com.android.location.fused", BuildConfig.APPLICATION_ID)

    private fun init(xposed: XposedModule) {
        module = xposed
        settings = Xshare(xposed.getRemotePreferences(Xshare.GROUP))
    }

    // re-reads settings (and re-rolls the random offset) at most once per interval, so start/stop and new locations apply without a reboot
    private fun isActive(interval: Int): Boolean {
        if (System.currentTimeMillis() - mLastUpdated > interval) {
            updateLocation()
        }
        return started
    }

    private fun updateLocation() {
        try {
            mLastUpdated = System.currentTimeMillis()
            started = settings.isStarted
            if (!started) return
            val x = (rand.nextInt(51) - 25).toDouble()
            val y = (rand.nextInt(51) - 25).toDouble()
            val dlat = x / earth
            val dlng = y / (earth * cos(pi * settings.getLat / 180.0))
            newlat =
                if (settings.isRandomPosition) settings.getLat + (dlat * 180.0 / pi) else settings.getLat
            newlng =
                if (settings.isRandomPosition) settings.getLng + (dlng * 180.0 / pi) else settings.getLng
            accuracy = settings.accuracy!!.toFloat()

        } catch (e: Exception) {
            module.log(Log.ERROR, TAG, "Failed to read settings", e)
        }
    }

    // the spoofed fix; keeps timing/bearing from the real one when there is one
    private fun fakeLocation(origin: Location? = null, provider: String = LocationManager.GPS_PROVIDER): Location {
        val location = Location(origin?.provider ?: provider)
        if (origin != null) {
            location.time = origin.time
            location.bearing = origin.bearing
            location.bearingAccuracyDegrees = origin.bearingAccuracyDegrees
            location.elapsedRealtimeNanos = origin.elapsedRealtimeNanos
            location.verticalAccuracyMeters = origin.verticalAccuracyMeters
        } else {
            location.time = System.currentTimeMillis() - 300
            location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        location.latitude = newlat
        location.longitude = newlng
        location.accuracy = accuracy
        location.altitude = 0.0
        location.speed = 0F
        location.speedAccuracyMetersPerSecond = 0F
        try {
            HiddenApiBypass.invoke(location.javaClass, location, "setIsFromMockProvider", false)
        } catch (e: Exception) {
            module.log(Log.WARN, TAG, "unable to set mock $e")
        }
        return location
    }

    // for methods taking a single Location: hand the original our fake instead
    private fun proceedWithFake(chain: Chain): Any? =
        chain.proceed(arrayOf<Any?>(fakeLocation(chain.getArg(0) as Location?)))

    // one missing method (ROM / version differences) shouldn't take the other hooks down with it
    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            module.log(Log.WARN, TAG, "Skipping hook $what", t)
        }
    }

    fun initSystemHooks(xposed: XposedModule, classLoader: ClassLoader) {
        init(xposed)
        module.log(Log.INFO, TAG, "Hooking system server")
        if (!settings.isHookedSystem) return
        val interval = 200

        if (Build.VERSION.SDK_INT < 34) {
            val lms = classLoader.loadClass("com.android.server.LocationManagerService")

            safely("getLastLocation") {
                module.hook(lms.getDeclaredMethod("getLastLocation", LocationRequest::class.java, String::class.java))
                    .intercept { chain -> if (isActive(interval)) fakeLocation() else chain.proceed() }
            }

            for (method in lms.declaredMethods) {
                if (method.returnType == Boolean::class.java &&
                    method.name in listOf("addGnssBatchingCallback", "addGnssMeasurementsListener", "addGnssNavigationMessageListener")
                ) {
                    safely(method.name) {
                        module.hook(method).intercept { chain -> if (isActive(interval)) false else chain.proceed() }
                    }
                }
            }

            safely("callLocationChangedLocked") {
                val receiver = classLoader.loadClass("com.android.server.LocationManagerService\$Receiver")
                module.hook(receiver.getDeclaredMethod("callLocationChangedLocked", Location::class.java))
                    .intercept { chain -> if (isActive(interval)) proceedWithFake(chain) else chain.proceed() }
            }
        } else {
            val lms = classLoader.loadClass("com.android.server.location.LocationManagerService")

            for (method in lms.declaredMethods) {
                if (method.name == "getLastLocation" && method.returnType == Location::class.java) {
                    // params: String::class.java, LastLocationRequest::class.java, String::class.java, String::class.java
                    safely(method.name) {
                        module.hook(method).intercept { chain -> if (isActive(interval)) fakeLocation() else chain.proceed() }
                    }
                } else if (method.returnType == Void.TYPE &&
                    method.name in listOf("startGnssBatch", "addGnssAntennaInfoListener", "addGnssMeasurementsListener", "addGnssNavigationMessageListener")
                ) {
                    safely(method.name) {
                        module.hook(method).intercept { chain -> if (isActive(interval)) null else chain.proceed() }
                    }
                }
            }

            safely("injectLocation") {
                module.hook(lms.getDeclaredMethod("injectLocation", Location::class.java))
                    .intercept { chain -> if (isActive(interval)) proceedWithFake(chain) else chain.proceed() }
            }
        }
    }

    fun initAppHooks(xposed: XposedModule, packageName: String) {
        if (packageName in ignorePkg) return
        init(xposed)
        val interval = 80 // 200

        for (method in Location::class.java.declaredMethods) {
            val spoofed: (() -> Any)? = when (method.name) {
                "getLatitude" -> { -> newlat }
                "getLongitude" -> { -> newlng }
                "getAccuracy" -> { -> accuracy }
                else -> null
            }
            if (spoofed != null) safely(method.name) {
                module.hook(method).intercept { chain -> if (isActive(interval)) spoofed() else chain.proceed() }
            }
        }

        safely("Location.set") {
            module.hook(Location::class.java.getDeclaredMethod("set", Location::class.java))
                .intercept { chain -> if (isActive(interval)) proceedWithFake(chain) else chain.proceed() }
        }

        safely("getLastKnownLocation") {
            module.hook(LocationManager::class.java.getDeclaredMethod("getLastKnownLocation", String::class.java))
                .intercept { chain ->
                    if (isActive(interval)) fakeLocation(provider = chain.getArg(0) as String) else chain.proceed()
                }
        }
    }
}

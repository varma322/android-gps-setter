package io.github.jqssun.gpssetter.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.MutableLiveData
import io.github.jqssun.gpssetter.BuildConfig
import io.github.jqssun.gpssetter.gsApp
import io.github.jqssun.gpssetter.xposed.Xshare
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executors


object PrefManager   {

    private const val START = "start"
    private const val LATITUDE = "latitude"          // legacy Float; read only to migrate old installs
    private const val LONGITUDE = "longitude"
    private const val LATITUDE_D = "latitude_d"       // Double bits (Long); Float lost ~1 m of precision
    private const val LONGITUDE_D = "longitude_d"
    private const val DEF_LAT = 40.7128
    private const val DEF_LNG = -74.0060
    private const val HOOKED_SYSTEM = "system_hooked"
    private const val HOOK_BOOT = "system_hooked_boot"       // the boot (Settings.Global.BOOT_COUNT) HOOK_AT_BOOT is for
    private const val HOOK_AT_BOOT = "system_hooked_at_boot" // the system hook setting that boot started with
    private const val RANDOM_POSITION = "random_position" // legacy on/off (= 25 m); read only to migrate
    private const val JITTER = "jitter_m"                 // Float metres, 0 = exact point
    private const val ACCURACY_SETTING = "accuracy_level"
    private const val MAP_TYPE = "map_type"
    private const val DARK_THEME = "dark_theme"
    private const val DISABLE_UPDATE = "update_disabled"
    private const val ENABLE_JOYSTICK = "joystick_enabled"
    private const val ACCENT = "accent_color"
    // motion, set during route playback; static spoofing leaves these at the defaults below
    private const val ALTITUDE = "altitude_d" // Double bits
    private const val SPEED = "speed"         // m/s
    private const val BEARING = "bearing"     // degrees, -1 = unset (use the real fix's bearing)
    private const val DISABLED_APPS = "disabled_apps" // scoped packages whose in-app hooks stand down
    private const val ROUTE_STATE = "route_state" // app-only, not mirrored: RoutePlaybackService.IDLE / RUNNING / PAUSED
    private const val ROUTE_ALONG = "route_along" // app-only: metres along the playing route


    private val pref: SharedPreferences by lazy {
        gsApp.getSharedPreferences("${BuildConfig.APPLICATION_ID}_prefs", Context.MODE_PRIVATE)
    }

    // the hook can't read app-private files, so the settings it needs are mirrored into
    // LSPosed's remote preferences (libxposed service) whenever they change
    @Volatile private var remote: SharedPreferences? = null
    // the framework's service while bound, for the Targets tab (scope, hooked processes)
    @Volatile var service: XposedService? = null
        private set
    private val fileWriter = Executors.newSingleThreadExecutor() // keeps writes in order, off the main thread
    val moduleActive = MutableLiveData<Boolean>()
    // e.g. "LSPosed 2.0.1 · API 102", for the Targets tab; set before moduleActive flips to true
    @Volatile var framework: String? = null
        private set
    // the listener runs once per changed key, and a route tick changes six; post the sync so one
    // edit() costs one sync (and one remote-file write), not six. Listeners run on the main thread.
    private val main = Handler(Looper.getMainLooper())
    private val syncTask = Runnable { syncRemote() }
    private val mirror = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        main.removeCallbacks(syncTask)
        main.post(syncTask)
    }

    // fires on any settings change (start/stop from the QS tile or notification, route playback, ...)
    // so screens can re-render; the listener is a field because prefs only hold listeners weakly
    val changed = MutableLiveData<Unit>()
    private val notifier = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changed.postValue(Unit) }
    fun watch() = pref.registerOnSharedPreferenceChangeListener(notifier)

    fun onServiceBind(service: XposedService) {
        framework = try {
            "${service.frameworkName} ${service.frameworkVersion} · API ${service.apiVersion}"
        } catch (e: RuntimeException) {
            null
        }
        remote = try {
            service.getRemotePreferences(Xshare.GROUP)
        } catch (e: RuntimeException) {
            Timber.e(e, "Remote preferences unavailable")
            return
        }
        this.service = service
        pref.registerOnSharedPreferenceChangeListener(mirror)
        syncRemote()
        moduleActive.postValue(true)
    }

    fun onServiceDied() {
        remote = null
        service = null
        moduleActive.postValue(false)
    }

    private fun syncRemote() {
        val remote = remote ?: return
        remote.edit()
            .putBoolean(START, isStarted)
            .putLong(LATITUDE_D, getLat.toRawBits())
            .putLong(LONGITUDE_D, getLng.toRawBits())
            .putBoolean(HOOKED_SYSTEM, isSystemHooked)
            .putFloat(JITTER, jitterMeters)
            .putString(ACCURACY_SETTING, accuracy)
            .putLong(ALTITUDE, altitude.toRawBits())
            .putFloat(SPEED, speed)
            .putFloat(BEARING, bearing)
            // a plain HashSet: the framework's process deserializes this and has no Kotlin classes
            // (emptySet() is kotlin.collections.EmptySet, and the failed commit crashed the app)
            .putStringSet(DISABLED_APPS, HashSet(disabledApps))
            .apply()
        writeRemoteFile(remote.all)
    }

    // The same values as a remote file, for the system_server hook: the framework may never push
    // preference changes there (see Xshare). Truncate first, then one write ending in the END marker,
    // so a read racing the write sees an incomplete file and keeps its previous values.
    private fun writeRemoteFile(values: Map<String, *>) {
        val service = service ?: return
        val text = values.entries.joinToString("") { "${it.key}=${it.value}\n" } + "${Xshare.END}=1\n"
        fileWriter.execute {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(service.openRemoteFile(Xshare.FILE)).use {
                    it.channel.truncate(0)
                    it.write(text.toByteArray())
                }
            } catch (e: Exception) {
                Timber.e(e, "Couldn't write ${Xshare.FILE}")
            }
        }
    }

    val altitude: Double
        get() = if (pref.contains(ALTITUDE)) Double.fromBits(pref.getLong(ALTITUDE, 0)) else 0.0
    val speed: Float get() = pref.getFloat(SPEED, 0f)
    val bearing: Float get() = pref.getFloat(BEARING, -1f)

    // written by RoutePlaybackService so the Routes tab notices a route that ended, or was paused or
    // stopped from the notification
    var routeState: Int
        get() = pref.getInt(ROUTE_STATE, 0)
        set(value) = pref.edit().putInt(ROUTE_STATE, value).apply()

    // route playback: move to a point with motion. start=true; mirror listener syncs to the hook.
    // [along] = metres along the route, for the Routes tab's progress (app-only, not mirrored)
    fun updateMotion(la: Double, ln: Double, altitude: Double, speed: Float, bearing: Float, along: Float) {
        runInBackground {
            pref.edit()
                .putLong(LATITUDE_D, la.toRawBits())
                .putLong(LONGITUDE_D, ln.toRawBits())
                .putBoolean(START, true)
                .putLong(ALTITUDE, altitude.toRawBits())
                .putFloat(SPEED, speed)
                .putFloat(BEARING, bearing)
                .putFloat(ROUTE_ALONG, along)
                .apply()
        }
    }

    val routeAlong: Float get() = pref.getFloat(ROUTE_ALONG, 0f)

    val disabledApps: Set<String> get() = pref.getStringSet(DISABLED_APPS, null)?.toSet() ?: emptySet()

    fun setAppSpoofed(pkg: String, spoofed: Boolean) {
        pref.edit().putStringSet(DISABLED_APPS, if (spoofed) disabledApps - pkg else disabledApps + pkg).apply()
    }


    val isStarted : Boolean
        get() = pref.getBoolean(START, false)

    val getLat : Double
        get() = readCoord(LATITUDE_D, LATITUDE, DEF_LAT)

    val getLng : Double
        get() = readCoord(LONGITUDE_D, LONGITUDE, DEF_LNG)

    // prefer the Double-bits value; fall back to the legacy Float once, so an upgrade keeps the saved point
    private fun readCoord(bitsKey: String, legacyKey: String, def: Double): Double = when {
        pref.contains(bitsKey) -> Double.fromBits(pref.getLong(bitsKey, 0))
        pref.contains(legacyKey) -> pref.getFloat(legacyKey, def.toFloat()).toDouble()
        else -> def
    }

    var isSystemHooked : Boolean
        get() = pref.getBoolean(HOOKED_SYSTEM, true) // must match Xshare.isHookedSystem default
        set(value) {
            // system_server reads this once, as it starts: on the first change this boot, note what it started with
            val boot = bootCount
            val editor = pref.edit()
            if (boot >= 0 && pref.getInt(HOOK_BOOT, -1) != boot) {
                editor.putInt(HOOK_BOOT, boot).putBoolean(HOOK_AT_BOOT, isSystemHooked)
            }
            editor.putBoolean(HOOKED_SYSTEM, value).apply()
        }

    // the setting system_server is running with now; isSystemHooked only takes effect at the next reboot
    val isSystemHookedThisBoot: Boolean
        get() = systemHookedThisBoot(isSystemHooked, pref.getInt(HOOK_BOOT, -1), pref.getBoolean(HOOK_AT_BOOT, true), bootCount)

    private val bootCount: Int
        get() = Settings.Global.getInt(gsApp.contentResolver, Settings.Global.BOOT_COUNT, -1)

    var jitterMeters: Float
        get() = when {
            pref.contains(JITTER) -> pref.getFloat(JITTER, 0f)
            pref.getBoolean(RANDOM_POSITION, false) -> 25f // the old switch used a fixed 25 m
            else -> 0f
        }
        set(value) = pref.edit().putFloat(JITTER, value).apply()

    var accuracy : String?
        get() = pref.getString(ACCURACY_SETTING,"10")
        set(value) { pref.edit().putString(ACCURACY_SETTING,value).apply()}

    var mapType : Int
        get() = pref.getInt(MAP_TYPE,1)
        set(value) { pref.edit().putInt(MAP_TYPE,value).apply()}

    var darkTheme: Int
        get() = pref.getInt(DARK_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = pref.edit().putInt(DARK_THEME, value).apply()

    var isUpdateDisabled: Boolean
        get() = pref.getBoolean(DISABLE_UPDATE, false)
        set(value) = pref.edit().putBoolean(DISABLE_UPDATE, value).apply()

    var isJoystickEnabled: Boolean
        get() = pref.getBoolean(ENABLE_JOYSTICK, false)
        set(value) = pref.edit().putBoolean(ENABLE_JOYSTICK, value).apply()

    var accentIndex: Int
        get() = pref.getInt(ACCENT, 0) // default Blue
        set(value) = pref.edit().putInt(ACCENT, value).apply()

    // apply() updates memory at once and writes to disk (and the remote mirror) in the background,
    // so callers read the new state straight after; the Teleport panel relies on that
    fun update(start:Boolean, la: Double, ln: Double) {
        pref.edit()
            .putLong(LATITUDE_D, la.toRawBits())
            .putLong(LONGITUDE_D, ln.toRawBits())
            .putBoolean(START, start)
            // static spoof: clear any motion left over from route playback
            .putLong(ALTITUDE, 0.0.toRawBits())
            .putFloat(SPEED, 0f)
            .putFloat(BEARING, -1f)
            .apply()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun runInBackground(method: suspend () -> Unit){
        GlobalScope.launch(Dispatchers.IO) {
            method.invoke()
        }
    }

}

// The system hook setting system_server is running with: [recordedValue] if it was recorded during
// this boot (the setting changed since system_server started), else the [saved] setting. An unknown
// boot count (-1) can't be matched, so it falls back to [saved].
internal fun systemHookedThisBoot(saved: Boolean, recordedBoot: Int, recordedValue: Boolean, boot: Int): Boolean =
    if (boot >= 0 && recordedBoot == boot) recordedValue else saved

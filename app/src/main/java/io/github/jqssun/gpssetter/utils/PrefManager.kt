package io.github.jqssun.gpssetter.utils

import android.content.Context
import android.content.SharedPreferences
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


object PrefManager   {

    private const val START = "start"
    private const val LATITUDE = "latitude"
    private const val LONGITUDE = "longitude"
    private const val HOOKED_SYSTEM = "system_hooked"
    private const val RANDOM_POSITION = "random_position"
    private const val ACCURACY_SETTING = "accuracy_level"
    private const val MAP_TYPE = "map_type"
    private const val DARK_THEME = "dark_theme"
    private const val DISABLE_UPDATE = "update_disabled"
    private const val ENABLE_JOYSTICK = "joystick_enabled"


    private val pref: SharedPreferences by lazy {
        gsApp.getSharedPreferences("${BuildConfig.APPLICATION_ID}_prefs", Context.MODE_PRIVATE)
    }

    // the hook can't read app-private files, so the settings it needs are mirrored into
    // LSPosed's remote preferences (libxposed service) whenever they change
    @Volatile private var remote: SharedPreferences? = null
    val moduleActive = MutableLiveData<Boolean>()
    private val mirror = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> syncRemote() }

    fun onServiceBind(service: XposedService) {
        remote = try {
            service.getRemotePreferences(Xshare.GROUP)
        } catch (e: RuntimeException) {
            Timber.e(e, "Remote preferences unavailable")
            return
        }
        pref.registerOnSharedPreferenceChangeListener(mirror)
        syncRemote()
        moduleActive.postValue(true)
    }

    fun onServiceDied() {
        remote = null
        moduleActive.postValue(false)
    }

    private fun syncRemote() {
        val remote = remote ?: return
        remote.edit()
            .putBoolean(START, isStarted)
            .putFloat(LATITUDE, getLat.toFloat())
            .putFloat(LONGITUDE, getLng.toFloat())
            .putBoolean(HOOKED_SYSTEM, isSystemHooked)
            .putBoolean(RANDOM_POSITION, isRandomPosition)
            .putString(ACCURACY_SETTING, accuracy)
            .apply()
    }


    val isStarted : Boolean
        get() = pref.getBoolean(START, false)

    val getLat : Double
        get() = pref.getFloat(LATITUDE, 40.7128F).toDouble()

    val getLng : Double
        get() = pref.getFloat(LONGITUDE, -74.0060F).toDouble()

    var isSystemHooked : Boolean
        get() = pref.getBoolean(HOOKED_SYSTEM, true) // must match Xshare.isHookedSystem default
        set(value) { pref.edit().putBoolean(HOOKED_SYSTEM,value).apply() }

    var isRandomPosition :Boolean
        get() = pref.getBoolean(RANDOM_POSITION, false)
        set(value) { pref.edit().putBoolean(RANDOM_POSITION, value).apply() }

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

    fun update(start:Boolean, la: Double, ln: Double) {
        runInBackground {
            val prefEditor = pref.edit()
            prefEditor.putFloat(LATITUDE, la.toFloat())
            prefEditor.putFloat(LONGITUDE, ln.toFloat())
            prefEditor.putBoolean(START, start)
            prefEditor.apply()
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun runInBackground(method: suspend () -> Unit){
        GlobalScope.launch(Dispatchers.IO) {
            method.invoke()
        }
    }

}